package taskflow.service

import cats.effect.IO
import taskflow.domain.*

/** The HTTP layer uses this interface independently of the persistence mechanism. */
trait TaskOperations:
  def create(title: String): IO[Either[DomainError, Task]]
  def find(id: TaskId): IO[Option[Task]]
  def list: IO[List[Task]]
  def changeStatus(id: TaskId, next: TaskStatus, expectedVersion: Long): IO[Either[DomainError, Task]]
  def history(id: TaskId): IO[List[TaskHistory]]
