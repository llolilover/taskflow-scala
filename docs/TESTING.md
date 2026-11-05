# TaskFlow — тестирование и ручная проверка

## 1. Автоматические тесты

Без PostgreSQL:

```bash
sbt test
```

С базой:

```bash
docker compose up -d db
TASKFLOW_INTEGRATION=1 sbt clean test
```

Интеграционные тесты добавляют в локальную базу несколько учебных задач; не запускайте их против реальной или чужой БД.

## 2. Проверка контейнеров и HTTP

```bash
docker compose up -d --build
docker compose ps
bash scripts/smoke.sh
```

Если хранилище осталось от старой версии, в которой SQL-схема создавалась через `db/init/001_schema.sql`, для *первой* миграции используйте проверенный порядок из README и **не удаляйте** Docker-том.

Smoke-тест проверяет HTTP health, создание, получение и переход статуса, устаревшую/отрицательную версию, историю, фильтры и две страницы по курсору.

## 3. Проверка постоянного хранения

Создайте задачу (команда выводит JSON с идентификатором):

```bash
curl -sS -X POST http://127.0.0.1:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Task survives restart"}'
```

Скопируйте `id`. Затем:

```bash
docker compose restart app
curl -i http://127.0.0.1:8080/tasks/UUID_ИЗ_ОТВЕТА
```

HTTP должен вернуть сохранённую задачу. В CI аналогичный тест автоматически сохраняет статус и историю, перезапускает приложение и проверяет обе записи.

## 4. Проверка Flyway

```bash
docker compose exec db psql -U taskflow -d taskflow \
  -c "SELECT installed_rank, version, description, success FROM flyway_schema_history ORDER BY installed_rank;"
```

Для новой базы должны присутствовать успешно применённые версии `1` и `2`. Повторный запуск приложения не должен добавлять дубликаты миграций.

## 5. Просмотр данных

```bash
docker compose exec db psql -U taskflow -d taskflow \
  -c "SELECT id, title, status, version FROM tasks ORDER BY created_at DESC LIMIT 10;"
```

```bash
docker compose exec db psql -U taskflow -d taskflow \
  -c "SELECT task_id, from_status, to_status, occurred_at FROM task_history ORDER BY occurred_at DESC LIMIT 10;"
```

## 6. Остановка

```bash
docker compose down
```

Это сохраняет том `taskflow_pgdata`. **`docker compose down -v` удаляет данные БД.**

## 7. Что проверить на собеседовании

Уметь без подсказок объяснить:
1. Чем `TaskId` отличается от `UUID`, а `Either` — от исключений.
2. Почему `IO` обозначает эффект и чем `Ref.modify` отличается от обычной переменной.
3. Почему один предварительный SELECT не защищает от `lost update`, а SQL UPDATE с `version` — защищает.
4. Почему UPDATE задачи и INSERT в историю находятся в одной транзакции.
5. Чем keyset отличается от OFFSET и почему здесь порядок по UUID, а не по времени.
6. Для чего Flyway, Doobie, HikariCP и Docker Compose; что происходит при запуске.
7. Какие ограничения у текущей реализации и что пришлось бы изменить для production.
