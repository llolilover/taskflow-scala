package taskflow.persistence

import cats.effect.IO
import cats.syntax.all.*
import doobie.*
import doobie.implicits.*
import doobie.postgres.implicits.*
import java.util.UUID

import taskflow.domain.*
import taskflow.domain.TaskId.*
import taskflow.domain.UserId.*
import taskflow.service.TaskOperations

/**
 * PostgreSQL implementation:
 * every transact(xa) encloses its ConnectionIO program in one DB transaction.
 */
final class PostgresTaskRepository(xa: Transactor[IO]) extends TaskOperations:

  private type TaskRow = (UUID, String, Option[UUID], String, Long)

  private def decode(row: TaskRow): Task =
    val (uuid, title, assignee, status, version) = row
    Task(
      TaskId.fromUUID(uuid),
      title,
      assignee.map(UserId.fromUUID),
      TaskStatus.valueOf(status),
      version
    )

  private def findTx(id: TaskId): ConnectionIO[Option[Task]] =
    sql"""SELECT id, title, assignee_id, status, version
           FROM tasks WHERE id = ${id.value}"""
      .query[TaskRow].option.map(_.map(decode))

  def create(title: String): IO[Either[DomainError, Task]] =
    IO(UUID.randomUUID()).flatMap { uuid =>
      Task.create(TaskId.fromUUID(uuid), title, None) match
        case Left(error) =>
          IO.pure[Either[DomainError, Task]](Left(error))
        case Right(task) =>
          sql"""INSERT INTO tasks (id, title, assignee_id, status, version)
                 VALUES (${task.id.value}, ${task.title}, ${task.assigneeId.map(_.value)},
                         ${task.status.toString}, ${task.version})"""
            .update.run.transact(xa)
            .as(Right(task): Either[DomainError, Task])
    }

  def find(id: TaskId): IO[Option[Task]] =
    findTx(id).transact(xa)

  def list: IO[List[Task]] =
    sql"""SELECT id, title, assignee_id, status, version FROM tasks
           ORDER BY created_at, id"""
      .query[TaskRow].to[List].map(_.map(decode)).transact(xa)

  def history(id: TaskId): IO[List[TaskHistory]] =
    sql"""SELECT id, task_id, from_status, to_status, occurred_at::text
           FROM task_history WHERE task_id = ${id.value}
           ORDER BY occurred_at, id"""
      .query[(UUID, UUID, String, String, String)].to[List]
      .map(_.map { case (eventId, taskId, from, to, at) =>
        TaskHistory(
          eventId,
          TaskId.fromUUID(taskId),
          TaskStatus.valueOf(from),
          TaskStatus.valueOf(to),
          at
        )
      })
      .transact(xa)

  def changeStatus(
      id: TaskId,
      next: TaskStatus,
      expectedVersion: Long
  ): IO[Either[DomainError, Task]] =
    IO(UUID.randomUUID()).flatMap { eventId =>
      val tx: ConnectionIO[Either[DomainError, Task]] =
        findTx(id).flatMap {
          case None =>
            (Left(DomainError.NotFound): Either[DomainError, Task]).pure[ConnectionIO]

          case Some(current) if current.version != expectedVersion =>
            (Left(DomainError.VersionConflict): Either[DomainError, Task]).pure[ConnectionIO]

          case Some(current) =>
            Task.transition(current, next) match
              case Left(error) =>
                (Left(error): Either[DomainError, Task]).pure[ConnectionIO]

              case Right(updated) =>
                sql"""UPDATE tasks
                       SET status = ${next.toString}, version = version + 1, updated_at = now()
                       WHERE id = ${id.value} AND version = ${expectedVersion}"""
                  .update.run.flatMap {
                    case 1 =>
                      sql"""INSERT INTO task_history (id, task_id, from_status, to_status)
                             VALUES (${eventId}, ${id.value}, ${current.status.toString}, ${next.toString})"""
                        .update.run
                        .as(Right(updated): Either[DomainError, Task])
                    case _ =>
                      (Left(DomainError.VersionConflict): Either[DomainError, Task]).pure[ConnectionIO]
                  }
        }

      // UPDATE and history INSERT commit or roll back together.
      tx.transact(xa)
    }
