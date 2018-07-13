import cats.effect.IO
import com.itv.bucky.QueueName
import fs2.async.Ref
import io.circe.generic.semiauto._
import io.circe.{Encoder, KeyEncoder}

package object micmesmeg {

  case class Location(bucket: String, key: String)
  object Location {
    implicit val locationEnc: Encoder[Location] = deriveEncoder[Location]

    implicit val locationKeyEnc: KeyEncoder[Location] =
      KeyEncoder.instance[Location](l => s"s3://${l.bucket}/${l.key}")
  }

  case class StoredObject(sizeBytes: Long)
  object StoredObject {
    implicit val storedObjEnc: Encoder[StoredObject] =
      deriveEncoder[StoredObject]
  }

  sealed trait ObjectSize {
    def id: String
  }
  object ObjectSize {
    case object Micro extends ObjectSize {
      override def id: String = "tiny"
    }
    case object Mesos extends ObjectSize {
      override def id: String = "medium"
    }
    case object Mega extends ObjectSize {
      override def id: String = "massive"
    }

    implicit val objectSizeKeyEnc: KeyEncoder[ObjectSize] =
      KeyEncoder.instance[ObjectSize](_.id)

    def apply(n: Long): ObjectSize = n match {
      case _ if n < 10000                  => Micro
      case _ if n > 10000 && n < 100000000 => Mesos
      case _                               => Mega
    }
  }

  type Datastore = Ref[IO, Map[Location, StoredObject]]

  case class Config(rmq: Config.Rmq)
  object Config {
    import fs2.Stream
    def read: Stream[IO, Config] = Stream(
      Config(Rmq("localhost", 5672, "guest", "guest"))
    )

    case class Rmq(host: String, port: Int, username: String, password: String)
  }
}
