package integration

import java.nio.file.Paths

import cats.effect.IO
import io.circe.Json
import micmesmeg.ObjectSize._
import org.scalatest.FreeSpec
import org.scalatest.Matchers._

import scala.concurrent.ExecutionContext.Implicits.global

class MicMesMegTest extends FreeSpec {
  "The Micro Mega app" - {
    "groups uploaded assets by size" in {
      withApp { app =>
        for {
          _ <- app.uploadJunk("file-a", 100)
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
