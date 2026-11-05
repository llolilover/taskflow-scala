package taskflow.service

import cats.effect.IO
import taskflow.domain.*

/** The HTTP layer uses this interface independently of the persistence mechanism. */
trait TaskOperations:
  def create(title: String, assigneeId: Option[UserId]): IO[Either[DomainError, Task]]
  def create(title: String): IO[Either[DomainError, Task]] = create(title, None)
  def find(id: TaskId): IO[Option[Task]]
  def list: IO[List[Task]]
  def page(query: TaskQuery): IO[TaskPage]
  def changeStatus(id: TaskId, next: TaskStatus, expectedVersion: Long): IO[Either[DomainError, Task]]
  def history(id: TaskId): IO[List[TaskHistory]]
