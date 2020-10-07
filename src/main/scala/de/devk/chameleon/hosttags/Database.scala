package de.devk.chameleon.hosttags

import akka.actor.{ActorSystem, CoordinatedShutdown}
import com.typesafe.config.Config
import de.devk.chameleon.Implicits.CoordinatedShutdownOps
import de.devk.chameleon.Logging
import de.devk.chameleon.jmx.JmxManager
import de.devk.chameleon.jmx.hostTags.DatabaseMetrics
import io.circe.syntax._
import org.flywaydb.core.Flyway
import org.sqlite.SQLiteDataSource
import slick.jdbc.JdbcBackend.Database
import slick.jdbc.SQLiteProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class Database(config: Config, jmxManager: JmxManager)(implicit actorSystem: ActorSystem) extends Logging {
  private val databasePath = config.getString("hostTags.database.path")
  private val database = s"jdbc:sqlite:$databasePath"

  private val databaseMetrics = new DatabaseMetrics
  jmxManager.registerMBean("de.devk.chameleon.hostTags:type=DatabaseMetrics", databaseMetrics)

  private val sqliteDataSource = new SQLiteDataSource()
  sqliteDataSource.setUrl(database)

  private val flyway = Flyway.configure().dataSource(sqliteDataSource).load()
  flyway.migrate()

  private val db = Database.forDataSource(sqliteDataSource, None)
  CoordinatedShutdown(actorSystem).addShutdownTask(CoordinatedShutdown.PhaseServiceStop, "Database")(Future.successful(db.close()))

  private val hostTags = TableQuery[HostTags]

  def insertOrUpdateHostTags(name: String, tags: Map[String, String]): Future[Int] = {
    val insertedOrUpdatedRows = db.run(hostTags.insertOrUpdate((name, tags.asJson.noSpaces)))
    databaseMetrics.incrementInsertedOrUpdatedHostTags()

    insertedOrUpdatedRows
  }

  def tagsForHost(name: String): Future[Map[String, String]] = {
    import io.circe.parser._

    val queriedTags = db.run(hostTags.filter(_.name === name).map(_.tags).result).flatMap { queryResult =>
      if (queryResult.size < 2) {

        queryResult.headOption.map { s =>
          val optionalTags = for {
            json <- parse(s).toOption
            jsonObject <- json.asObject
          } yield jsonObject.toMap

          optionalTags.map { tags =>
            val (elements, errors) = tags.view
              .mapValues(e => e.asString)
              .partition(_._2.isDefined)

            val removedHostTags =
              if (errors.nonEmpty) {
                removeHostTags(name)
              } else {
                Future.successful()
              }

            removedHostTags
              .map(_ => elements.map { case (key, value) =>
                key -> value.get
              }.toMap)
          }.getOrElse(removeHostTags(name))

        }.getOrElse(Future.successful(Map.empty[String, String]))
      } else {
        removeHostTags(name)
      }
    }

    databaseMetrics.incrementQueriedHostTags()

    queriedTags
  }

  def removeHostTags(name: String): Future[Map[String, String]] = {
    db.run(hostTags.filter(_.name === name).delete).map { removedRows =>
      logger.error(s"Removed $removedRows for hostname $name")
      databaseMetrics.addRemovedHostTags(removedRows)

      Map.empty
    }
  }

}


class HostTags(tag: Tag) extends Table[(String, String)](tag, "host_tags") {
  def name = column[String]("name", O.PrimaryKey)
  def tags = column[String]("tags")

  def * = (name, tags)
}