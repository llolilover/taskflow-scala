# TaskFlow

[![Scala CI](https://github.com/llolilover/taskflow-scala/actions/workflows/ci.yml/badge.svg)](https://github.com/llolilover/taskflow-scala/actions/workflows/ci.yml)

REST API для управления задачами на **Scala 3** и **PostgreSQL**.

## Возможности

- Создание задач, получение списка и просмотр истории изменений статуса.
- Жизненный цикл задач: `Backlog → InProgress → Done` с поддержкой отмены и возврата в `Backlog` из `InProgress`.
- Защита от конкурентных изменений через optimistic locking (`version`).
- Атомарное сохранение статуса и истории переходов в PostgreSQL.
- Фильтрация по статусу и исполнителю, курсорная пагинация.

## Стек

**Scala 3**, Cats Effect, http4s, Circe, Doobie, PostgreSQL, Flyway, Docker Compose, MUnit.

## Запуск

Требуются Docker и Docker Compose.

```bash
docker compose up -d --build
```

API: `http://localhost:8080`

```bash
# Создать задачу
curl -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title":"Implement API"}'

# Получить страницу задач
curl 'http://localhost:8080/tasks?status=Backlog&limit=20'
```

Остановить сервисы: `docker compose down`. Данные PostgreSQL сохраняются в Docker volume.

## API

| Метод | Endpoint | Описание |
| --- | --- | --- |
| `GET` | `/health` | Проверка доступности |
| `POST` | `/tasks` | Создание задачи |
| `GET` | `/tasks` | Список задач с фильтрами и пагинацией |
| `GET` | `/tasks/{id}` | Задача по ID |
| `PATCH` | `/tasks/{id}/status` | Изменение статуса |
| `GET` | `/tasks/{id}/history` | История переходов |

Подробнее: [HTTP API](docs/API.md).

## Тестирование

```bash
docker compose up -d db
TASKFLOW_INTEGRATION=1 sbt test
```

Для проверки запущенного API: `bash scripts/smoke.sh`.

Тесты и сборка Docker-образа запускаются автоматически в GitHub Actions.

## Архитектура

`HTTP (http4s) → TaskOperations → Doobie → PostgreSQL`

Правила переходов вынесены в доменную модель. Миграции схемы выполняет Flyway при старте приложения.

Детали: [Architecture](docs/ARCHITECTURE.md).
