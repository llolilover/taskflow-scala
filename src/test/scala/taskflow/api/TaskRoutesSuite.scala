package taskflow.api

import cats.effect.{IO, Ref}
import cats.effect.unsafe.implicits.global
import cats.syntax.all.*
import _root_.io.circe.parser.parse
import java.util.UUID
import munit.FunSuite
import org.http4s.*
import org.http4s.implicits.*
import org.typelevel.ci.CIString

import taskflow.domain.*
import taskflow.service.TaskService

/** Exercise the actual HTTP route decoder, validation and response mapping. */
class TaskRoutesSuite extends FunSuite:

  private def freshApp: IO[HttpApp[IO]] =
    Ref.of[IO, Map[TaskId, Task]](Map.empty[TaskId, Task])
      .map(ref => TaskRoutes.routes(new TaskService(ref)).orNotFound)

  private def get(path: String): Request[IO] =
    Request[IO](method = Method.GET, uri = Uri.unsafeFromString(path))

  private def jsonRequest(method: Method, path: String, json: String): Request[IO] =
    Request[IO](method = method, uri = Uri.unsafeFromString(path))
      .withEntity(json)
      .putHeaders(Header.Raw(CIString("Content-Type"), "application/json"))

  private def taskId(body: String): String =
    parse(body).toOption.get.hcursor.get[String]("id").toOption.get

  test("create task, normalize title and fetch via HTTP"):
    val result = (for
      http <- freshApp
      created <- http.run(jsonRequest(Method.POST, "/tasks", """{"title":"  Scala backend  "}"""))
      json <- created.as[String]
      fetched <- http.run(get(s"/tasks/${taskId(json)}"))
      fetchedJson <- fetched.as[String]
    yield (created.status, fetched.status, fetchedJson)).unsafeRunSync()

    assertEquals(result._1, Status.Created)
    assertEquals(result._2, Status.Ok)
    val fields = parse(result._3).toOption.get.hcursor
    assertEquals(fields.get[String]("title").toOption, Some("Scala backend"))
    assertEquals(fields.get[String]("status").toOption, Some("Backlog"))
    assertEquals(fields.get[Long]("version").toOption, Some(0L))

  test("malformed JSON, empty titles and invalid assignee IDs return 400"):
    val statuses = (for
      http <- freshApp
      responses <- List(
        """{not-json""",
        """{"title":"  "}""",
        """{"title":"Example","assigneeId":"not-a-uuid"}"""
      ).traverse(body => http.run(jsonRequest(Method.POST, "/tasks", body)).map(_.status))
    yield responses).unsafeRunSync()

    assertEquals(statuses, List.fill(3)(Status.BadRequest))

  test("PATCH rejects negative versions; stale versions and forbidden transitions return 409"):
    val result = (for
      http <- freshApp
      created <- http.run(jsonRequest(Method.POST, "/tasks", """{"title":"Transition"}"""))
      body <- created.as[String]
      path = s"/tasks/${taskId(body)}/status"
      invalid <- http.run(jsonRequest(Method.PATCH, path,
        """{"status":"InProgress","expectedVersion":-1}"""))
      valid <- http.run(jsonRequest(Method.PATCH, path,
        """{"status":"InProgress","expectedVersion":0}"""))
      stale <- http.run(jsonRequest(Method.PATCH, path,
        """{"status":"Cancelled","expectedVersion":0}"""))
      forbidden <- http.run(jsonRequest(Method.PATCH, path,
        """{"status":"InProgress","expectedVersion":1}"""))
      fetched <- http.run(get(s"/tasks/${taskId(body)}"))
      saved <- fetched.as[String]
    yield (invalid.status, valid.status, stale.status, forbidden.status, saved)).unsafeRunSync()

    assertEquals(result._1, Status.BadRequest)
    assertEquals(result._2, Status.Ok)
    assertEquals(result._3, Status.Conflict)
    assertEquals(result._4, Status.Conflict)
    val task = parse(result._5).toOption.get.hcursor
    assertEquals(task.get[String]("status").toOption, Some("InProgress"))
    assertEquals(task.get[Long]("version").toOption, Some(1L))

  test("list rejects invalid filters and IDs"):
    val statuses = (for
      http <- freshApp
      invalid <- List(
        "/tasks?limit=0",
        "/tasks?limit=101",
        "/tasks?limit=NaN",
        "/tasks?status=Unknown",
        "/tasks?assignee=bad-uuid",
        "/tasks?cursor=bad-uuid",
        "/tasks/not-a-uuid"
      ).traverse(url => http.run(get(url)).map(_.status))
      missing <- http.run(get(s"/tasks/${UUID.randomUUID()}"))
    yield (invalid, missing.status)).unsafeRunSync()

    assertEquals(statuses._1, List.fill(7)(Status.BadRequest))
    assertEquals(statuses._2, Status.NotFound)

  test("filter by assignee and follow the nextCursor across pages"):
    val assignee = UUID.randomUUID().toString
    val result = (for
      http <- freshApp
      first <- http.run(jsonRequest(Method.POST, "/tasks",
        s"""{"title":"First","assigneeId":"$assignee"}"""))
      second <- http.run(jsonRequest(Method.POST, "/tasks",
        s"""{"title":"Second","assigneeId":"$assignee"}"""))
      firstBody <- first.as[String]
      secondBody <- second.as[String]
      page1 <- http.run(get(s"/tasks?assignee=$assignee&limit=1"))
      page1Body <- page1.as[String]
      cursor = parse(page1Body).toOption.get.hcursor.get[String]("nextCursor").toOption.get
      page2 <- http.run(get(s"/tasks?assignee=$assignee&limit=1&cursor=$cursor"))
      page2Body <- page2.as[String]
    yield (firstBody, secondBody, page1Body, page2Body)).unsafeRunSync()

    val expected = Set(taskId(result._1), taskId(result._2))
    def ids(body: String): Set[String] =
      parse(body).toOption.get.hcursor.downField("items").focus.get.asArray.get
        .map(item => item.hcursor.get[String]("id").toOption.get).toSet

    assertEquals(ids(result._3).size, 1)
    assertEquals(ids(result._4).size, 1)
    assertEquals(ids(result._3) ++ ids(result._4), expected)
    assertEquals(
      parse(result._4).toOption.get.hcursor.get[Option[String]]("nextCursor").toOption,
      Some(None)
    )
