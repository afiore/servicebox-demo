import java.nio.file.Path

import cats.effect.IO
import fs2.{Scheduler, Segment, Stream}
import io.circe.Json
import io.minio.MinioClient
import m3.Main
import org.http4s.Uri
import org.http4s.circe._
import org.http4s.client.Client
import org.http4s.client.blaze.Http1Client
import org.scalatest.Assertion
import scala.concurrent.ExecutionContext
import scala.util.Random
import scala.concurrent.duration._
import cats.syntax.apply._
import cats.syntax.foldable._
import cats.instances.list._
import cats.instances.set._
import cats.syntax.option._
import com.itv.servicebox.interpreter.IOLogger

package object integration {

  private[integration] class ItTestApp(minioClient: MinioClient,
                                       httpClient: Client[IO],
                                       scheduler: Scheduler,
                                       endpointUri: Uri) {

    def uploadFile(path: Path): IO[Unit] =
      IO(
        minioClient.putObject(deps.Minio.Settings.bucket,
                              path.getFileName.toString,
                              path.toString))

    def waitUntilHealthy(implicit ec: ExecutionContext): Stream[IO, Unit] = {
      val healthEndpoint = endpointUri / "_meta" / "health"
      val isHealthy = IOLogger.debug("waiting for app to be healthy...") *> httpClient
        .expect[Unit](healthEndpoint)
      scheduler.retry(isHealthy, 200.millis, _ => 500.millis, 25)
    }

    def uploadBytes(filename: String, byteSize: Long, segmentSize: Int = 10000)(
        implicit ec: ExecutionContext): IO[Unit] =
      (for {
        is <- bytes(byteSize, segmentSize).through(fs2.io.toInputStream[IO])
        _ <- Stream.eval(
          IO(
            minioClient
              .putObject(deps.Minio.Settings.bucket,
                         filename,
                         is,
                         "application/octet-stream")))

      } yield ()).compile.drain

    def pollObjectsUntil(p: Set[String] => Boolean)(
        implicit ec: ExecutionContext): IO[Json] = {
      val jsonWithKeys = IOLogger.debug(s"polling $endpointUri ...") *> httpClient
        .expect[Json](endpointUri)
        .map(json =>
          json.asObject.fold((Set.empty[String], json))(o =>
            (o.values.toList.foldMap(_.asObject.map(_.keys.toSet).orEmpty),
             json)))
        .flatMap {
          case (keys, json) =>
            if (!p(keys))
              IO.raiseError(
                new IllegalStateException(s"invalid key state: $keys"))
            else
              IO.pure(json)
        }

      scheduler
        .retry(jsonWithKeys, 200.millis, _ => 500.millis, 30)
        .compile
        .last
        .map(_.get)
    }

    private def bytes(totalSize: Long, segmentSize: Int): Stream[IO, Byte] =
      Stream
        .unfoldSegment(totalSize) { missing =>
          val currentSegmentSize =
            if (missing < segmentSize) missing else segmentSize

          if (missing <= 0) None
          else {
            val bytes = new Array[Byte](currentSegmentSize.toInt)
            Random.nextBytes(bytes)
            Some(Segment.seq(bytes) -> (missing - currentSegmentSize))
          }
        }
        .covary[IO]
  }

  object ItTestApp {
    def apply(config: m3.Config,
              httpClient: Client[IO],
              scheduler: Scheduler): ItTestApp = {
      val endpoint =
        s"http://${config.objectStore.host}:${config.objectStore.port}"
      val minioClient = new MinioClient(endpoint,
                                        config.objectStore.key,
                                        config.objectStore.secret)
      new ItTestApp(
        minioClient,
        httpClient,
        scheduler,
        Uri.unsafeFromString(s"http://localhost:${config.http.port}"))
    }

  }

  def withApp(disableShutdown: Boolean = false)(
      runTest: ItTestApp => IO[Assertion]): Unit = {
    import scala.concurrent.ExecutionContext.Implicits.global

    (for {
      scheduler <- fs2.Scheduler[IO](3)
      config <- deps.setupWithUpdatedConfig
      httpClient <- Http1Client.stream[IO]()
      stopSignal <- Stream.eval(fs2.async.signalOf[IO, Boolean](false))
      testApp = ItTestApp(config, httpClient, scheduler)
      _ <- Main
        .server(config)
        .interruptWhen(stopSignal)
        .concurrently(
          testApp.waitUntilHealthy
            .evalMap(
              _ =>
                runTest(testApp) *> IOLogger
                  .info("completed test run...") *> stopSignal.set(
                  !disableShutdown)))

    } yield ()).compile.drain.unsafeRunSync()
  }
}
