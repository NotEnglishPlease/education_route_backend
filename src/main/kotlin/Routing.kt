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

@Serializable
data class ClientDTO(
    val id: Int,
    val childName: String,
    val parentName: String,
    val age: Int,
    val phone: String,
    val paidLessons: Int?
)

@Serializable
data class MyCourseResponse(
    val lessonVisit: LessonVisitData,
    val lesson: LessonDTO
)

@Serializable
data class LessonVisitData(
    val visit: Boolean,
    val garde: Int?,
    val date: String,
    val lessonId: Int
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
            val childBirthday = params["child_birthday"] ?: return@post call.respond(HttpStatusCode.BadRequest, "Child birthday required")

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

        // Получить список всех клиентов
        get("/clients") {
            val clients = transaction {
                Client.selectAll().map {
                    val birthday = it[Client.childBirthday]
                    val age = calculateAge(birthday)
                    
                    ClientDTO(
                        id = it[Client.id],
                        childName = it[Client.childName],
                        parentName = it[Client.parentName],
                        age = age,
                        phone = it[Client.phone],
                        paidLessons = it[Client.paidLessons]
                    )
                }
            }
            call.respond(clients)
        }

        // Обновить количество оплаченных занятий
        put("/clients/{id}/paid_lessons") {
            val id = call.parameters["id"]?.toIntOrNull()
            if (id == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid id")
                return@put
            }
            
            val params = call.receiveParameters()
            val paidLessons = params["paid_lessons"]?.toIntOrNull()
            if (paidLessons == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid paid_lessons value")
                return@put
            }
            
            val updated = transaction {
                Client.update({ Client.id eq id }) {
                    it[Client.paidLessons] = paidLessons
                }
            }
            
            if (updated > 0) {
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.NotFound, "Client not found")
            }
        }

        // Получить доступные уроки по возрасту клиента
        get("/lessons/available/{clientId}") {
            val clientId = call.parameters["clientId"]?.toIntOrNull()
            if (clientId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid client id")
                return@get
            }

            val client = transaction {
                Client.selectAll().where { Client.id eq clientId }.firstOrNull()
            }

            if (client == null) {
                call.respond(HttpStatusCode.NotFound, "Client not found")
                return@get
            }

            val clientAge = calculateAge(client[Client.childBirthday])
            
            // Получаем ID уроков, на которые уже записан пользователь
            val enrolledLessonIds = transaction {
                LessonVisit.select(LessonVisit.lessonId)
                    .where { LessonVisit.studentId eq clientId }
                    .map { it[LessonVisit.lessonId] }
            }

            val lessons = transaction {
                Lesson.selectAll()
                    .where { 
                        (Lesson.ageLevel eq clientAge) and 
                        (Lesson.id notInList enrolledLessonIds)
                    }
                    .map {
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

        // --- Новый эндпоинт: запись на занятие ---
        post("/enroll") {
            val params = call.receiveParameters()
            val clientId = params["clientId"]?.toIntOrNull()
            val lessonId = params["lessonId"]?.toIntOrNull()
            if (clientId == null || lessonId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid clientId or lessonId")
                return@post
            }

            val exists = transaction {
                LessonVisit.selectAll()
                    .where { (LessonVisit.studentId eq clientId) and (LessonVisit.lessonId eq lessonId) }
                    .count() > 0
            }
            if (exists) {
                call.respond(HttpStatusCode.Conflict, "Already enrolled")
                return@post
            }

            val lesson = transaction {
                Lesson.selectAll().where { Lesson.id eq lessonId }.firstOrNull()
            }
            if (lesson == null) {
                call.respond(HttpStatusCode.NotFound, "Lesson not found")
                return@post
            }
            val weekDay = lesson[Lesson.weekDay]

            // Определяем ближайшую дату нужного дня недели
            val today = java.time.LocalDate.now()
            val targetDay = try { java.time.DayOfWeek.valueOf(weekDay.uppercase()) } catch (e: Exception) { null }
            var nextDate = today
            if (targetDay != null) {
                while (nextDate.dayOfWeek != targetDay) {
                    nextDate = nextDate.plusDays(1)
                }
            }

            transaction {
                LessonVisit.insert {
                    it[LessonVisit.studentId] = clientId
                    it[LessonVisit.lessonId] = lessonId
                    it[LessonVisit.visit] = false
                    it[LessonVisit.garde] = null
                    it[LessonVisit.date] = nextDate.toString()
                }
            }
            call.respond(HttpStatusCode.Created, "Enrolled")
        }

        // --- Новый эндпоинт: мои курсы ---
        get("/my_courses/{clientId}") {
            val clientId = call.parameters["clientId"]?.toIntOrNull()
            if (clientId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid clientId")
                return@get
            }
            val courses = transaction {
                (LessonVisit innerJoin Lesson).selectAll().where { LessonVisit.studentId eq clientId }
                    .map {
                        MyCourseResponse(
                            lessonVisit = LessonVisitData(
                                visit = it[LessonVisit.visit],
                                garde = it[LessonVisit.garde],
                                date = it[LessonVisit.date],
                                lessonId = it[LessonVisit.lessonId]
                            ),
                            lesson = LessonDTO(
                                id = it[Lesson.id],
                                employeeId = it[Lesson.employeeId],
                                subject = it[Lesson.subject],
                                topic = it[Lesson.topic],
                                time = it[Lesson.time],
                                weekDay = it[Lesson.weekDay],
                                ageLevel = it[Lesson.ageLevel],
                                homework = it[Lesson.homework]
                            )
                        )
                    }
            }
            call.respond(courses)
        }

        // --- Новый эндпоинт: отписка от занятия ---
        delete("/unenroll/{clientId}/{lessonId}") {
            val clientId = call.parameters["clientId"]?.toIntOrNull()
            val lessonId = call.parameters["lessonId"]?.toIntOrNull()
            if (clientId == null || lessonId == null) {
                call.respond(HttpStatusCode.BadRequest, "Invalid clientId or lessonId")
                return@delete
            }

            val deleted = transaction {
                LessonVisit.deleteWhere { 
                    (LessonVisit.studentId eq clientId) and 
                    (LessonVisit.lessonId eq lessonId) 
                }
            }

            if (deleted > 0) {
                call.respond(HttpStatusCode.OK, "Unenrolled successfully")
            } else {
                call.respond(HttpStatusCode.NotFound, "Enrollment not found")
            }
        }
    }
}

private fun calculateAge(birthday: String): Int {
    val parts = birthday.split(".")
    if (parts.size != 3) return 0
    
    val day = parts[0].toIntOrNull() ?: return 0
    val month = parts[1].toIntOrNull() ?: return 0
    val year = parts[2].toIntOrNull() ?: return 0
    
    val today = java.time.LocalDate.now()
    val birthDate = java.time.LocalDate.of(year, month, day)
    
    return java.time.Period.between(birthDate, today).years
}
