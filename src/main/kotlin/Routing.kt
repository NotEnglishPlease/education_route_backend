package com

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.sql.Connection
import java.sql.DriverManager
import org.jetbrains.exposed.sql.*
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.event.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.mindrot.jbcrypt.BCrypt
import com.Client
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.dao.id.EntityID
import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val success: Boolean,
    val role: String? = null,
    val message: String? = null,
    val client: ClientData? = null
)

@Serializable
data class ClientData(
    val id: Int,
    val email: String,
    val parentName: String,
    val phone: String,
    val childName: String,
    val childBirthday: String
)

@Serializable
data class LessonDTO(
    val id: Int? = null,
    val employeeId: Int,
    val subject: String,
    val topic: String? = null,
    val time: String,
    val weekDay: String,
    val ageLevel: Int,
    val homework: String? = null
)

@Serializable
data class EmployeeDTO(
    val id: Int,
    val name: String,
    val profile: String? = null
)

fun Application.configureRouting() {
    routing {
        get("/login") {
            val params = call.request.queryParameters
            val email = params["email"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Email required")
            val password = params["password"] ?: return@get call.respond(HttpStatusCode.BadRequest, "Password required")

            // Проверка на роль администратора
            if (email == "admin@gmail.com" && password == "12345678") {
                call.respond(LoginResponse(
                    success = true,
                    role = "admin"
                ))
                return@get
            }

            // Проверка на роль преподавателя
            if (email == "tutor@gmail.com" && password == "12345678") {
                call.respond(LoginResponse(
                    success = true,
                    role = "tutor"
                ))
                return@get
            }

            // Проверка учетных данных клиента
            val client = transaction {
                Client.selectAll().where { Client.email eq email }.firstOrNull()
            }

            if (client == null) {
                call.respond(LoginResponse(
                    success = false,
                    message = "Неверный email или пароль"
                ))
                return@get
            }

            val hashedPassword = client[Client.password]
            if (!BCrypt.checkpw(password, hashedPassword)) {
                call.respond(LoginResponse(
                    success = false,
                    message = "Неверный email или пароль"
                ))
                return@get
            }

            call.respond(HttpStatusCode.OK, LoginResponse(
                success = true,
                role = "client",
                client = ClientData(
                    id = client[Client.id],
                    email = client[Client.email],
                    parentName = client[Client.parentName],
                    phone = client[Client.phone],
                    childName = client[Client.childName],
                    childBirthday = client[Client.childBirthday]
                )
            ))
        }

        post("/register") {
            val params = call.receiveParameters()
            val email = params["email"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Email required")
            val password = params["password"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Password required")
            val parentName = params["name"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Parent name required")
            val phone = params["phone"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Phone required")
            val childName = params["child_name"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Child name required")

            val exists = transaction {
                Client.selectAll().where{ Client.email eq email }.count() > 0
            }
            if (exists) {
                call.respond(HttpStatusCode.Conflict, "Email already registered")
                return@post
            }

            val hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt())
            transaction {
                Client.insert {
                    it[Client.email] = email
                    it[Client.password] = hashedPassword
                    it[Client.parentName] = parentName
                    it[Client.phone] = phone
                    it[Client.childName] = childName
                    it[Client.childBirthday] = childBirthday
                }
            }
            call.respond(HttpStatusCode.Created, "Registration successful")
        }

        get("/") {
            call.respondText("Hello World!")
        }

        // Получить все занятия
        get("/lessons") {
            val lessons = transaction {
                Lesson.selectAll().map {
                    LessonDTO(
                        id = it[Lesson.id],
                        employeeId = it[Lesson.employeeId],
                        subject = it[Lesson.subject],
                        topic = it[Lesson.topic],
                        time = it[Lesson.time],
                        weekDay = it[Lesson.weekDay],
                        ageLevel = it[Lesson.ageLevel],
                        homework = it[Lesson.homework]
                    )
                }
            }
            call.respond(lessons)
        }

        // Получить одно занятие по id
        get("/lessons/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@get
            }
            val lesson = transaction {
                Lesson.selectAll().where { Lesson.id eq id }.firstOrNull()?.let {
                    LessonDTO(
                        id = it[Lesson.id],
                        employeeId = it[Lesson.employeeId],
                        subject = it[Lesson.subject],
                        topic = it[Lesson.topic],
                        time = it[Lesson.time],
                        weekDay = it[Lesson.weekDay],
                        ageLevel = it[Lesson.ageLevel],
                        homework = it[Lesson.homework]
                    )
                }
            }
            if (lesson == null) {
                call.respond(HttpStatusCode.NotFound, "Lesson not found")
            } else {
                call.respond(lesson)
            }
        }

        // Добавить новое занятие
        post("/lessons") {
            val dto = call.receive<LessonDTO>()
            println("Received time: ${dto.time}")
            val newId: Int = transaction {
                Lesson.insert {
                    it[Lesson.employeeId] = dto.employeeId
                    it[Lesson.subject] = dto.subject
                    it[Lesson.topic] = dto.topic
                    it[Lesson.time] = dto.time
                    it[Lesson.weekDay] = dto.weekDay
                    it[Lesson.ageLevel] = dto.ageLevel
                    it[Lesson.homework] = dto.homework
                } get Lesson.id
            }!!
            call.respond(HttpStatusCode.Created, mapOf("id" to newId))
        }

        // Обновить существующее занятие
        put("/lessons/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@put
            }
            val dto = call.receive<LessonDTO>()
            val updated = transaction {
                Lesson.update({ Lesson.id eq id }) {
                    it[Lesson.employeeId] = dto.employeeId
                    it[Lesson.subject] = dto.subject
                    it[Lesson.topic] = dto.topic
                    it[Lesson.time] = dto.time
                    it[Lesson.weekDay] = dto.weekDay
                    it[Lesson.ageLevel] = dto.ageLevel
                    it[Lesson.homework] = dto.homework
                }
            }
            if (updated > 0) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, "Lesson not found")
            }
        }

        // Удалить занятие
        delete("/lessons/{id}") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@delete
            }
            val deleted = transaction {
                Lesson.deleteWhere { Lesson.id eq id }
            }
            if (deleted > 0) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, "Lesson not found")
            }
        }

        // Получить id преподавателя по ФИО
        get("/employee/by_name") {
            val name = call.request.queryParameters["name"]
            if (name.isNullOrBlank()) {
                call.respond(HttpStatusCode.BadRequest, "Name required")
                return@get
            }
            val id = transaction {
                Employee.selectAll().where { Employee.name eq name }.firstOrNull()?.get(Employee.id)
            }
            if (id != null) {
                call.respond(mapOf("id" to id))
            } else {
                call.respond(HttpStatusCode.NotFound, "Employee not found")
            }
        }

        // Получить список всех преподавателей
        get("/employees") {
            val employees = transaction {
                Employee.selectAll().map {
                    EmployeeDTO(
                        id = it[Employee.id],
                        name = it[Employee.name],
                        profile = it[Employee.profile]
                    )
                }
            }
            call.respond(employees)
        }
    }
}
