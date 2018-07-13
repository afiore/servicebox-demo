import _root_.fs2.Stream
import cats.effect.IO
import com.itv.bucky
import com.itv.bucky.Monad.Id
import com.itv.bucky.{AmqpSimulator, RabbitSimulator, ext}
import fs2.async.Ref
import io.circe.Json
import io.circe.syntax._
import micmesmeg._
import micmesmeg.rmq.{Declarations, ObjectStoreEvent}
import org.http4s.Uri
import org.http4s.client.Client
import org.http4s.circe._
import org.scalatest.Assertion

package object features {
  type Fs2AmqpSimulator = AmqpSimulator[Id, IO, Throwable, Stream[IO, Unit]]

  case class TestData(fileInfos: Map[Location, StoredObject])

  class TestApp(httpClient: Client[IO],
                amqpClient: Fs2AmqpSimulator,
                datastore: Datastore) {

    private def storedObjects: IO[Map[Location, StoredObject]] = datastore.get

    def objectsBySize: IO[Json] =
      httpClient
        .expect[Json](Uri(path = "/"))

    private def publishEvent(event: Json): IO[bucky.ConsumeAction] = {
      val decl = micmesmeg.rmq.Declarations
      val publishCommand = RabbitSimulator.stringPublishCommandBuilder using
        decl.exchange using decl.routingKey

      amqpClient.publish(publishCommand.toPublishCommand(event.spaces4))
    }

    def publishObjectCreated(location: Location,
                             sizeBytes: Long): IO[bucky.ConsumeAction] = {
      publishEvent(
        Json.obj(
          "EventName" -> Json.fromString("s3:ObjectCreated:Put"),
          "Records" -> Json.arr(
            Json.obj(
              "s3" -> Json.obj(
                "bucket" -> Json.obj(
                  "name" -> Json.fromString(location.bucket)
                ),
                "object" -> Json.obj(
                  "key" -> Json.fromString(location.key),
                  "size" -> Json.fromLong(sizeBytes)
                )
              )
            ))
        ))
    }

    def publishObjectRemoved(location: Location): IO[bucky.ConsumeAction] = {
      publishEvent(
        Json.obj(
          "EventName" -> Json.fromString("s3:ObjectRemoved:Delete"),
          "Records" -> Json.arr(
            Json.obj(
              "s3" -> Json.obj(
                "bucket" -> Json.obj(
                  "name" -> Json.fromString(location.bucket)
                ),
                "object" -> Json.obj(
                  "key" -> Json.fromString(location.key),
                )
              )
            ))
        ))
    }
  }

  def withApp(testData: TestData = TestData(Map.empty))(
      f: TestApp => IO[Assertion]): Unit = {
    import com.itv.bucky.future.SameThreadExecutionContext.implicitly
    implicit val ioMonad = com.itv.bucky.fs2.ioMonadError
    implicit val fMonad = com.itv.bucky.future.futureMonad
    import micmesmeg.rmq.Declarations

    val datastore =
      Ref[IO, Map[Location, StoredObject]](testData.fileInfos).unsafeRunSync()
    ext.fs2.withSimulator[TestApp](Declarations.asList) { amqpSimulator =>
      for {
        config <- Config.read
        app <- Stream(Main.App(amqpSimulator, config, datastore)).covary[IO]
        _ <- Stream.eval(IO.unit).concurrently(app.handlers)
        httpClient <- Stream.bracket(IO.pure(Client.fromHttpService(app.http)))(
          Stream.emit(_),
          _.shutdown)
        testApp <- Stream(new TestApp(httpClient, amqpSimulator, datastore))
          .covary[IO]
      } yield testApp
    }(f)
  }
}
