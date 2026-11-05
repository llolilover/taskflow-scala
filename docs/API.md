# TaskFlow HTTP API

Base URL: `http://localhost:8080`

| Метод | Endpoint | Результат |
| --- | --- | --- |
| `GET` | `/health` | `200`, текст состояния |
| `POST` | `/tasks` | `201`, созданная задача |
| `GET` | `/tasks` | `200`, страница задач |
| `GET` | `/tasks/{id}` | `200`, задача |
| `PATCH` | `/tasks/{id}/status` | `200`, обновлённая задача |
| `GET` | `/tasks/{id}/history` | `200`, история переходов |

## Создание задачи

`POST /tasks`

```json
{
  "title": "Implement API",
  "assigneeId": null
}
```

`assigneeId` — необязательный UUID исполнителя. Название не может быть пустым, максимальная длина — 120 символов.

Ответ содержит `id`, `title`, `status` (`Backlog`), `version` (`0`) и `assigneeId`.

## Список задач

`GET /tasks?status=Backlog&limit=20`

| Параметр | Значение |
| --- | --- |
| `status` | `Backlog`, `InProgress`, `Done`, `Cancelled` |
| `assignee` | UUID исполнителя |
| `limit` | 1–100, по умолчанию 20 |
| `cursor` | UUID из `nextCursor` предыдущей страницы |

```json
{
  "items": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "title": "Implement API",
      "status": "Backlog",
      "version": 0,
      "assigneeId": null
    }
  ],
  "nextCursor": null
}
```

`nextCursor = null` означает конец списка. Порядок — по UUID задачи.

## Изменение статуса

`PATCH /tasks/{id}/status`

```json
{
  "status": "InProgress",
  "expectedVersion": 0
}
```

`expectedVersion` — неотрицательная версия задачи, прочитанная клиентом. Успешный переход увеличивает `version` на 1 и создаёт запись в истории.

## История

`GET /tasks/{id}/history` возвращает массив событий с полями `id`, `fromStatus`, `toStatus`, `occurredAt`.

## Ошибки

| Код | Причина |
| --- | --- |
| `400` | Некорректные параметры или тело запроса |
| `404` | Задача не найдена |
| `409` | Конфликт версии или запрещённый переход |
