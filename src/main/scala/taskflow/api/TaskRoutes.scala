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

  private final case class CreateTaskRequest(title: String)
  private object CreateTaskRequest:
    given Decoder[CreateTaskRequest] = deriveDecoder[CreateTaskRequest]

  private final case class ChangeStatusRequest(status: String, expectedVersion: Long)
  private object ChangeStatusRequest:
    given Decoder[ChangeStatusRequest] = deriveDecoder[ChangeStatusRequest]

  private final case class TaskResponse(id: String, title: String, status: String, version: Long)
  private object TaskResponse:
    given Encoder[TaskResponse] = deriveEncoder[TaskResponse]
    def from(task: Task): TaskResponse =
      TaskResponse(task.id.value.toString, task.title, task.status.toString, task.version)

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
  private given taskListEncoder: EntityEncoder[IO, List[TaskResponse]] = jsonEncoderOf[IO, List[TaskResponse]]
  private given historyListEncoder: EntityEncoder[IO, List[HistoryResponse]] = jsonEncoderOf[IO, List[HistoryResponse]]
  private given EntityEncoder[IO, ErrorResponse] = jsonEncoderOf[IO, ErrorResponse]

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
            service.create(body.title).flatMap {
              case Left(error) => errorResponse(error)
              case Right(task) => Created(TaskResponse.from(task))
            }
        }

      case GET -> Root / "tasks" =>
        service.list.flatMap(tasks => Ok(tasks.map(TaskResponse.from)))

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
