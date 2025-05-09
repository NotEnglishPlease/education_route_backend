package com

import io.ktor.server.application.*
import java.sql.Connection
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction

object Client : Table("client") {
    val id = integer("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()
    val password = varchar("password", 255)
    val parentName = varchar("parent_name", 255)
    val phone = varchar("phone", 255)
    val childName = varchar("child_name", 255)
    val childBirthday = varchar("child_birthday", 255)
    override val primaryKey = PrimaryKey(id)
}

object Group : Table("group") {
    val id = integer("id").autoIncrement()
    val number = integer("number")
    val level = integer("level")
    override val primaryKey = PrimaryKey(id)
}

object Employee : Table("employee") {
    val id = integer("id").autoIncrement()
    val name = varchar("name", 255)
    val profile = varchar("profile", 255)
    val photo = blob("photo").nullable()
    val type = varchar("type", 50)
    override val primaryKey = PrimaryKey(id)
}

object Lesson : Table("lesson") {
    val id = integer("id").autoIncrement()
    val employeeId = integer("id_employee").references(Employee.id)
    val groupId = integer("id_group").references(Group.id)
    val subject = varchar("subject", 255)
    val topic = varchar("topic", 255).nullable()
    val time = varchar("time", 255)
    val date = varchar("date", 255)
    override val primaryKey = PrimaryKey(id)
}

object LessonVisit : Table("lesson_visit") {
    val id = integer("id").autoIncrement()
    val studentId = integer("id_student").references(Client.id)
    val lessonId = integer("id_lesson").references(Lesson.id)
    val visit = bool("visit")
    val grade = integer("grade")
    val homework = varchar("homework", 255).nullable()
    override val primaryKey = PrimaryKey(id)
}

fun Application.configureDatabases() {
    Database.connect(
        url = "jdbc:postgresql://127.0.0.1:8181/mydatabase",
        user = "postgres",
        driver = "org.postgresql.Driver",
        password = "postgres",
    )
    transaction {
        SchemaUtils.create(Client, Group, Employee, Lesson, LessonVisit)
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
