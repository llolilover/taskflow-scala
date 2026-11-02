package taskflow.domain

import java.util.UUID
import munit.FunSuite

class TaskSuite extends FunSuite:
  private def task: Task =
    Task.create(TaskId.fromUUID(UUID.randomUUID()), "  Implement API  ", None).toOption.get

  test("creation trims title and initializes version"):
    assertEquals(task.title, "Implement API")
    assertEquals(task.status, TaskStatus.Backlog)
    assertEquals(task.version, 0L)

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
