package integration.deps

import java.nio.file.Paths

import cats.data.NonEmptyList
import cats.effect.IO
import com.itv.servicebox.algebra._
import com.itv.servicebox.interpreter._
import io.circe.Json
import io.minio.MinioClient
import io.minio.messages.{
  EventType,
  NotificationConfiguration,
  QueueConfiguration
}
import micmesmeg.Config

import scala.concurrent.duration._

object Minio {
  val port = 9000

  object Settings {
    val region = "us-east-1"
    val bucket = "test-bucket"
  }

  def apply(config: Config): IO[Service.Spec[IO]] = {
    import cats.syntax.apply._
    import micmesmeg.rmq.{Declarations => decl}

    import scala.collection.JavaConverters._

    val configNotifications: NotificationConfiguration = {
      val config = new NotificationConfiguration
      val queueConfigs = config.queueConfigurationList()
      val queue = new QueueConfiguration()
      queue.setQueue(s"arn:minio:sqs:${Settings.region}:1:amqp")

      queue.setEvents(
        List(
          EventType.OBJECT_CREATED_ANY,
          EventType.OBJECT_REMOVED_ANY
        ).asJava)

      queueConfigs.add(queue)
      config.setQueueConfigurationList(List(queue).asJava)
      config
    }

    def probeIndexAndConfigBucket(endpoints: Endpoints): IO[Unit] = {
      import Settings._
      for {
        l <- endpoints.locationFor[IO](port)
        _ <- IOLogger.info(s"Connecting to minio $l")
        client = new MinioClient(s"http://${l.host}:${l.port}",
                                 config.objectStore.key,
                                 config.objectStore.secret)

        _ <- IOLogger.info(s"checking if a bucket exists")
        bucketExist <- IO(client.bucketExists(bucket))
        _ <- if (bucketExist) IO.unit
        else
          IOLogger.info(s"Creating bucket '$bucket'...") *> IO(
            client.makeBucket(bucket))

        _ <- IOLogger.info(s"getting bucket config ...")
        _ <- IO(client.setBucketNotification(bucket, configNotifications))

      } yield ()
    }

    val configContent = Json
      .obj(
        "version" -> Json.fromString("23"),
        "credential" -> Json.obj(
          "accessKey" -> Json.fromString(config.objectStore.key),
          "secretKey" -> Json.fromString(config.objectStore.secret)
        ),
        "region" -> Json.fromString(Settings.region),
        "browser" -> Json.fromString("on"),
        "domain" -> Json.fromString(""),
        "storageclass" -> Json.obj(
          "standard" -> Json.fromString(""),
          "rrs" -> Json.fromString("")
        ),
        "cache" -> Json.obj(
          "drives" -> Json.arr(),
          "expiry" -> Json.fromInt(90),
          "exclude" -> Json.arr()
        ),
        "notify" ->
          Json.obj(
            "amqp" ->
              Json.obj("1" -> Json.obj(
                "enable" -> Json.fromBoolean(true),
                "url" -> Json
                  .fromString(
                    //TODO: this needs to point to host/port known before hand!!!
                    s"amqp://${config.amqp.username}:${config.amqp.password}@rabbitmq-3-6-management:${RabbitMQ.port}"),
                "exchange" -> Json.fromString(decl.exchange.value),
                "routingKey" -> Json.fromString(decl.routingKey.value),
                "exchangeType" -> Json.fromString(
                  decl.declaration.exchangeType.value),
                "deliveryMode" -> Json.fromInt(0),
                "mandatory" -> Json.fromBoolean(false),
                "immediate" -> Json.fromBoolean(false),
                "durable" -> Json.fromBoolean(decl.declaration.isDurable),
                "internal" -> Json.fromBoolean(decl.declaration.isInternal),
                "noWait" -> Json.fromBoolean(false),
                "autoDeleted" -> Json.fromBoolean(false)
              )))
      )
      .toString()
      .getBytes()

    BindMount
      .fromTmpFileContent[IO](Paths.get("target", "servicebox"))(
        Paths.get("/root/.minio"))("config.json" -> configContent)
      .map { bindMount =>
        Service.Spec[IO](
          "minio",
          NonEmptyList.of(
            Container
              .Spec("minio/minio:edge",
                    Map.empty[String, String],
                    Set(9000),
                    Some(NonEmptyList.of("server", "/data")),
                    Some(NonEmptyList.of(bindMount)))),
          Service.ReadyCheck(probeIndexAndConfigBucket, 3.seconds, 1.minute),
          dependencies = Set(RabbitMQ.spec.ref)
        )
      }
  }
}
