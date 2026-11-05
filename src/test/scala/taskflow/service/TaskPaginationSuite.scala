package taskflow.service

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import java.util.UUID
import munit.FunSuite

import taskflow.domain.*

class TaskPaginationSuite extends FunSuite:

  test("in-memory pagination is stable and filters by assignee"):
    val user = UserId.fromUUID(UUID.randomUUID())
    val result = (for
      ref <- Ref.of[IO, Map[TaskId, Task]](Map.empty)
      service = new TaskService(ref)
      createdA <- service.create("A", Some(user))
      createdB <- service.create("B", Some(user))
      createdC <- service.create("C", Some(user))
      page1 <- service.page(TaskQuery(None, Some(user), 2, None))
      page2 <- service.page(TaskQuery(
        None, Some(user), 2,
        page1.nextCursor.map(x => TaskId.fromUUID(UUID.fromString(x)))
      ))
      all <- service.list
    yield (List(createdA, createdB, createdC), page1, page2, all)).unsafeRunSync()

    assert(result._1.forall(_.isRight))
    assertEquals(result._2.items.size, 2)
    assertEquals(result._3.items.size, 1)
    assertEquals(result._3.nextCursor, None)
    assertEquals((result._2.items ++ result._3.items).map(_.id).toSet, result._4.map(_.id).toSet)

  test("status filter reflects successful transitions"):
    val result = (for
      ref <- Ref.of[IO, Map[TaskId, Task]](Map.empty)
      service = new TaskService(ref)
      created <- service.create("Follow up")
      _ <- service.changeStatus(created.toOption.get.id, TaskStatus.InProgress, 0L)
      open <- service.page(TaskQuery(Some(TaskStatus.Backlog), None, 20, None))
      active <- service.page(TaskQuery(Some(TaskStatus.InProgress), None, 20, None))
    yield (open, active)).unsafeRunSync()

    assertEquals(result._1.items.size, 0)
    assertEquals(result._2.items.size, 1)

  test("page metadata only exposes cursor when there is another entry"):
    val ids = List.fill(3)(TaskId.fromUUID(UUID.randomUUID()))
    val tasks = ids.map(id => Task(id, "test", None, TaskStatus.Backlog, 0))
    val first = TaskPage.fromFetched(tasks, 2)
    val last = TaskPage.fromFetched(tasks.take(2), 2)
    assertEquals(first.items.size, 2)
    assertEquals(first.nextCursor, Some(ids(1).value.toString))
    assertEquals(last.nextCursor, None)
