# TaskFlow API

Базовый адрес: `http://127.0.0.1:8080`.

| Метод | URL | Ответ |
| --- | --- | --- |
| GET | `/health` | 200, строка состояния |
| POST | `/tasks` | 201, созданная задача |
| GET | `/tasks` | 200, страница с фильтрами и курсором |
| GET | `/tasks/{uuid}` | 200, конкретная задача |
| PATCH | `/tasks/{uuid}/status` | 200, задача с новой версией |
| GET | `/tasks/{uuid}/history` | 200, история переходов |

### Получение страницы задач

`GET /tasks?status=Backlog&assignee=<UUID>&limit=20&cursor=<UUID>`

Все параметры необязательные. `status` — одно из `Backlog`, `InProgress`, `Done`, `Cancelled`; регистр неважен. `assignee` — UUID исполнителя; `limit` от 1 до 100 (по умолчанию 20). `cursor` — последний UUID из предыдущей страницы, который сервер вернул как `nextCursor`.

Результат всегда объект:

```json
{
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Read Scala docs",
      "status": "Backlog",
      "version": 0,
      "assigneeId": null
    }
  ],
  "nextCursor": null
}
```

Когда `nextCursor` не `null`, передайте его как `cursor` для следующего запроса. Сортировка **по UUID задачи, а не по времени создания**: это намеренно выбранный стабильный keyset-порядок, использующий первичный индекс `tasks.id`. Используем `LIMIT + 1`, чтобы определить наличие следующей страницы, без `COUNT(*)`. При параллельных изменениях статуса состав страниц с фильтром может меняться.

`POST /tasks` принимает обязательный `title` и необязательный `assigneeId` (UUID):

```json
{"title": "Read Scala docs", "assigneeId": "ad2541c4-c101-4d5a-b958-3dd0c5b281c8"}
```

### Создать задачу:

```bash
curl -i -X POST http://127.0.0.1:8080/tasks \
  -H 'Content-Type: application/json' -d '{"title":"Read Scala docs"}'
```

Успешный ответ `201` содержит автоматически сгенерированный UUID, `status="Backlog"` и `version=0`. Скопируй реальный `id` из ответа.

Изменить статус:

```bash
curl -i -X PATCH http://127.0.0.1:8080/tasks/UUID/status \
  -H 'Content-Type: application/json' \
  -d '{"status":"InProgress","expectedVersion":0}'
```

При успехе `version` станет `1`. Повтор с прежней версией даст `409 Conflict`.

История задачи:

```bash
curl -i http://127.0.0.1:8080/tasks/UUID/history
```

Пример события (идентификаторы и время условные):

```json
[{"id":"37c721d5-7787-42ef-85fa-e68b54523249","fromStatus":"Backlog","toStatus":"InProgress","occurredAt":"2026-11-05 19:00:00+00"}]
```

Коды ошибок:
- `400`: неверный JSON, UUID, статус, название, курсор или limit
- `404`: задача отсутствует
- `409`: версия устарела или переход запрещён

Важно: новая запись истории появляется только при **успешном** изменении статуса, внутри той же SQL-транзакции, что и обновление задачи.
