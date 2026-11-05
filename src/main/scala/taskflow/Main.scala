package taskflow

import cats.effect.{IO, IOApp, Ref}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*

import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.ember.server.EmberServerBuilder

import taskflow.api.TaskRoutes
import taskflow.domain.{Task, TaskId}
import taskflow.service.TaskService

object Main extends IOApp.Simple:

  private val healthRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok("TaskFlow is running")
  }

  def run: IO[Unit] =
    Ref.of[IO, Map[TaskId, Task]](Map.empty[TaskId, Task]).flatMap { state =>
      val service = new TaskService(state)
      val app = (healthRoutes <+> TaskRoutes.routes(service)).orNotFound

      EmberServerBuilder
        .default[IO]
        .withHost(ipv4"127.0.0.1")
        .withPort(port"8080")
        .withHttpApp(app)
        .build
        .use(_ => IO.never)
    }
