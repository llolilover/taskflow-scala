package taskflow

import cats.effect.{IO, IOApp}
import com.comcast.ip4s.*

import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp.Simple:

  private val routes = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok("TaskFlow is running")
  }

  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"127.0.0.1")
      .withPort(port"8080")
      .withHttpApp(routes.orNotFound)
      .build
      .use(_ => IO.never)

