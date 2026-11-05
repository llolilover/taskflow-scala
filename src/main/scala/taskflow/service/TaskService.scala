package taskflow.service

import cats.effect.{IO, Ref}
import java.util.UUID

import taskflow.domain.*
import taskflow.domain.TaskId.*

/** Fast in-memory implementation used by unit tests. The running server uses PostgreSQL. */
final class TaskService(state: Ref[IO, Map[TaskId, Task]]) extends TaskOperations:

  def create(title: String): IO[Either[DomainError, Task]] =
    IO(UUID.randomUUID()).flatMap { uuid =>
      Task.create(TaskId.fromUUID(uuid), title, None) match
        case Left(error) =>
          IO.pure[Either[DomainError, Task]](Left(error))
        case Right(task) =>
          state.update(_ + (task.id -> task))
            .map(_ => Right(task): Either[DomainError, Task])
    }

  def find(id: TaskId): IO[Option[Task]] =
    state.get.map(_.get(id))

  def list: IO[List[Task]] =
    state.get.map(_.values.toList.sortBy(_.id.value.toString))

  /** One atomic check-and-update: concurrent callers using the same version cannot both succeed. */
  def changeStatus(
      id: TaskId,
      next: TaskStatus,
      expectedVersion: Long
  ): IO[Either[DomainError, Task]] =
    state.modify[Either[DomainError, Task]] { tasks =>
      tasks.get(id) match
        case None =>
          (tasks, Left(DomainError.NotFound))
        case Some(current) if current.version != expectedVersion =>
          (tasks, Left(DomainError.VersionConflict))
        case Some(current) =>
          Task.transition(current, next) match
            case Left(error) =>
              (tasks, Left(error))
            case Right(updated) =>
              (tasks.updated(id, updated), Right(updated))
    }

  // Only the PostgreSQL implementation records a persistent audit history.
  def history(id: TaskId): IO[List[TaskHistory]] = IO.pure(Nil)
