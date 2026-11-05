package taskflow.persistence

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.*
import java.util.UUID
import munit.FunSuite
import doobie.*
import doobie.implicits.*

import taskflow.domain.*

/** Run with TASKFLOW_INTEGRATION=1 sbt test, after docker compose up -d db. */
class PostgresTaskRepositorySuite extends FunSuite:

  private def withRepo[A](use: PostgresTaskRepository => IO[A]): A =
    Database.transactor(Database.Config.fromEnv)
      .use(xa => use(new PostgresTaskRepository(xa)))
      .unsafeRunSync()

  override def beforeAll(): Unit =
    if sys.env.get("TASKFLOW_INTEGRATION").contains("1") then
      Database.migrate(Database.Config.fromEnv).unsafeRunSync()

  if sys.env.get("TASKFLOW_INTEGRATION").contains("1") then

    test("tasks survive closing the connection pool and opening another"):
      val created = withRepo(_.create("Persisted in PostgreSQL")).toOption.get
      val loaded = withRepo(_.find(created.id))
      assertEquals(loaded.map(_.title), Some("Persisted in PostgreSQL"))
      assertEquals(loaded.map(_.version), Some(0L))

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

    test("keyset pagination filters by status and assignee"):
      val user = UserId.fromUUID(UUID.randomUUID())
      val result = withRepo { repo =>
        for
          a <- repo.create("Pagination A", Some(user))
          b <- repo.create("Pagination B", Some(user))
          c <- repo.create("Pagination C", Some(user))
          firstPage <- repo.page(TaskQuery(None, Some(user), 2, None))
          nextPage <- repo.page(TaskQuery(
            None, Some(user), 2,
            firstPage.nextCursor.map(value => TaskId.fromUUID(UUID.fromString(value)))
          ))
          updated <- repo.changeStatus(a.toOption.get.id, TaskStatus.InProgress, 0L)
          filtered <- repo.page(TaskQuery(Some(TaskStatus.InProgress), Some(user), 10, None))
        yield (List(a, b, c), firstPage, nextPage, updated, filtered)
      }
      assert(result._1.forall(_.isRight))
      assertEquals(result._2.items.size, 2)
      assert(result._2.nextCursor.nonEmpty)
      assertEquals(result._3.items.size, 1)
      assertEquals(result._3.nextCursor, None)
      val seen = (result._2.items ++ result._3.items).map(_.id).toSet
      assertEquals(seen, result._1.map(_.toOption.get.id).toSet)
      assert(result._4.isRight)
      assertEquals(result._5.items.map(_.id), List(result._1.head.toOption.get.id))

    test("page size does not exceed requested limit"):
      val user = UserId.fromUUID(UUID.randomUUID())
      val result = withRepo { repo =>
        for
          _ <- repo.create("Bound A", Some(user))
          _ <- repo.create("Bound B", Some(user))
          page <- repo.page(TaskQuery(None, Some(user), 1, None))
        yield page
      }
      assertEquals(result.items.size, 1)
      assert(result.nextCursor.nonEmpty)

    test("Flyway applied V1 and V2 migrations"):
      val applied = Database.transactor(Database.Config.fromEnv).use { xa =>
        sql"SELECT count(*) FROM flyway_schema_history WHERE success = true"
          .query[Long].unique.transact(xa)
      }.unsafeRunSync()
      assert(applied >= 2L)

    test("forbidden transition does not mutate task or create audit entry"):
      val (rejected, saved, events) = withRepo { repo =>
        for
          created <- repo.create("Forbidden transition")
          id = created.toOption.get.id
          rejected <- repo.changeStatus(id, TaskStatus.Done, 0L)
          saved <- repo.find(id)
          events <- repo.history(id)
        yield (rejected, saved, events)
      }
      assertEquals(rejected, Left(DomainError.InvalidTransition(TaskStatus.Backlog, TaskStatus.Done)))
      assertEquals(saved.map(_.status), Some(TaskStatus.Backlog))
      assertEquals(saved.map(_.version), Some(0L))
      assertEquals(events.size, 0)

    test("Flyway migrations can run twice without modifying history"):
      val config = Database.Config.fromEnv
      def applied: IO[Long] =
        Database.transactor(config).use { xa =>
          sql"SELECT count(*) FROM flyway_schema_history WHERE success = true"
            .query[Long].unique.transact(xa)
        }
      val (before, after) = (for
        before <- applied
        _ <- Database.migrate(config)
        after <- applied
      yield (before, after)).unsafeRunSync()
      assert(before >= 2L)
      assertEquals(after, before)
