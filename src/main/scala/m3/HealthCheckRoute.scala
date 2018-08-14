package m3

import cats.effect.IO
import io.circe.Json
import m3.rmq.Declarations.queueName
import org.http4s.client.Client
import org.http4s.dsl.io._
import org.http4s.headers.Authorization
import org.http4s.circe._
import org.http4s.{BasicCredentials, Headers, HttpService, Request, Uri}

object HealthCheckRoute {
  def apply(client: Client[IO]): HttpService[IO] = HttpService[IO] {
    case GET -> Root | HEAD -> Root =>
      val req = Request[IO](
        uri = Uri.unsafeFromString(
          s"http://localhost:15672/api/queues/%2f/${queueName.value}"))
        .withHeaders(
          Headers(Authorization(BasicCredentials("guest", "guest")))
        )

      client.expect[Json](req).flatMap { json =>
        json.hcursor
          .downField("consumer_details")
          .as[List[Json]]
          .toOption
          .fold(ServiceUnavailable())(consumers =>
            if (consumers.nonEmpty) Ok() else ServiceUnavailable())
      }

  }
}
