package app.dayflow.core

import kotlinx.serialization.Serializable
import java.time.*
import java.time.temporal.ChronoUnit
import java.util.UUID

@Serializable enum class TaskKind { TASK, NOTE }
@Serializable enum class ThemeMode { SYSTEM, LIGHT, DARK }
@Serializable enum class RepeatFrequency { DAILY, WEEKLY, MONTHLY, YEARLY }
@Serializable data class TaskSection(val id: String = UUID.randomUUID().toString(), val listId: String, val name: String, val order: Long = System.currentTimeMillis())
@Serializable data class AppSettings(val theme: ThemeMode = ThemeMode.SYSTEM, val board: Boolean = false, val weekStart: Int = 7, val focusMinutes: Int = 25, val breakMinutes: Int = 5) {
    fun validate() { require(weekStart in 1..7 && focusMinutes in 1..180 && breakMinutes in 1..60) }
}
@Serializable data class Recurrence(val frequency: RepeatFrequency, val interval: Int = 1, val weekdays: Set<Int> = emptySet(), val anchorDate: String, val endDate: String? = null, val count: Int? = null) {
    fun validate() { require(interval in 1..999 && weekdays.all { it in 1..7 }); LocalDate.parse(anchorDate); endDate?.let { require(LocalDate.parse(it) >= LocalDate.parse(anchorDate)) }; require(count == null || count in 1..100000) }
    fun next(after: LocalDate, occurrence: Int): LocalDate? {
        validate()
        if (count != null && occurrence >= count) return null
        val anchor = LocalDate.parse(anchorDate)
        val result = when (frequency) {
            RepeatFrequency.DAILY -> after.plusDays(interval.toLong())
            RepeatFrequency.WEEKLY -> if (weekdays.isEmpty()) after.plusWeeks(interval.toLong()) else {
                val weekAnchor = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                generateSequence(after.plusDays(1)) { it.plusDays(1) }.first { it.dayOfWeek.value in weekdays && ChronoUnit.WEEKS.between(weekAnchor, it.minusDays((it.dayOfWeek.value - 1).toLong())) % interval == 0L }
            }
            RepeatFrequency.MONTHLY -> YearMonth.from(after).plusMonths(interval.toLong()).let { it.atDay(minOf(anchor.dayOfMonth, it.lengthOfMonth())) }
            RepeatFrequency.YEARLY -> after.plusYears(interval.toLong()).let { LocalDate.of(it.year, anchor.month, minOf(anchor.dayOfMonth, YearMonth.of(it.year, anchor.month).lengthOfMonth())) }
        }
        return result.takeIf { endDate == null || it <= LocalDate.parse(endDate) }
    }
}

fun TaskItem.reminderInstants(): List<Instant> {
    if (deletedAt != null || completedAt != null || dueDate == null || dueTime == null) return emptyList()
    val base = LocalDate.parse(dueDate).atTime(LocalTime.parse(dueTime)).atZone(ZoneId.of(zoneId)).toInstant()
    return reminderMinutes.distinct().map { base.minusSeconds(it * 60L) }.sorted()
}

@Serializable data class Habit(val id: String = UUID.randomUUID().toString(), val title: String, val target: Int = 1, val unit: String = "회", val weekdays: Set<Int> = (1..7).toSet(), val archived: Boolean = false, val createdDate: String = LocalDate.now().toString()) {
    fun validate() { require(title.isNotBlank() && title.length <= 200 && target in 1..100000 && weekdays.isNotEmpty() && weekdays.all { it in 1..7 }); LocalDate.parse(createdDate) }
}
@Serializable data class HabitLog(val habitId: String, val date: String, val count: Int)
fun Snapshot.habitCount(id: String, date: LocalDate): Int = habitLogs.find { it.habitId == id && it.date == date.toString() }?.count ?: 0
fun Snapshot.habitStreak(habit: Habit, today: LocalDate): Int {
    var date = today
    var count = 0
    val start = LocalDate.parse(habit.createdDate)
    if (date.dayOfWeek.value in habit.weekdays && habitCount(habit.id, date) < habit.target) date = date.minusDays(1)
    while (date >= start) {
        if (date.dayOfWeek.value in habit.weekdays) { if (habitCount(habit.id, date) < habit.target) break; count++ }
        date = date.minusDays(1)
    }
    return count
}

@Serializable enum class FocusMode { POMODORO, STOPWATCH, BREAK }
@Serializable data class ActiveFocus(val id: String = UUID.randomUUID().toString(), val taskId: String? = null, val mode: FocusMode = FocusMode.POMODORO, val startedAt: Long = System.currentTimeMillis(), val runningSince: Long? = startedAt, val elapsedMillis: Long = 0, val durationSeconds: Int = 1500) {
    fun elapsed(now: Long) = elapsedMillis + (runningSince?.let { (now - it).coerceAtLeast(0) } ?: 0)
    fun remaining(now: Long) = (durationSeconds - elapsed(now) / 1000).coerceAtLeast(0)
}
@Serializable data class FocusSession(val id: String, val taskId: String?, val mode: FocusMode, val startedAt: Long, val endedAt: Long, val elapsedSeconds: Long, val completed: Boolean)
@Serializable data class Countdown(val id: String = UUID.randomUUID().toString(), val title: String, val date: String, val yearly: Boolean = false)
fun Countdown.daysFrom(today: LocalDate): Long {
    val date = LocalDate.parse(date)
    if (!yearly) return ChronoUnit.DAYS.between(today, date)
    var next = date.withYear(today.year)
    if (next < today) next = date.withYear(today.year + 1)
    return ChronoUnit.DAYS.between(today, next)
}
@Serializable data class SavedFilter(val id: String = UUID.randomUUID().toString(), val name: String, val listIds: Set<String> = emptySet(), val tags: Set<String> = emptySet(), val minimumPriority: Int = 0, val overdueOnly: Boolean = false) {
    fun matches(task: TaskItem, today: LocalDate) = (listIds.isEmpty() || task.listId in listIds) && (tags.isEmpty() || tags.all { it in task.tags }) && task.priority >= minimumPriority && (!overdueOnly || task.dueDate?.let { LocalDate.parse(it) < today } == true)
}
@Serializable data class TaskTemplate(val id: String = UUID.randomUUID().toString(), val name: String, val task: TaskItem)
