package taskflow

import cats.effect.{IO, IOApp}
import cats.syntax.semigroupk.*
import com.comcast.ip4s.*
import doobie.implicits.*
import doobie.*

import org.http4s.HttpRoutes
import org.http4s.dsl.io.*
import org.http4s.implicits.*
import org.http4s.ember.server.EmberServerBuilder

import taskflow.api.TaskRoutes
import taskflow.persistence.{Database, PostgresTaskRepository}

object Main extends IOApp.Simple:

  private val healthRoutes = HttpRoutes.of[IO] {
    case GET -> Root / "health" =>
      Ok("TaskFlow is running")
  }

  def run: IO[Unit] =
    val config = Database.Config.fromEnv
    Database.migrate(config).flatMap { _ =>
      Database.transactor(config).use { xa =>
      val service = new PostgresTaskRepository(xa)
      val app = (healthRoutes <+> TaskRoutes.routes(service)).orNotFound

      // Fail immediately if the DB is not reachable, rather than silently serving errors.
      sql"SELECT 1".query[Int].unique.transact(xa).flatMap { _ =>
        EmberServerBuilder
          .default[IO]
          .withHost(ipv4"0.0.0.0")
          .withPort(port"8080")
          .withHttpApp(app)
          .build
          .use(_ => IO.never)
      }
      }
    }
