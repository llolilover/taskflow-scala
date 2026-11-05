package taskflow.service

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.parallel.*
import java.util.UUID
import munit.FunSuite

import taskflow.domain.*

class TaskServiceSuite extends FunSuite:

  private def service: IO[TaskService] =
    Ref.of[IO, Map[TaskId, Task]](Map.empty).map(ref => new TaskService(ref))

  test("create, find and list"):
    val result = (for
      svc <- service
      created <- svc.create("  Learn Scala  ")
      tasks <- svc.list
      found <- svc.find(created.toOption.get.id)
    yield (created, tasks, found)).unsafeRunSync()

    assertEquals(result._1.toOption.map(_.title), Some("Learn Scala"))
    assertEquals(result._2.size, 1)
    assertEquals(result._3.map(_.title), Some("Learn Scala"))

  test("empty title does not persist"):
    val result = (for
      svc <- service
      created <- svc.create("  ")
      tasks <- svc.list
    yield (created, tasks)).unsafeRunSync()
    assert(result._1.isLeft)
    assertEquals(result._2.size, 0)

  test("two concurrent updates with the same version: exactly one succeeds"):
    val result = (for
      svc <- service
      created <- svc.create("Concurrent task")
      id = created.toOption.get.id
      updates <- (
        svc.changeStatus(id, TaskStatus.InProgress, 0L),
        svc.changeStatus(id, TaskStatus.Cancelled, 0L)
      ).parTupled
      stored <- svc.find(id)
    yield (updates, stored)).unsafeRunSync()

    val (first, second) = result._1
    assertEquals(List(first, second).count(_.isRight), 1)
    assertEquals(List(first, second).count(_ == Left(DomainError.VersionConflict)), 1)
    assertEquals(result._2.map(_.version), Some(1L))

  test("forbidden transition leaves task unchanged"):
    val result = (for
      svc <- service
      created <- svc.create("Transition")
      id = created.toOption.get.id
      started <- svc.changeStatus(id, TaskStatus.InProgress, 0L)
      done <- svc.changeStatus(id, TaskStatus.Done, 1L)
      invalid <- svc.changeStatus(id, TaskStatus.Backlog, 2L)
      stored <- svc.find(id)
    yield (started, done, invalid, stored)).unsafeRunSync()

    assert(result._1.isRight)
    assert(result._2.isRight)
    assert(result._3.isLeft)
    assertEquals(result._4.map(_.status), Some(TaskStatus.Done))
    assertEquals(result._4.map(_.version), Some(2L))

  test("missing task returns NotFound"):
    val result = (for
      svc <- service
      updated <- svc.changeStatus(TaskId.fromUUID(UUID.randomUUID()), TaskStatus.InProgress, 0L)
    yield updated).unsafeRunSync()
    assertEquals(result, Left(DomainError.NotFound))
