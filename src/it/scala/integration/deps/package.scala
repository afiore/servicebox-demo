package integration

import com.itv.servicebox.algebra.{AppTag, Location}
import fs2.Stream
import cats.effect.IO
import com.itv.servicebox.interpreter._
import com.itv.servicebox.docker
import scala.concurrent.ExecutionContext.Implicits.global
import com.typesafe.scalalogging.StrictLogging
import micmesmeg.Config
import org.scalatest.Assertion

package object deps extends StrictLogging {
  implicit val tag: AppTag = AppTag("example", "micmesmeg")

  def setupWithUpdatedConfig: Stream[IO, Config] =
    for {
      config <- Config.read
      _ <- Stream.eval(IO.pure(logger.info("Loading config for test")))
      rmqSpec = RabbitMQ.spec
      minioSpec <- Stream.eval(Minio(config))
      runner <- Stream.bracket(IO.pure(docker.runner()(rmqSpec, minioSpec)))(
        Stream.emit(_),
        _ => IO.unit)
//        _.tearDown)
      _ <- Stream.eval(IO(logger.info(s"Base config: $config")))

      services <- Stream.eval(runner.setUp)
      rmqLocation <- Stream.eval(
        services.locationFor(rmqSpec.ref, RabbitMQ.port))
      minioLocation <- Stream.eval(
        services.locationFor(minioSpec.ref, Minio.port))

      _ <- Stream.eval(IO(logger.info(s"Updated config: $config")))

    } yield updateConfig(config)(rmqLocation, minioLocation)

//  def withRunningServices(runTest: Config => IO[Assertion]): Stream[IO, Assertion] =
//    for {
//      config <- setupWithUpdatedConfig
//      assertion <- Stream.eval(runTest(config))
//    } yield assertion

  private def updateConfig(config: Config)(rmqLocation: Location,
                                           minioLocation: Location) = {
    val amqpConfig = config.amqp
      .copy(host = rmqLocation.host, port = rmqLocation.port)

    val minioConfig = config.objectStore.copy(
      host = minioLocation.host,
      port = minioLocation.port
    )

    config.copy(amqp = amqpConfig, objectStore = minioConfig)
  }

}
