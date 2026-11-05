package taskflow.api

import cats.effect.IO
import java.util.UUID
import scala.util.Try

import _root_.io.circe.{Decoder, Encoder}
import _root_.io.circe.generic.semiauto.{deriveDecoder, deriveEncoder}

import org.http4s.{EntityDecoder, EntityEncoder, HttpRoutes, Response}
import org.http4s.circe.{jsonEncoderOf, jsonOf}
import org.http4s.dsl.io.*

import taskflow.domain.*
import taskflow.domain.TaskId.*
import taskflow.service.TaskOperations

object TaskRoutes:

  private final case class CreateTaskRequest(title: String, assigneeId: Option[String])
  private object CreateTaskRequest:
    given Decoder[CreateTaskRequest] = deriveDecoder[CreateTaskRequest]

  private final case class ChangeStatusRequest(status: String, expectedVersion: Long)
  private object ChangeStatusRequest:
    given Decoder[ChangeStatusRequest] = deriveDecoder[ChangeStatusRequest]

  private final case class TaskResponse(id: String, title: String, status: String, version: Long, assigneeId: Option[String])
  private object TaskResponse:
    given Encoder[TaskResponse] = deriveEncoder[TaskResponse]
    def from(task: Task): TaskResponse =
      TaskResponse(task.id.value.toString, task.title, task.status.toString, task.version, task.assigneeId.map(_.value.toString))

  private final case class TaskPageResponse(items: List[TaskResponse], nextCursor: Option[String])
  private object TaskPageResponse:
    given Encoder[TaskPageResponse] = deriveEncoder[TaskPageResponse]
    def from(page: TaskPage): TaskPageResponse =
      TaskPageResponse(page.items.map(TaskResponse.from), page.nextCursor)

  private final case class HistoryResponse(id: String, fromStatus: String, toStatus: String, occurredAt: String)
  private object HistoryResponse:
    given Encoder[HistoryResponse] = deriveEncoder[HistoryResponse]
    def from(event: TaskHistory): HistoryResponse =
      HistoryResponse(event.id.toString, event.fromStatus.toString, event.toStatus.toString, event.occurredAt)

  private final case class ErrorResponse(error: String)
  private object ErrorResponse:
    given Encoder[ErrorResponse] = deriveEncoder[ErrorResponse]

  private given EntityDecoder[IO, CreateTaskRequest] = jsonOf[IO, CreateTaskRequest]
  private given EntityDecoder[IO, ChangeStatusRequest] = jsonOf[IO, ChangeStatusRequest]
  private given EntityEncoder[IO, TaskResponse] = jsonEncoderOf[IO, TaskResponse]
  private given EntityEncoder[IO, TaskPageResponse] = jsonEncoderOf[IO, TaskPageResponse]
  private given taskListEncoder: EntityEncoder[IO, List[TaskResponse]] = jsonEncoderOf[IO, List[TaskResponse]]
  private given historyListEncoder: EntityEncoder[IO, List[HistoryResponse]] = jsonEncoderOf[IO, List[HistoryResponse]]
  private given EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

  private def parseUuid(raw: String): Option[UUID] =
    Try(UUID.fromString(raw)).toOption

  private def optionalUuid(
      raw: Option[String],
      label: String
  ): Either[String, Option[UUID]] =
    raw match
      case None => Right(None)
      case Some(value) =>
        parseUuid(value).toRight(s"Invalid $label UUID").map(Some(_))

  private def parseListQuery(params: Map[String, String]): Either[String, TaskQuery] =
    for
      limit <- params.get("limit") match
        case None => Right(TaskQuery.DefaultLimit)
        case Some(raw) =>
          Try(raw.toInt).toOption
            .filter(value => value >= 1 && value <= TaskQuery.MaxLimit)
            .toRight(s"limit must be between 1 and ${TaskQuery.MaxLimit}")
      status <- params.get("status") match
        case None => Right(None)
        case Some(raw) =>
          TaskStatus.parse(raw).toRight("Invalid status filter").map(Some(_))
      assignee <- optionalUuid(params.get("assignee"), "assignee")
      cursor <- optionalUuid(params.get("cursor"), "cursor")
    yield TaskQuery(status, assignee.map(UserId.fromUUID), limit, cursor.map(TaskId.fromUUID))

  private def errorResponse(error: DomainError): IO[Response[IO]] =
    error match
      case DomainError.NotFound => NotFound(ErrorResponse(error.message))
      case DomainError.VersionConflict => Conflict(ErrorResponse(error.message))
      case _: DomainError.InvalidTransition => Conflict(ErrorResponse(error.message))
      case _: DomainError.Validation => BadRequest(ErrorResponse(error.message))

  def routes(service: TaskOperations): HttpRoutes[IO] =
    HttpRoutes.of[IO] {
      case req @ POST -> Root / "tasks" =>
        req.attemptAs[CreateTaskRequest].value.flatMap {
          case Left(_) => BadRequest(ErrorResponse("Invalid JSON body"))
          case Right(body) =>
            optionalUuid(body.assigneeId, "assigneeId") match
              case Left(message) => BadRequest(ErrorResponse(message))
              case Right(assignee) =>
                service.create(body.title, assignee.map(UserId.fromUUID)).flatMap {
                  case Left(error) => errorResponse(error)
                  case Right(task) => Created(TaskResponse.from(task))
                }
        }

      case req @ GET -> Root / "tasks" =>
        parseListQuery(req.uri.query.params) match
          case Left(message) => BadRequest(ErrorResponse(message))
          case Right(query) =>
            service.page(query).flatMap(page => Ok(TaskPageResponse.from(page)))

      case GET -> Root / "tasks" / rawId / "history" =>
        Try(UUID.fromString(rawId)).toOption match
          case None => BadRequest(ErrorResponse("Invalid task ID"))
          case Some(uuid) =>
            val id = TaskId.fromUUID(uuid)
            service.find(id).flatMap {
              case None => NotFound(ErrorResponse("Task not found"))
              case Some(_) =>
                service.history(id).flatMap(events => Ok(events.map(HistoryResponse.from)))
            }

      case GET -> Root / "tasks" / rawId =>
        Try(UUID.fromString(rawId)).toOption match
          case None => BadRequest(ErrorResponse("Invalid task ID"))
          case Some(uuid) =>
            service.find(TaskId.fromUUID(uuid)).flatMap {
              case None => NotFound(ErrorResponse("Task not found"))
              case Some(task) => Ok(TaskResponse.from(task))
            }

      case req @ PATCH -> Root / "tasks" / rawId / "status" =>
        Try(UUID.fromString(rawId)).toOption match
          case None => BadRequest(ErrorResponse("Invalid task ID"))
          case Some(uuid) =>
            req.attemptAs[ChangeStatusRequest].value.flatMap {
              case Left(_) => BadRequest(ErrorResponse("Invalid JSON body"))
              case Right(body) =>
                TaskStatus.parse(body.status) match
                  case None => BadRequest(ErrorResponse("Unknown task status"))
                  case Some(status) =>
                    service.changeStatus(TaskId.fromUUID(uuid), status, body.expectedVersion)
                      .flatMap {
                        case Left(error) => errorResponse(error)
                        case Right(task) => Ok(TaskResponse.from(task))
                      }
            }
    }
