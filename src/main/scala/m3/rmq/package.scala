package m3

import cats.instances.either._
import cats.syntax.either._
import cats.syntax.apply._
import com.itv.bucky.CirceSupport._
import com.itv.bucky.decl.{Declaration, Exchange, Fanout, Topic}
import com.itv.bucky.pattern.requeue.requeueDeclarations
import com.itv.bucky.{ExchangeName, PayloadUnmarshaller, QueueName, RoutingKey}
import io.circe.{ACursor, Decoder, DecodingFailure}

package object rmq {

  sealed trait ObjectStoreEvent

  object ObjectStoreEvent {
    case class Created(location: Location, sizeBytes: Long)
        extends ObjectStoreEvent

    object Created {
      val prefix = "s3:ObjectCreated:"
    }

    case class Removed(location: Location) extends ObjectStoreEvent
    object Removed {
      val prefix = "s3:ObjectRemoved:"
    }
    case class Other(name: String) extends ObjectStoreEvent

    private def location(rec: ACursor): Decoder.Result[Location] =
      for {
        bucket <- rec
          .downField("s3")
          .downField("bucket")
          .downField("name")
          .as[String]
        key <- rec
          .downField("s3")
          .downField("object")
          .downField("key")
          .as[String]
      } yield Location(bucket, key)

    private def size(rec: ACursor): Decoder.Result[Long] =
      rec.downField("s3").downField("object").downField("size").as[Long]

    private def objectCreated(
        rec: ACursor,
        eventName: String): Decoder.Result[ObjectStoreEvent] =
      (location(rec), size(rec))
        .mapN((a, b) => Created(a, b))
        .ensure(DecodingFailure(s"expected ${Created.prefix}*", rec.history))(
          _ => eventName.startsWith(Created.prefix))

    private def objectRemoved(
        rec: ACursor,
        eventName: String): Decoder.Result[ObjectStoreEvent] =
      location(rec)
        .map(Removed(_))
        .ensure(
          DecodingFailure(s"""expected ${Removed.prefix}*""", rec.history))(_ =>
          eventName.startsWith(Removed.prefix))

    implicit val eventDec = Decoder.instance[ObjectStoreEvent](h =>
      for {
        eventName <- h.get[String]("EventName")
        firstRecord = h.downField("Records").downN(0)
        event <- objectCreated(firstRecord, eventName)
          .orElse(objectRemoved(firstRecord, eventName))
          .orElse(
            Right(Other(eventName))
          )

      } yield event)

    implicit val eventUm: PayloadUnmarshaller[ObjectStoreEvent] =
      unmarshallerFromDecodeJson[ObjectStoreEvent]
  }

  object Declarations {
    val exchange = ExchangeName("minio-event")
    val routingKey = RoutingKey("test-events")
    val queueName = QueueName("micmesmeg")
    val declaration =
      Exchange(exchange, exchangeType = Topic, isDurable = true)
        .binding(routingKey -> queueName)

    val asList: List[Declaration] = List(declaration) ++ requeueDeclarations(
      queueName,
      routingKey)
  }
}
