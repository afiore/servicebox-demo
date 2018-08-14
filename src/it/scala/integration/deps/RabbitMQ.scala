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
  val containerName = "rabbit"
  val spec: Service.Spec[IO] = {

    def connectToAdminUI(endpoints: Endpoints): IO[Unit] =
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
      name = "Rabbit",
      containers = NonEmptyList.of(
        Container.Spec(
          imageName = "rabbitmq:3.6-management",
          env = Map.empty[String, String],
          ports = Set(PortSpec.assign(port), PortSpec.assign(AdminUIPort)),
          command = None,
          mounts = None,
          name = Some(containerName)
        )),
      Service.ReadyCheck(connectToAdminUI, 3.second, 1.minute)
    )
  }
}
