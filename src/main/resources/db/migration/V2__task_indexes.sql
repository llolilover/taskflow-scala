-- V2: indexes for task listing and audit history.
CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(created_at, id);
CREATE INDEX IF NOT EXISTS idx_history_task ON task_history(task_id, occurred_at, id);
