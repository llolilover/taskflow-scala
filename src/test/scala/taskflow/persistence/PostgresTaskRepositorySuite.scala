package taskflow.persistence

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.*
import munit.FunSuite

import taskflow.domain.*

/** Run with TASKFLOW_INTEGRATION=1 sbt test, after docker compose up -d db. */
class PostgresTaskRepositorySuite extends FunSuite:

  private def withRepo[A](use: PostgresTaskRepository => IO[A]): A =
    Database.transactor(Database.Config.fromEnv)
      .use(xa => use(new PostgresTaskRepository(xa)))
      .unsafeRunSync()

  if sys.env.get("TASKFLOW_INTEGRATION").contains("1") then

    test("tasks survive creating a new repository object"):
      val (created, loaded) = withRepo { repo =>
        for
          saved <- repo.create("Persisted in PostgreSQL")
          found <- repo.find(saved.toOption.get.id)
        yield (saved, found)
      }
      assert(created.isRight)
      assertEquals(loaded.map(_.title), Some("Persisted in PostgreSQL"))

    test("status update records one history entry; stale version does not"):
      val (first, stale, rows) = withRepo { repo =>
        for
          created <- repo.create("Audit check")
          id = created.toOption.get.id
          first <- repo.changeStatus(id, TaskStatus.InProgress, 0L)
          stale <- repo.changeStatus(id, TaskStatus.Cancelled, 0L)
          rows <- repo.history(id)
        yield (first, stale, rows)
      }
      assert(first.isRight)
      assertEquals(stale, Left(DomainError.VersionConflict))
      assertEquals(rows.size, 1)
      assertEquals(rows.head.fromStatus, TaskStatus.Backlog)
      assertEquals(rows.head.toStatus, TaskStatus.InProgress)

    test("two parallel updates yield one winner and one history record"):
      val (first, second, stored, rows) = withRepo { repo =>
        for
          created <- repo.create("Parallel updates")
          id = created.toOption.get.id
          results <- (
            repo.changeStatus(id, TaskStatus.InProgress, 0L),
            repo.changeStatus(id, TaskStatus.Cancelled, 0L)
          ).parTupled
          stored <- repo.find(id)
          rows <- repo.history(id)
        yield (results._1, results._2, stored, rows)
      }
      assertEquals(List(first, second).count(_.isRight), 1)
      assertEquals(List(first, second).count(_ == Left(DomainError.VersionConflict)), 1)
      assertEquals(stored.map(_.version), Some(1L))
      assertEquals(rows.size, 1)
