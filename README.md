# TaskFlow — Scala 3 backend

Учебный REST API управления задачами. Демонстрирует типобезопасную предметную область, работу с PostgreSQL и защиту от конфликтов при конкурентных обновлениях.

## Реализовано

- Scala 3: opaque types, enum/ADT, Either, чистая бизнес-логика переходов
- Cats Effect + http4s + Circe: HTTP API и JSON
- Doobie + PostgreSQL 16: постоянное хранение задач
- HikariCP: ограниченный пул соединений, жизненный цикл через Resource
- Optimistic locking: SQL UPDATE с проверкой version
- История переходов: UPDATE задачи и INSERT аудита в одной транзакции
- MUnit: unit-тесты и PostgreSQL-интеграционные тесты
- Docker Compose и GitHub Actions

## Быстрый запуск

Нужны JDK 21, sbt, Docker + Docker Compose.

```bash
docker compose up -d db
docker compose ps
sbt test
sbt run
```

По умолчанию приложение подключается к `jdbc:postgresql://127.0.0.1:55432/taskflow`, пользователь и пароль `taskflow`. Эти значения предназначены **только для локальной разработки**. При необходимости можно задать `DB_URL`, `DB_USER`, `DB_PASSWORD` в окружении.

В новом терминале:

```bash
bash scripts/smoke.sh
```

Для тестов с реальной БД:

```bash
TASKFLOW_INTEGRATION=1 sbt test
```

Проверить историю через `GET /tasks/{id}/history`. Полный контракт — [docs/API.md](docs/API.md).

Остановить сервер — Ctrl+C, остановить базу — `docker compose down`. **Не используй** `docker compose down -v`, если хочешь сохранить данные: этот флаг удаляет том с базой.

## Архитектура

`TaskRoutes` → `TaskOperations` → `PostgresTaskRepository` → PostgreSQL

`TaskService` — старая реализация на `Ref`, сохранена для быстрых модульных тестов. В работающем HTTP-приложении используется только PostgreSQL.

При обновлении статуса транзакция:
1. читает задачу и проверяет ожидаемую версию;
2. применяет чистое доменное правило перехода;
3. выполняет условный `UPDATE ... WHERE version = expectedVersion`;
4. только при успешном UPDATE записывает событие в `task_history`;
5. коммитит оба изменения вместе.

Это гарантирует отсутствие потерянных обновлений для одной задачи при конкурентных запросах. При ошибке транзакция откатывается.

## Ограничения

Это учебный сервис, а не production-система. Пока нет аутентификации, полноценного мигратора БД, фильтрации/пагинации и деплоя. Скрипт `db/init/001_schema.sql` запускается PostgreSQL Docker **только при первом создании пустого тома**. Не изменяй его в существующей базе без миграции.

