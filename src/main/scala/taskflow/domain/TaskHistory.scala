package taskflow.domain

import java.util.UUID

/** Persisted audit record of a successful status transition. */
final case class TaskHistory(
    id: UUID,
    taskId: TaskId,
    fromStatus: TaskStatus,
    toStatus: TaskStatus,
    occurredAt: String
)
