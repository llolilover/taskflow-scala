-- Initial schema. Mounted into the PostgreSQL Docker image on first database initialization.
CREATE TABLE IF NOT EXISTS tasks (
  id UUID PRIMARY KEY,
  title VARCHAR(120) NOT NULL CHECK (length(btrim(title)) > 0),
  assignee_id UUID,
  status VARCHAR(20) NOT NULL CHECK (status IN ('Backlog', 'InProgress', 'Done', 'Cancelled')),
  version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS task_history (
  id UUID PRIMARY KEY,
  task_id UUID NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
  from_status VARCHAR(20) NOT NULL CHECK (from_status IN ('Backlog', 'InProgress', 'Done', 'Cancelled')),
  to_status VARCHAR(20) NOT NULL CHECK (to_status IN ('Backlog', 'InProgress', 'Done', 'Cancelled')),
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_tasks_created ON tasks(created_at, id);
CREATE INDEX IF NOT EXISTS idx_history_task ON task_history(task_id, occurred_at, id);
