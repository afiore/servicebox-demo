package m3

import cats.effect.IO
import org.http4s.{EntityEncoder, HttpService}
import org.http4s.dsl.io._
import org.http4s.circe._

object ObjectSizerRoute {
  implicit val datastoreEntityEnc =
    jsonEncoderOf[IO, Map[ObjectSize, Map[Location, StoredObject]]]

  def apply(datastore: Datastore): HttpService[IO] = HttpService[IO] {
    case GET -> Root =>
      datastore.get.flatMap { objs =>
        Ok(objs.groupBy { case (_, o) => ObjectSize(o.sizeBytes) })
      }
  }
}
