package micmesmeg

import cats.effect.IO
import org.http4s.HttpService
import fs2.Stream

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import fs2.async.Ref
import fs2.StreamApp
import org.http4s.server.Router
import com.itv.bucky.{fs2 => buckyFs2, _}
import com.itv.bucky.fs2.IOAmqpClient
import com.itv.bucky.pattern.requeue.{RequeueOps, RequeuePolicy}
import com.typesafe.scalalogging.StrictLogging
import micmesmeg.rmq.ObjectStoreEvent
import org.http4s.server.blaze.BlazeBuilder

object Main extends StreamApp[IO] with StrictLogging {
  trait App {
    def http: HttpService[IO]
    def handlers: Stream[IO, Unit]
  }

  object App {
    def apply(amqpClient: IOAmqpClient,
              config: Config,
              datastore: Datastore): App = {
      new App {
        override def http = Router(
          "/" -> ObjectSizerRoute(datastore)
        )

        override def handlers: Stream[IO, Unit] =
          RequeueOps(amqpClient)
            .requeueHandlerOf[ObjectStoreEvent](
              rmq.Declarations.queueName,
              rmq.Handler(datastore),
              RequeuePolicy(maximumProcessAttempts = 10, 3.minute),
              implicitly[PayloadUnmarshaller[ObjectStoreEvent]]
            )
      }
    }

    def apply(config: Config): Stream[IO, App] =
      for {
        datastore <- Stream.eval(
          Ref[IO, Map[Location, StoredObject]](Map.empty))
        amqpConfig = AmqpClientConfig(config.rmq.host,
                                      config.rmq.port,
                                      config.rmq.username,
                                      config.rmq.password)
        amqpClient <- buckyFs2.IOAmqpClient
          .use(amqpConfig, rmq.Declarations.asList)(Stream.emit(_))
      } yield App(amqpClient, config, datastore)
  }

  val server: Stream[IO, StreamApp.ExitCode] = for {
    config <- Config.read
    _ <- Stream.eval(IO(logger.info(s"starting app with ${config}")))
    app <- App(config)
    exitCode <- BlazeBuilder[IO]
      .bindHttp(8080, "0.0.0.0")
      .mountService(app.http)
      .serve concurrently app.handlers
  } yield exitCode

  override def stream(
      args: List[String],
      requestShutdown: IO[Unit]): Stream[IO, StreamApp.ExitCode] = Main.server
}
