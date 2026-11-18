package app.dayflow.core

import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.util.UUID

@Serializable
data class TaskItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val listId: String = INBOX,
    val dueDate: String? = null,
    val priority: Int = 0,
    val tags: List<String> = emptyList(),
    val checklist: List<CheckItem> = emptyList(),
    val completedAt: Long? = null,
    val deletedAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
    val sectionId: String? = null,
    val parentId: String? = null,
    val order: Long = createdAt,
    val kind: TaskKind = TaskKind.TASK,
    val pinned: Boolean = false,
    val dueTime: String? = null,
    val durationMinutes: Int = 0,
    val zoneId: String = java.time.ZoneId.systemDefault().id,
    val reminderMinutes: List<Int> = emptyList(),
    val recurrence: Recurrence? = null,
    val seriesId: String? = null,
    val generatedFrom: String? = null,
    val occurrence: Int = 1,
    val deletedWithParent: String? = null,
)

@Serializable
data class CheckItem(val id: String = UUID.randomUUID().toString(), val title: String, val done: Boolean = false)

@Serializable
data class TaskList(val id: String = UUID.randomUUID().toString(), val name: String, val archived: Boolean = false)

const val INBOX = "inbox"

@Serializable
data class Snapshot(
    val schemaVersion: Int = 2,
    val lists: List<TaskList> = listOf(TaskList(INBOX, "수집함")),
    val tasks: List<TaskItem> = emptyList(),
    val sections: List<TaskSection> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val habits: List<Habit> = emptyList(),
    val habitLogs: List<HabitLog> = emptyList(),
    val focusSessions: List<FocusSession> = emptyList(),
    val activeFocus: ActiveFocus? = null,
    val countdowns: List<Countdown> = emptyList(),
    val filters: List<SavedFilter> = emptyList(),
    val templates: List<TaskTemplate> = emptyList(),
) {
    fun validate(): Snapshot {
        require(schemaVersion == 2) { "지원하지 않는 데이터 버전입니다. 원본은 보존됩니다." }
        require(lists.any { it.id == INBOX }) { "수집함이 없습니다." }
        require(lists.map { it.id }.distinct().size == lists.size) { "중복 목록 ID" }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "중복 작업 ID" }
        lists.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 100) }
        val listIds = lists.map { it.id }.toSet()
        val taskMap = tasks.associateBy { it.id }
        require(sections.map { it.id }.distinct().size == sections.size)
        sections.forEach { require(it.listId in listIds && it.name.isNotBlank() && it.name.length <= 100) }
        tasks.forEach {
            require(it.id.isNotBlank() && it.title.isNotBlank() && it.title.length <= 1000)
            require(it.notes.length <= 100_000 && it.priority in 0..3)
            require(it.listId in listIds) { "존재하지 않는 목록" }
            it.dueDate?.let(LocalDate::parse)
            it.dueTime?.let(java.time.LocalTime::parse)
            require(it.dueTime == null || it.dueDate != null) { "시간을 지정하려면 날짜가 필요합니다." }
            java.time.ZoneId.of(it.zoneId)
            require(it.durationMinutes in 0..525600 && it.reminderMinutes.size <= 5 && it.reminderMinutes.all { m -> m in 0..525600 })
            require(it.reminderMinutes.isEmpty() || (it.dueDate != null && it.dueTime != null)) { "알림에는 날짜와 시간이 필요합니다." }
            require(it.sectionId == null || sections.any { s -> s.id == it.sectionId && s.listId == it.listId })
            require(it.parentId == null || taskMap[it.parentId]?.let { p -> p.listId == it.listId && p.id != it.id } == true)
            val ancestors = mutableSetOf(it.id)
            var parent = it.parentId
            while (parent != null) { require(ancestors.add(parent) && ancestors.size <= 32) { "하위 작업 순환 또는 깊이 초과" }; parent = taskMap[parent]?.parentId }
            require(it.occurrence >= 1)
            it.recurrence?.validate()
            require(it.recurrence == null || (it.dueDate != null && it.parentId == null && it.kind == TaskKind.TASK)) { "반복은 날짜가 있는 최상위 할 일에만 설정할 수 있습니다." }
            require(it.checklist.map(CheckItem::id).distinct().size == it.checklist.size)
            require(it.checklist.all { item -> item.title.isNotBlank() })
        }
        settings.validate()
        require(habits.map { it.id }.distinct().size == habits.size)
        habits.forEach { it.validate() }
        require(habitLogs.map { it.habitId to it.date }.distinct().size == habitLogs.size)
        habitLogs.forEach { require(habits.any { h -> h.id == it.habitId } && it.count in 0..100000); LocalDate.parse(it.date) }
        require(focusSessions.map { it.id }.distinct().size == focusSessions.size)
        focusSessions.forEach { require(it.elapsedSeconds >= 0 && it.endedAt >= it.startedAt) }
        activeFocus?.let { require(it.elapsedMillis >= 0 && it.durationSeconds in 0..86400) }
        require(countdowns.map { it.id }.distinct().size == countdowns.size)
        countdowns.forEach { require(it.title.isNotBlank()); LocalDate.parse(it.date) }
        require(filters.map { it.id }.distinct().size == filters.size)
        filters.forEach { require(it.name.isNotBlank() && it.minimumPriority in 0..3) }
        require(templates.map { it.id }.distinct().size == templates.size)
        templates.forEach { require(it.name.isNotBlank() && it.task.title.isNotBlank()) }
        return this
    }
}

enum class View { ALL, INBOX, TODAY, UPCOMING, COMPLETED, TRASH, LIST, NOTES, CALENDAR, MATRIX, HABITS, FOCUS, COUNTDOWNS, SETTINGS, FILTER }
enum class Sort { DUE, PRIORITY, TITLE, CREATED }

data class TaskQuery(val view: View = View.TODAY, val listId: String? = null, val text: String = "", val sort: Sort = Sort.DUE, val filterId: String? = null)

fun Snapshot.query(query: TaskQuery, today: LocalDate = LocalDate.now()): List<TaskItem> {
    val items = tasks.filter { task ->
        val date = task.dueDate?.let(LocalDate::parse)
        val visible = when (query.view) {
            View.TRASH -> task.deletedAt != null
            View.COMPLETED -> task.deletedAt == null && task.completedAt != null
            else -> task.deletedAt == null && task.completedAt == null && when (query.view) {
                View.ALL -> true
                View.INBOX -> task.listId == INBOX
                View.TODAY -> date != null && date <= today
                View.UPCOMING -> date != null && date > today && date <= today.plusDays(7)
                View.LIST -> task.listId == query.listId
                View.NOTES -> task.kind == TaskKind.NOTE
                View.CALENDAR, View.MATRIX -> task.kind == TaskKind.TASK
                View.FILTER -> filters.find { it.id == query.filterId }?.matches(task, today) == true
                else -> false
            }
        }
        visible && (task.kind != TaskKind.NOTE || query.view in setOf(View.NOTES, View.LIST, View.TRASH, View.COMPLETED, View.FILTER)) &&
            (query.view in setOf(View.TRASH, View.COMPLETED) || lists.find { it.id == task.listId }?.archived != true || query.view == View.LIST) &&
            (query.text.isBlank() || listOf(task.title, task.notes, task.tags.joinToString(" ")).any { it.contains(query.text.trim(), ignoreCase = true) })
    }
    val comparator = when (query.sort) {
        Sort.DUE -> compareBy<TaskItem> { it.dueDate ?: "9999-12-31" }.thenByDescending { it.priority }
        Sort.PRIORITY -> compareByDescending<TaskItem> { it.priority }.thenBy { it.dueDate ?: "9999-12-31" }
        Sort.TITLE -> compareBy { it.title.lowercase() }
        Sort.CREATED -> compareByDescending { it.createdAt }
    }
    return items.sortedWith(comparator.thenBy { it.id })
}
