package app.dayflow.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface SnapshotStore {
    fun load(): Snapshot
    fun save(snapshot: Snapshot)
}

class TaskRepository private constructor(private val store: SnapshotStore, initial: Snapshot) {
    private val mutex = Mutex()
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<Snapshot> = mutable.asStateFlow()

    companion object {
        suspend fun open(store: SnapshotStore): TaskRepository = withContext(Dispatchers.IO) {
            TaskRepository(store, store.load().validate())
        }
    }

    internal suspend fun change(block: (Snapshot) -> Snapshot) = mutex.withLock {
        val next = block(mutable.value).validate()
        withContext(Dispatchers.IO) { store.save(next) }
        mutable.value = next
    }

    suspend fun saveTask(task: TaskItem) = change { state ->
        val old = state.tasks.find { it.id == task.id }
        val normalized = task.copy(title = task.title.trim(), tags = task.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct(), updatedAt = nextUpdatedAt(old))
        val saved = if (old == null) normalized else normalized.copy(createdAt = old.createdAt, completedAt = old.completedAt, deletedAt = old.deletedAt)
        val descendants = state.descendants(saved.id)
        val moved = state.tasks.filterNot { it.id == saved.id }.map {
            if (it.id in descendants && old?.listId != saved.listId) it.copy(listId = saved.listId, sectionId = null, updatedAt = saved.updatedAt) else it
        }
        state.copy(tasks = moved + saved)
    }

    private suspend fun update(id: String, block: (TaskItem) -> TaskItem) = change { state ->
        require(state.tasks.any { it.id == id }) { "작업을 찾을 수 없습니다." }
        state.copy(tasks = state.tasks.map { if (it.id == id) block(it).copy(updatedAt = nextUpdatedAt(it)) else it })
    }

    /** Edits must always advance updatedAt, even within the same millisecond, so "unchanged" checks and backup merges see them. */
    private fun nextUpdatedAt(old: TaskItem?): Long = maxOf(System.currentTimeMillis(), (old?.updatedAt ?: Long.MIN_VALUE) + 1)

    suspend fun toggleComplete(id: String) = change { state ->
        val task = state.tasks.find { it.id == id } ?: error("작업을 찾을 수 없습니다.")
        require(task.deletedAt == null && task.kind == TaskKind.TASK) { "이 항목은 완료할 수 없습니다." }
        val now = System.currentTimeMillis()
        var tasks = state.tasks
        if (task.completedAt == null) {
            require(state.tasks.none { it.id in state.descendants(id) && it.completedAt == null && it.deletedAt == null }) { "하위 작업을 먼저 완료하세요." }
            val nextDate = task.recurrence?.next(java.time.LocalDate.parse(task.dueDate), task.occurrence)
            if (nextDate != null) {
                val source = state.tasks.filter { it.id == id || (it.id in state.descendants(id) && it.deletedAt == null) }
                val ids = source.associate { it.id to java.util.UUID.randomUUID().toString() }
                tasks = tasks + source.map { item -> item.copy(id = ids.getValue(item.id), parentId = item.parentId?.let { ids[it] }, dueDate = if (item.id == id) nextDate.toString() else item.dueDate?.let { java.time.LocalDate.parse(it).plusDays(java.time.temporal.ChronoUnit.DAYS.between(java.time.LocalDate.parse(task.dueDate), nextDate)).toString() }, checklist = item.checklist.map { it.copy(done = false) }, completedAt = null, createdAt = now, updatedAt = now, seriesId = task.seriesId ?: task.id, generatedFrom = item.id, occurrence = item.occurrence + 1) }
            }
        } else {
            val next = tasks.find { it.generatedFrom == id }
            if (next != null) {
                val generatedIds = state.descendants(next.id) + next.id
                require(tasks.filter { it.id in generatedIds }.all { it.completedAt == null && it.deletedAt == null && it.updatedAt == it.createdAt }) { "다음 반복 작업이 변경되어 완료를 취소할 수 없습니다." }
                tasks = tasks.filterNot { it.id in generatedIds }
            }
        }
        state.copy(tasks = tasks.map { if (it.id == id) it.copy(completedAt = if (task.completedAt == null) now else null, updatedAt = now) else it })
    }
    suspend fun trash(id: String) = change { state ->
        require(state.tasks.any { it.id == id && it.deletedAt == null })
        val ids = state.descendants(id) + id
        val now = System.currentTimeMillis()
        state.copy(tasks = state.tasks.map { if (it.id in ids && it.deletedAt == null) it.copy(deletedAt = now, deletedWithParent = id, updatedAt = now) else it })
    }
    suspend fun restore(id: String) = change { state ->
        val item = state.tasks.find { it.id == id } ?: error("작업을 찾을 수 없습니다.")
        require(item.parentId == null || state.tasks.find { it.id == item.parentId }?.deletedAt == null) { "상위 작업을 먼저 복원하세요." }
        state.copy(tasks = state.tasks.map { if (it.id == id || it.deletedWithParent == id) it.copy(deletedAt = null, deletedWithParent = null, updatedAt = System.currentTimeMillis()) else it })
    }
    suspend fun toggleCheck(taskId: String, checkId: String) = update(taskId) { task ->
        require(task.checklist.any { it.id == checkId })
        task.copy(checklist = task.checklist.map { if (it.id == checkId) it.copy(done = !it.done) else it })
    }
    suspend fun addList(name: String) = change { state ->
        val trimmed = name.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= 100) { "목록 이름은 1~100자로 입력하세요." }
        require(state.lists.none { it.name.equals(trimmed, ignoreCase = true) }) { "같은 이름의 목록이 있습니다." }
        state.copy(lists = state.lists + TaskList(name = trimmed))
    }
}
