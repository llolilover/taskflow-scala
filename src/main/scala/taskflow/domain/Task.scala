package taskflow.domain

import java.util.UUID

opaque type TaskId = UUID
object TaskId:
  def fromUUID(id: UUID): TaskId = id
  extension (id: TaskId) def value: UUID = id

opaque type UserId = UUID
object UserId:
  def fromUUID(id: UUID): UserId = id
  extension (id: UserId) def value: UUID = id

enum TaskStatus:
  case Backlog, InProgress, Done, Cancelled

object TaskStatus:
  def parse(raw: String): Option[TaskStatus] =
    TaskStatus.values.find(_.toString.equalsIgnoreCase(raw.trim))

sealed trait DomainError:
  def message: String

object DomainError:
  case object NotFound extends DomainError:
    val message = "Task not found"

  case object VersionConflict extends DomainError:
    val message = "Task version is outdated"

  final case class Validation(message: String) extends DomainError

  final case class InvalidTransition(from: TaskStatus, to: TaskStatus) extends DomainError:
    def message: String = s"Transition $from -> $to is forbidden"

final case class Task(
    id: TaskId,
    title: String,
    assigneeId: Option[UserId],
    status: TaskStatus,
    version: Long
)

object Task:
  val MaxTitleLength = 120

  def create(id: TaskId, rawTitle: String, assigneeId: Option[UserId]): Either[DomainError, Task] =
    validateTitle(rawTitle).map(title => Task(id, title, assigneeId, TaskStatus.Backlog, 0L))


  private def validateTitle(raw: String): Either[DomainError, String] =
    val title = raw.trim
    if title.isEmpty then Left(DomainError.Validation("Title must not be empty"))
    else if title.length > MaxTitleLength then
      Left(DomainError.Validation(s"Title must be <= $MaxTitleLength characters"))
    else Right(title)

  def transition(task: Task, next: TaskStatus): Either[DomainError, Task] =
    if TaskRules.canTransition(task.status, next) then
      Right(task.copy(status = next, version = task.version + 1L))
    else Left(DomainError.InvalidTransition(task.status, next))

  
  def rename(task: Task, rawTitle: String): Either[DomainError, Task] = 
    validateTitle(rawTitle).map { newTitle => 
      if newTitle == task.title then task
      else task.copy(title = newTitle, version = task.version + 1)
    }



object TaskRules:
  def canTransition(from: TaskStatus, to: TaskStatus): Boolean =
    (from, to) match
      case (TaskStatus.Backlog, TaskStatus.InProgress)   => true
      case (TaskStatus.Backlog, TaskStatus.Cancelled)    => true
      case (TaskStatus.InProgress, TaskStatus.Backlog)   => true
      case (TaskStatus.InProgress, TaskStatus.Done)      => true
      case (TaskStatus.InProgress, TaskStatus.Cancelled) => true
      case _                                             => false
