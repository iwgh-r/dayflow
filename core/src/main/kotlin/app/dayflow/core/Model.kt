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
)

@Serializable
data class CheckItem(val id: String = UUID.randomUUID().toString(), val title: String, val done: Boolean = false)

@Serializable
data class TaskList(val id: String = UUID.randomUUID().toString(), val name: String)

const val INBOX = "inbox"

@Serializable
data class Snapshot(
    val schemaVersion: Int = 1,
    val lists: List<TaskList> = listOf(TaskList(INBOX, "수집함")),
    val tasks: List<TaskItem> = emptyList(),
) {
    fun validate(): Snapshot {
        require(schemaVersion == 1) { "지원하지 않는 데이터 버전입니다. 원본은 보존됩니다." }
        require(lists.any { it.id == INBOX }) { "수집함이 없습니다." }
        require(lists.map { it.id }.distinct().size == lists.size) { "중복 목록 ID" }
        require(tasks.map { it.id }.distinct().size == tasks.size) { "중복 작업 ID" }
        lists.forEach { require(it.id.isNotBlank() && it.name.isNotBlank() && it.name.length <= 100) }
        tasks.forEach {
            require(it.id.isNotBlank() && it.title.isNotBlank() && it.title.length <= 1000)
            require(it.notes.length <= 100_000 && it.priority in 0..3)
            require(lists.any { list -> list.id == it.listId }) { "존재하지 않는 목록" }
            it.dueDate?.let(LocalDate::parse)
            require(it.checklist.map(CheckItem::id).distinct().size == it.checklist.size)
            require(it.checklist.all { item -> item.title.isNotBlank() })
        }
        return this
    }
}

enum class View { ALL, INBOX, TODAY, UPCOMING, COMPLETED, TRASH, LIST }
enum class Sort { DUE, PRIORITY, TITLE, CREATED }

data class TaskQuery(val view: View = View.TODAY, val listId: String? = null, val text: String = "", val sort: Sort = Sort.DUE)

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
                else -> false
            }
        }
        visible && (query.text.isBlank() || listOf(task.title, task.notes, task.tags.joinToString(" ")).any { it.contains(query.text.trim(), ignoreCase = true) })
    }
    val comparator = when (query.sort) {
        Sort.DUE -> compareBy<TaskItem> { it.dueDate ?: "9999-12-31" }.thenByDescending { it.priority }
        Sort.PRIORITY -> compareByDescending<TaskItem> { it.priority }.thenBy { it.dueDate ?: "9999-12-31" }
        Sort.TITLE -> compareBy { it.title.lowercase() }
        Sort.CREATED -> compareByDescending { it.createdAt }
    }
    return items.sortedWith(comparator.thenBy { it.id })
}
