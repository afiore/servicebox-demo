package features

import micmesmeg._
import micmesmeg.rmq._
import org.scalatest.FreeSpec
import org.scalatest.Matchers._
import io.circe.Json
import micmesmeg.ObjectSize._

class ObjectSizerFeatureTest extends FreeSpec {
  "The object sizer" - {
    "sizes a newly created 100 bytes object as 'micro'" in {
      withApp() { app =>
        for {
          _ <- app.publishObjectCreated(Location("some-bucket", "key"), 100)
          objectsBySize <- app.objectsBySize

        } yield
          objectsBySize should ===(
            Json.obj(
              Micro.id -> Json.obj(
                "s3://some-bucket/key" -> Json.obj(
                  "sizeBytes" -> Json.fromInt(100)
                )
              )
            ))

      }
    }

    "re-sizes an updated object from micro to macro" in {
      val testData =
        TestData(Map(Location("some-bucket", "key") -> StoredObject(100)))

      withApp(testData) { app =>
        for {
          _ <- app.publishObjectCreated(Location("some-bucket", "key"),
                                        5000000000L)
          objectsBySize <- app.objectsBySize
        } yield
          objectsBySize should ===(
            Json.obj(
              Mega.id -> Json.obj(
                "s3://some-bucket/key" -> Json.obj(
                  "sizeBytes" -> Json.fromLong(5000000000L)
                )
              )
            ))
      }
    }

    "drops deleted objects" in {
      val testData =
        TestData(
          Map(Location("some-bucket", "key1") -> StoredObject(5000000),
              Location("some-bucket", "key2") -> StoredObject(2)))

      withApp(testData) { app =>
        for {
          _ <- app.publishObjectRemoved(Location("some-bucket", "key2"))
          objectsBySize <- app.objectsBySize
        } yield
          objectsBySize should ===(
            Json.obj(
              Mesos.id -> Json.obj(
                "s3://some-bucket/key1" -> Json.obj(
                  "sizeBytes" -> Json.fromLong(5000000))
              )
            )
          )
      }
    }
  }
}
