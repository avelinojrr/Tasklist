package tasklist

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.datetime.*
import java.io.File

enum class Priority(val string: String, val color: String) {
    CRITICAL("C", "\u001B[101m \u001B[0m"),
    HIGH("H", "\u001B[103m \u001B[0m"),
    NORMAL("N", "\u001B[102m \u001B[0m"),
    LOW("L", "\u001B[104m \u001B[0m")
}

enum class DueTag(val string: String, val color: String) {
    TODAY("T", "\u001B[103m \u001B[0m"),
    IN_TIME("I", "\u001B[102m \u001B[0m"),
    OVERDUE("O", "\u001B[101m \u001B[0m")
}

// we store the date and time as an ISO String (localDateTime.toString())
class Task(val priority: Priority, val isoDateTime: String, val lines: List<String>) {
    val isBlank get() = lines.isEmpty()

    val dueTag get(): DueTag {
        val taskDate = LocalDateTime.parse(isoDateTime)
        val currentDate = Clock.System.now().toLocalDateTime(TimeZone.UTC).date
        val numberOfDays = currentDate.daysUntil(taskDate.date)

        return when {
            numberOfDays == 0 -> DueTag.TODAY
            numberOfDays > 0 -> DueTag.IN_TIME
            else -> DueTag.OVERDUE
        }
    }
}

object TaskPrinter {
    private const val DIVIDER = "+----+------------+-------+---+---+--------------------------------------------+"
    private const val HEADER  = "| N  |    Date    | Time  | P | D |                   Task                     |"
    private const val EMPTY   = "|    |            |       |   |   |"
    private const val MAX_LENGTH_TASK_LINE = 44

    fun printTasks(tasks: List<Task>) {
        println(DIVIDER)
        println(HEADER)
        println(DIVIDER)

        tasks.forEachIndexed { i, task ->
            print(task, i + 1)
            println(DIVIDER)
        }
    }

    private fun print(task: Task, number: Int) {
        val n = number.toString().padEnd(2)
        val (date, time) = task.isoDateTime.split('T')

        task.lines.forEachIndexed { lineIndex, line ->
            line.chunked(MAX_LENGTH_TASK_LINE).forEachIndexed { chunkIndex, s ->
                val lineChunk = s.padEnd(MAX_LENGTH_TASK_LINE, ' ')

                // only the first chunk of the first line will display other task data
                if (lineIndex == 0 && chunkIndex == 0) {
                    println("| $n | $date | $time | ${task.priority.color} | ${task.dueTag.color} |$lineChunk|")
                } else
                    println("$EMPTY$lineChunk|")
            }
        }
    }
}

const val JSON_FILENAME = "tasklist.json"

fun main() {
    val tasks = loadFromJsonFile()

    while (true) {
        println("Input an action (add, print, edit, delete, end):")

        when (readLine()!!.trim()) {
            "end" -> {
                saveJsonFile(tasks)
                println("Tasklist exiting!")
                return
            }
            "add" -> addAction(tasks)
            "print" -> printAction(tasks)
            "edit" -> editAction(tasks)
            "delete" -> deleteAction(tasks)
            else -> println("The input action is invalid")
        }
    }
}

fun loadFromJsonFile(): MutableList<Task> {
    val jsonFile = File(JSON_FILENAME)
    return if (jsonFile.exists())
        getTaskListAdapter().fromJson(jsonFile.readText())?.toMutableList() ?: mutableListOf()
    else
        mutableListOf()
}

fun saveJsonFile(tasks: List<Task>) {
    File(JSON_FILENAME).writeText(getTaskListAdapter().toJson(tasks))
}

fun getTaskListAdapter(): JsonAdapter<List<Task>> {
    val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    val type = Types.newParameterizedType(List::class.java, Task::class.java)
    return moshi.adapter(type)
}

fun addAction(tasks: MutableList<Task>) {
    val newTask = getNewTask()
    if (newTask.isBlank) {
        println("The task is blank")
    } else {
        tasks.add(newTask)
    }
}

fun editAction(tasks: MutableList<Task> ) {
    printAction(tasks)

    if (tasks.isEmpty()) return

    val index = getValidIndexOfTask(tasks.size)
    val oldTask = tasks[index]

    when (getValidTaskField()) {
        "priority" -> {
            val newPriority = getValidPriority()
            tasks[index] = Task(newPriority, oldTask.isoDateTime, oldTask.lines)
        }
        "date" -> {
            val newLocalDate = getValidLocalDate()
            val old = oldTask.isoDateTime.toLocalDateTime()
            val newLocalDateTime = LocalDateTime(newLocalDate.year, newLocalDate.month, newLocalDate.dayOfMonth, old.hour, old.minute)
            tasks[index] = Task(oldTask.priority, newLocalDateTime.toString(), oldTask.lines)
        }
        "time" -> {
            val newLocalDateTime = getValidLocalDateTime(oldTask.isoDateTime.toLocalDateTime().date)
            tasks[index] = Task(oldTask.priority, newLocalDateTime.toString(), oldTask.lines)
        }
        "task" -> {
            val newLines = getTaskLines()
            tasks[index] = Task(oldTask.priority, oldTask.isoDateTime, newLines)
        }
    }
    println("The task is changed")
}

fun getValidTaskField(): String {
    while (true) {
        val fields = listOf("priority", "date", "time", "task")
        println("Input a field to edit (${fields.joinToString(", ")}):")
        val input = readln()
        if (input in fields) {
            return input
        } else {
            println("Invalid field")
        }
    }
}

fun deleteAction(tasks: MutableList<Task>) {
    printAction(tasks)

    if (tasks.isEmpty()) return

    val index = getValidIndexOfTask(tasks.size)
    tasks.removeAt(index)
    println("The task is deleted")
}

fun getValidIndexOfTask(noOfTasks: Int): Int {
    while (true) {
        println("Input the task number (1-$noOfTasks):")
        try {
            val input = readln().toInt()
            if (input in 1..noOfTasks) {
                return input - 1
            } else {
                println("Invalid task number")
            }
        } catch (nfex: NumberFormatException) {
            println("Invalid task number")
        }
    }
}

fun getNewTask(): Task {
    val priority = getValidPriority()
    val localDate = getValidLocalDate()
    val localDateTime = getValidLocalDateTime(localDate)
    val lines = getTaskLines()

    return Task(priority, localDateTime.toString(), lines)
}

fun getValidPriority():Priority {
    while (true) {
        val validPrios = Priority.values().map { it.string }
        println("Input the task priority (${validPrios.joinToString(", ")}):")
        when(readln().uppercase()) {
            "C" -> return Priority.CRITICAL
            "H" -> return Priority.HIGH
            "N" -> return Priority.NORMAL
            "L" -> return Priority.LOW
        }
    }
}

fun getValidLocalDate(): LocalDate {
    while (true) {
        println("Input the date (yyyy-mm-dd):")
        try {
            return parseDate(readln())
        } catch (ex: Exception) {
            println("The input date is invalid")
        }
    }
}

// parse yyyy-mm-dd to LocalDate
// attention: user can only enter one month or day (no leading 0s), e.g. 2022-1-3
fun parseDate(s: String): LocalDate {
    val parts = s.split('-').map { it.toInt() }
    return LocalDate(parts[0], parts[1], parts[2])
}

// user inputs time, and we add this to the given LocalDate
fun getValidLocalDateTime(localDate: LocalDate): LocalDateTime {
    while (true) {
        println("Input the time (hh:mm):")
        try {
            val parts = readln().split(':').map { it.toInt() }
            return LocalDateTime(localDate.year, localDate.month, localDate.dayOfMonth, parts[0], parts[1])
        } catch (ex: Exception) {
            println("The input time is invalid")
        }
    }
}

fun getTaskLines(): List<String> {
    println("Input a new task (enter a blank line to end):")
    val lines = mutableListOf<String>()
    while (true) {
        val input = readln().trim()
        if (input.isEmpty()) {
            return lines
        } else {
            lines.add(input)
        }
    }
}

fun printAction(tasks: List<Task>) {
    if (tasks.isEmpty()) {
        println("No tasks have been input")
        return
    }

    TaskPrinter.printTasks(tasks)
}