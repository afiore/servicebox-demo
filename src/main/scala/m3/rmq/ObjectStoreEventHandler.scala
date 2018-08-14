package m3.rmq

import cats.effect.IO
import cats.syntax.functor._
import com.itv.bucky
import com.itv.bucky.{Ack, RequeueHandler}
import com.typesafe.scalalogging.StrictLogging
import m3.{Datastore, StoredObject}

case class ObjectStoreEventHandler(store: Datastore)
    extends RequeueHandler[IO, ObjectStoreEvent]
    with StrictLogging {

  override def apply(event: ObjectStoreEvent): IO[bucky.RequeueConsumeAction] =
    for {
      _ <- IO { logger.info(s"got an object store event: $event") }
      _ <- handleEvent(event)
    } yield Ack

  private def handleEvent(event: ObjectStoreEvent): IO[Unit] =
    event match {
      case ObjectStoreEvent.Created(location, sizeBytes) =>
        store.modify(_.updated(location, StoredObject(sizeBytes))).void

      case ObjectStoreEvent.Removed(location) =>
        store.modify(_ - location).void

      case _ =>
        IO(println(s"ignoring event: $event"))
    }

}
