package taskflow.persistence

import cats.effect.{IO, Resource}
import com.zaxxer.hikari.HikariConfig
import doobie.hikari.HikariTransactor

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
    }).flatMap(HikariTransactor.fromHikariConfig[IO])
