# TaskFlow API

Базовый адрес: `http://127.0.0.1:8080`.

| Метод | URL | Ответ |
| --- | --- | --- |
| GET | `/health` | 200, строка состояния |
| POST | `/tasks` | 201, созданная задача |
| GET | `/tasks` | 200, список по дате создания |
| GET | `/tasks/{uuid}` | 200, конкретная задача |
| PATCH | `/tasks/{uuid}/status` | 200, задача с новой версией |
| GET | `/tasks/{uuid}/history` | 200, история переходов |

Создать задачу:

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
- `400`: неверный JSON, UUID, статус или название
- `404`: задача отсутствует
- `409`: версия устарела или переход запрещён

Важно: новая запись истории появляется только при **успешном** изменении статуса, внутри той же SQL-транзакции, что и обновление задачи.
