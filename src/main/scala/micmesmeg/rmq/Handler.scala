package micmesmeg.rmq

import cats.effect.IO
import cats.syntax.functor._
import com.itv.bucky
import com.itv.bucky.{Ack, RequeueHandler}
import com.typesafe.scalalogging.StrictLogging
import micmesmeg.{Datastore, StoredObject}

case class Handler(datastore: Datastore)
    extends RequeueHandler[IO, ObjectStoreEvent]
    with StrictLogging {

  override def apply(event: ObjectStoreEvent): IO[bucky.RequeueConsumeAction] =
    for {
      _ <- IO { logger.info(s"got an object store event: event") }
      _ <- handleEvent(event)
    } yield Ack

  private def handleEvent(event: ObjectStoreEvent): IO[Unit] =
    event match {
      case ObjectStoreEvent.Created(location, sizeBytes) =>
        datastore.modify { m =>
          m.updated(location, StoredObject(sizeBytes))
        }.void
      case ObjectStoreEvent.Removed(location) =>
        datastore.modify(_ - location).void
      case _ =>
        IO(println(s"ignoring event: $event"))
    }

}
