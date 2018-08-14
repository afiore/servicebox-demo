package integration

import io.circe.Json
import m3.ObjectSize._
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class M3IntegrationTest extends FreeSpec {
  "The M3 app" - {
    "groups uploaded assets by size" in {
      withApp(disableShutdown = false) { app =>
        for {
          _ <- app.uploadBytes("file-a", 100)
          objectsBySize <- app.pollObjectsUntil(_.exists(_.endsWith("file-a")))

        } yield
          objectsBySize should ===(
            Json.obj(
              Micro.id -> Json.obj(
                s"s3://${deps.Minio.Settings.bucket}/file-a" -> Json.obj(
                  "sizeBytes" -> Json.fromLong(100)
                )
              )
            ))

      }
    }
  }
}
