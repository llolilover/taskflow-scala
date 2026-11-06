package taskflow.domain

import java.util.UUID
import munit.FunSuite

class TaskSuite extends FunSuite:
  private def task: Task =
    Task.create(TaskId.fromUUID(UUID.randomUUID()), "  Implement API  ", None).toOption.get

  test("creation trims title and initializes version"):
    val created = task

    assertEquals(created.title, "Implement API")
    assertEquals(created.status, TaskStatus.Backlog)
    assertEquals(created.version, 0L)

  test("title must not be blank"):
    assert(Task.create(TaskId.fromUUID(UUID.randomUUID()), "  ", None).isLeft)

  test("title cannot be longer than 120 characters"):
    assert(Task.create(TaskId.fromUUID(UUID.randomUUID()), "x" * 121, None).isLeft)

  test("Backlog -> InProgress increments version"):
    val changed = Task.transition(task, TaskStatus.InProgress).toOption.get
    assertEquals(changed.status, TaskStatus.InProgress)
    assertEquals(changed.version, 1L)

  test("InProgress -> Done is allowed"):
    val done = for
      first <- Task.transition(task, TaskStatus.InProgress)
      second <- Task.transition(first, TaskStatus.Done)
    yield second
    assertEquals(done.toOption.map(_.status), Some(TaskStatus.Done))

  test("Done -> Backlog is forbidden"):
    val done = Task.transition(Task.transition(task, TaskStatus.InProgress).toOption.get, TaskStatus.Done).toOption.get
    assertEquals(Task.transition(done, TaskStatus.Backlog), Left(DomainError.InvalidTransition(TaskStatus.Done, TaskStatus.Backlog)))

  test("cancelled task cannot reopen"):
    val cancelled = Task.transition(task, TaskStatus.Cancelled).toOption.get
    assert(Task.transition(cancelled, TaskStatus.InProgress).isLeft)

  test("cannot transition to same status"):
    assert(Task.transition(task, TaskStatus.Backlog).isLeft)

  test("parse status is case insensitive"):
    assertEquals(TaskStatus.parse("inprogress"), Some(TaskStatus.InProgress))
    assertEquals(TaskStatus.parse("unknown"), None)

  test("rename updates title and increments version"):
    val original = task
    val result = Task.rename(original, "New title")

    assertEquals(
      result,
      Right(original.copy(title = "New title", version = 1L))
    )
    assertEquals(original.title, "Implement API")
    assertEquals(original.version, 0L)

  test("rename with same title preserves task"):
    val original = task
    val result = Task.rename(original, "  Implement API  ")

    assertEquals(result, Right(original))

  test("rename rejects blank title"):
    val result = Task.rename(task, "   ")

    assertEquals(
      result,
      Left(DomainError.Validation("Title must not be empty"))
    )

  test("rename rejects title longer than 120 characters"):
    val result = Task.rename(task, "x" * 121)

    assertEquals(
      result,
      Left(DomainError.Validation("Title must be <= 120 characters"))
    )

  test("rename trims surrounding whitespace"):
    val result = Task.rename(task, "  Clean title  ")

    assertEquals(result.toOption.map(_.title), Some("Clean title"))
    assertEquals(result.toOption.map(_.version), Some(1L))
