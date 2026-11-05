package taskflow.domain

/** Keyset pagination, ordered by UUID (stable, but not chronological). */
final case class TaskQuery(
    status: Option[TaskStatus],
    assigneeId: Option[UserId],
    limit: Int,
    cursor: Option[TaskId]
)

object TaskQuery:
  val DefaultLimit: Int = 20
  val MaxLimit: Int = 100

final case class TaskPage(items: List[Task], nextCursor: Option[String])

object TaskPage:
  import TaskId.*

  /** Fetch limit + 1 entries to determine whether a next page exists. */
  def fromFetched(rows: List[Task], limit: Int): TaskPage =
    val items = rows.take(limit)
    val next =
      if rows.size > limit then items.lastOption.map(_.id.value.toString)
      else None
    TaskPage(items, next)
