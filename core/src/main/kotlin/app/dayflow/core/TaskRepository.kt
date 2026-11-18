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

    private suspend fun change(block: (Snapshot) -> Snapshot) = mutex.withLock {
        val next = block(mutable.value).validate()
        withContext(Dispatchers.IO) { store.save(next) }
        mutable.value = next
    }

    suspend fun saveTask(task: TaskItem) = change { state ->
        val normalized = task.copy(title = task.title.trim(), tags = task.tags.map { it.trim().removePrefix("#") }.filter { it.isNotBlank() }.distinct(), updatedAt = System.currentTimeMillis())
        val old = state.tasks.find { it.id == task.id }
        val saved = if (old == null) normalized else normalized.copy(createdAt = old.createdAt, completedAt = old.completedAt, deletedAt = old.deletedAt)
        state.copy(tasks = state.tasks.filterNot { it.id == saved.id } + saved)
    }

    private suspend fun update(id: String, block: (TaskItem) -> TaskItem) = change { state ->
        require(state.tasks.any { it.id == id }) { "작업을 찾을 수 없습니다." }
        state.copy(tasks = state.tasks.map { if (it.id == id) block(it).copy(updatedAt = System.currentTimeMillis()) else it })
    }

    suspend fun toggleComplete(id: String) = update(id) {
        require(it.deletedAt == null) { "휴지통에서 먼저 복원하세요." }
        it.copy(completedAt = if (it.completedAt == null) System.currentTimeMillis() else null)
    }
    suspend fun trash(id: String) = update(id) { it.copy(deletedAt = System.currentTimeMillis()) }
    suspend fun restore(id: String) = update(id) { it.copy(deletedAt = null) }
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
