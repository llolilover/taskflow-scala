# TaskFlow — Scala 3 backend

Учебный backend-сервис управления задачами с типобезопасными правилами, конкурентными обновлениями и постоянным хранением в PostgreSQL.

## Реализовано

- Scala 3: opaque types, enum, ADT/Either, чистая модель переходов статусов
- http4s + Cats Effect + Circe: HTTP API и JSON
- Doobie + HikariCP + PostgreSQL: постоянное хранение и пул соединений
- Optimistic locking: `UPDATE ... WHERE version = expectedVersion`
- Аудит: изменение задачи и запись истории в одной SQL-транзакции
- Фильтры по статусу и исполнителю; keyset-пагинация по UUID с лимитом 1–100
- Flyway: версия схемы БД, V1 (таблицы) и V2 (индексы)
- MUnit: unit + PostgreSQL-интеграционные тесты
- Docker Compose: приложение и база запускаются вместе
- GitHub Actions: компиляция, тесты, сборка JAR, Docker и HTTP smoke-тесты

## Быстрый запуск через Docker

Требуются Docker и Docker Compose:

```bash
docker compose up -d --build
docker compose ps
bash scripts/smoke.sh
```

HTTP API доступен на `http://127.0.0.1:8080`. Постоянные данные хранятся в Docker-томе `taskflow_pgdata`.

Остановить:

```bash
docker compose down
```

**Не используй** `docker compose down -v`, если хочешь сохранить задачи: этот флаг удалит том базы.

### Если раньше уже запускал TaskFlow с прежней схемой

Раньше `db/init/001_schema.sql` создавал таблицы напрямую, без Flyway. Теперь Flyway по умолчанию **не присваивает версию существующей непустой БД**, чтобы случайно не принять незнакомую схему.

**Для твоей существующей локальной БД из старой версии TaskFlow** разреши однократную базовую отметку версии:

```bash
FLYWAY_BASELINE_ON_MIGRATE=true docker compose up -d --build
```

Flyway отметит исходную схему как V1 и применит V2 (индексы с `IF NOT EXISTS`); таблицы и данные сохранятся. После успешного первого запуска возвращайся к обычной команде без этого флага. **Не используй baseline для чужих или неизвестных баз**.

Если ты уже собрал сервис вручную локально и хочешь выполнить одноразовый baseline без Docker-приложения:

```bash
docker compose up -d db
FLYWAY_BASELINE_ON_MIGRATE=true sbt run
```

## Локальный запуск без Docker-контейнера приложения

Требуются JDK 21, sbt и запущенный контейнер PostgreSQL:

```bash
docker compose up -d db
TASKFLOW_INTEGRATION=1 sbt clean test
sbt run
```

Для новой базы Flyway создаст таблицы сам. Для **старой непустой базы** требуется описанный выше однократный baseline.

## Сборка отдельного JAR

```bash
sbt assembly
java -jar target/scala-3.3.9/taskflow.jar
```

Соединение задаётся переменными `DB_URL`, `DB_USER`, `DB_PASSWORD`. Значения по умолчанию — только для локальной разработки, не для production.

## Архитектура

`TaskRoutes` → `TaskOperations` → `PostgresTaskRepository` → PostgreSQL

Доменная модель применяет правила переходов без взаимодействия с БД. Конкурентная защита работает атомарным SQL `UPDATE` с проверкой версии. В той же транзакции создаётся запись истории. `TaskService` на `Ref` используется только в быстрых unit-тестах.

Миграции запускаются **до** открытия HTTP-порта; при ошибке соединения или миграции сервис не стартует.

## API

См. [docs/API.md](docs/API.md). Основные маршруты: `POST /tasks`, `GET /tasks?limit=20&status=Backlog`, `GET /tasks/{id}`, `PATCH /tasks/{id}/status`, `GET /tasks/{id}/history`.

## Ограничения

Проект предназначен для демонстрации backend-разработки. Пока нет аутентификации, метрик, деплоя в облако и production-конфигурации секретов. Доступ к HTTP и PostgreSQL в Compose ограничен loopback-интерфейсом компьютера.
