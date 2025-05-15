package com

import io.ktor.server.application.*
import java.sql.Connection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Client : Table("client") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 50).uniqueIndex()
    val password = varchar("password", 255)
    val parentName = varchar("parent_name", 50)
    val phone = varchar("phone", 20)
    val childName = varchar("child_name", 50)
    val childBirthday = varchar("child_birthday", 50)
    val paidLessons = integer("paid_lessons").nullable()
    override val primaryKey = PrimaryKey(id)
}

object Employee : Table("employee") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 50)
    val profile = varchar("profile", 50)
    override val primaryKey = PrimaryKey(id)
}

object Lesson : Table("lesson") {
    val id = integer("id").autoIncrement()
    val employeeId = integer("id_employee").references(Employee.id)
    val subject = varchar("subject", 50)
    val topic = varchar("topic", 100).nullable()
    val time = varchar("time", 50)
    val weekDay = varchar("week_day", 15)
    val ageLevel = integer("age_level")
    val homework = varchar("homework", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

object LessonVisit : Table("lesson_visit") {
    val studentId = integer("id_student").references(Client.id)
    val lessonId = integer("id_lesson").references(Lesson.id)
    val visit = bool("visit")
    val garde = integer("garde").nullable()
    val date = varchar("date", 50)
}

fun Application.configureDatabases() {
    Database.connect(
        url = "jdbc:postgresql://127.0.0.1:8181/mydatabase",
        user = "postgres",
        driver = "org.postgresql.Driver",
        password = "postgres",
    )
    transaction {
        SchemaUtils.create(Client, Employee, Lesson, LessonVisit)
    }
}
/**
 * Makes a connection to a Postgres database.
 *
 * In order to connect to your running Postgres process,
 * please specify the following parameters in your configuration file:
 * - postgres.url -- Url of your running database process.
 * - postgres.user -- Username for database connection
 * - postgres.password -- Password for database connection
 *
 * If you don't have a database process running yet, you may need to [download]((https://www.postgresql.org/download/))
 * and install Postgres and follow the instructions [here](https://postgresapp.com/).
 * Then, you would be able to edit your url,  which is usually "jdbc:postgresql://host:port/database", as well as
 * user and password values.
 *
 *
 * @param embedded -- if [true] defaults to an embedded database for tests that runs locally in the same process.
 * In this case you don't have to provide any parameters in configuration file, and you don't have to run a process.
 *
 * @return [Connection] that represent connection to the database. Please, don't forget to close this connection when
 * your application shuts down by calling [Connection.close]
 * */
