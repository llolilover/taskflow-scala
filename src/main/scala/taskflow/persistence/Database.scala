package taskflow.persistence

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor
import org.flywaydb.core.Flyway

/** Environment-based configuration for the local PostgreSQL database. */
object Database:
  final case class Config(url: String, user: String, password: String)

  object Config:
    def fromEnv: Config =
      Config(
        sys.env.getOrElse("DB_URL", "jdbc:postgresql://127.0.0.1:55432/taskflow"),
        sys.env.getOrElse("DB_USER", "taskflow"),
        sys.env.getOrElse("DB_PASSWORD", "taskflow")
      )

  /**
   * Apply versioned migrations on startup before serving requests.
   * For an existing local database initialized by db/init/001_schema.sql,
   * opt into one-time baselining with FLYWAY_BASELINE_ON_MIGRATE=true.
   */
  def migrate(config: Config): IO[Unit] =
    IO.blocking {
      val baselineExisting =
        sys.env.get("FLYWAY_BASELINE_ON_MIGRATE").contains("true")
      val flyway = Flyway.configure()
        .dataSource(config.url, config.user, config.password)
        .locations("classpath:db/migration")
        .baselineOnMigrate(baselineExisting)
        .load()
      flyway.migrate()
      ()
    }

  def transactor(config: Config): Resource[IO, HikariTransactor[IO]] =
    Resource.eval(IO {
      val hikari = new HikariConfig()
      hikari.setDriverClassName("org.postgresql.Driver")
      hikari.setJdbcUrl(config.url)
      hikari.setUsername(config.user)
      hikari.setPassword(config.password)
      hikari.setMaximumPoolSize(8)
      hikari.setMinimumIdle(1)
      hikari.setPoolName("taskflow-db")
      hikari
    }).flatMap(config => HikariTransactor.fromHikariConfig[IO](config))
