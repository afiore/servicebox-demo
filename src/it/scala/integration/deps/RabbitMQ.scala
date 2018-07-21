package integration.deps

import cats.data.NonEmptyList
import com.itv.servicebox.algebra._
import cats.effect.IO

import scala.concurrent.duration._
import com.typesafe.scalalogging.StrictLogging
import org.http4s.client.blaze.{BlazeClientConfig, Http1Client}
import fs2.Stream
import org.http4s.Uri

object RabbitMQ extends StrictLogging {
  val port = 5672
  val AdminUIPort = 15672
  val spec: Service.Spec[IO] = {

    def checkConnection(endpoints: Endpoints): IO[Unit] =
      endpoints.locationFor[IO](AdminUIPort).flatMap { adminUILocation =>
        val baseUrl =
          s"http://${adminUILocation.host}:${adminUILocation.port}"

        (for {
          client <- Http1Client.stream[IO](
            BlazeClientConfig.defaultConfig.copy(idleTimeout = 100.millis))
          _ <- Stream.eval(client.get(Uri.unsafeFromString(baseUrl)) { res =>
            if (res.status.isSuccess)
              IO(logger.info(s"Docker RabbitMQ started."))
            else
              IO.raiseError(new RuntimeException(
                s"RMQ Docker check failed due to status $res.status from $baseUrl"))
          })
        } yield ()).compile.drain
      }

    Service.Spec(
      "Rabbit",
      NonEmptyList.of(
        Container.Spec("rabbitmq:3.6-management",
                       Map.empty[String, String],
                       List(PortSpec.assign(port),
                            PortSpec.assign(AdminUIPort)),
                       None,
                       None,
                       Some("rabbit"))),
      Service.ReadyCheck(checkConnection, 3.second, 1.minute)
    )
  }
}
