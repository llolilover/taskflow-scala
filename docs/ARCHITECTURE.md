# Архитектура TaskFlow

## Компоненты

```text
TaskRoutes (http4s / Circe)
        |
        v
TaskOperations
        |
        +-- PostgresTaskRepository (Doobie) --> PostgreSQL
        |
        +-- TaskService (Ref)              --> in-memory, unit tests

Task / TaskRules — доменная модель
Database          — HikariCP, Flyway
```

В рабочем приложении используется `PostgresTaskRepository`. `TaskService` — реализация интерфейса для тестов.

## Состояния задачи

| Статус | Допустимые переходы |
| --- | --- |
| `Backlog` | `InProgress`, `Cancelled` |
| `InProgress` | `Backlog`, `Done`, `Cancelled` |
| `Done` | — |
| `Cancelled` | — |

Идентификаторы задач и исполнителей реализованы через отдельные `opaque type` поверх UUID. Доменные ошибки представлены `DomainError`.

## Конкурентные обновления

`PATCH /tasks/{id}/status` принимает `expectedVersion`. Репозиторий проверяет переход и обновляет строку условно:

```sql
UPDATE tasks
SET status = :status, version = version + 1, updated_at = now()
WHERE id = :id AND version = :expected_version;
```

Если затронута одна строка, в той же транзакции добавляется запись в `task_history`. Если версия устарела, API возвращает `409 Conflict`; запись истории не создаётся.

## Хранение и выборка

- `tasks` — задачи, статус, исполнитель, версия и временные метки.
- `task_history` — история успешных переходов.
- Flyway — версии схемы `V1` и `V2`.
- `GET /tasks` — фильтры `status` и `assignee`, keyset-пагинация по UUID (`id > cursor`).

Порядок по UUID стабилен, но не соответствует хронологии создания. При изменении статусов во время постраничной выборки состав отфильтрованных страниц может меняться.

## Тесты

MUnit покрывает доменные правила, HTTP-маршруты и PostgreSQL-операции, включая конкурентные обновления и аудит. CI дополнительно проверяет запуск через Docker Compose и сохранность данных после перезапуска приложения.
