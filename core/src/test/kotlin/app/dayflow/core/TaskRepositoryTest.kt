package app.dayflow.core

import java.io.File
import java.nio.file.Files
import java.time.LocalDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlin.test.*
import org.junit.Test

class TaskRepositoryTest {
    private class MemoryStore(var snapshot: Snapshot = Snapshot()) : SnapshotStore {
        var fail = false
        override fun load() = snapshot
        override fun save(snapshot: Snapshot) { if (fail) error("disk full"); this.snapshot = snapshot }
    }

    @Test fun lifecycleSurvivesRestartAndTrashRestore() = runBlocking {
        val dir = Files.createTempDirectory("dayflow-test").toFile()
        try {
            val file = File(dir, "tasks.json")
            JsonFileStore(file).use { store ->
                val repo = TaskRepository.open(store)
                val task = TaskItem(title = "장보기", dueDate = "2026-11-18", tags = listOf(" #생활 ", "생활"))
                repo.saveTask(task)
                repo.saveTask(repo.state.value.tasks.single().copy(notes = "우유", priority = 3))
                repo.toggleComplete(task.id)
                repo.trash(task.id)
            }
            JsonFileStore(file).use { store ->
                val repo = TaskRepository.open(store)
                val task = repo.state.value.tasks.single()
                assertEquals("우유", task.notes)
                assertEquals(listOf("생활"), task.tags)
                assertTrue(repo.state.value.query(TaskQuery(View.COMPLETED)).isEmpty())
                assertEquals(1, repo.state.value.query(TaskQuery(View.TRASH)).size)
                repo.restore(task.id)
                assertEquals(1, repo.state.value.query(TaskQuery(View.COMPLETED)).size)
                repo.toggleComplete(task.id)
                assertEquals(1, repo.state.value.query(TaskQuery(View.ALL)).size)
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun saveFailureDoesNotPublishUncommittedState() = runBlocking {
        val store = MemoryStore()
        val repo = TaskRepository.open(store)
        store.fail = true
        assertFailsWith<IllegalStateException> { repo.saveTask(TaskItem(title = "실패")) }
        assertTrue(repo.state.value.tasks.isEmpty())
    }

    @Test fun concurrentSavesDoNotLoseTasks() = runBlocking {
        val repo = TaskRepository.open(MemoryStore())
        coroutineScope { (1..100).map { async { repo.saveTask(TaskItem(title = "작업 $it")) } }.awaitAll() }
        assertEquals(100, repo.state.value.tasks.size)
    }

    @Test fun todayAndUpcomingRespectDateBoundaries() {
        val today = LocalDate.parse("2026-11-18")
        val snapshot = Snapshot(tasks = listOf(
            TaskItem(title = "지남", dueDate = "2026-11-17"),
            TaskItem(title = "오늘", dueDate = "2026-11-18"),
            TaskItem(title = "내일", dueDate = "2026-11-19"),
            TaskItem(title = "마지막", dueDate = "2026-11-25"),
            TaskItem(title = "나중", dueDate = "2026-11-26"),
            TaskItem(title = "날짜 없음"),
        ))
        assertEquals(listOf("지남", "오늘"), snapshot.query(TaskQuery(View.TODAY), today).map { it.title })
        assertEquals(listOf("내일", "마지막"), snapshot.query(TaskQuery(View.UPCOMING), today).map { it.title })
    }

    @Test fun invalidDateAndBlankTitleRejectedWithoutMutation() = runBlocking {
        val repo = TaskRepository.open(MemoryStore())
        assertFails { repo.saveTask(TaskItem(title = " ")) }
        assertFails { repo.saveTask(TaskItem(title = "날짜 오류", dueDate = "2026-02-30")) }
        assertTrue(repo.state.value.tasks.isEmpty())
    }

    @Test fun checklistAndSearchUsePersistedData() = runBlocking {
        val repo = TaskRepository.open(MemoryStore())
        val task = TaskItem(title = "보고서", notes = "Meeting", tags = listOf("업무"), checklist = listOf(CheckItem(title = "검토")))
        repo.saveTask(task)
        repo.toggleCheck(task.id, task.checklist.single().id)
        assertTrue(repo.state.value.tasks.single().checklist.single().done)
        for (term in listOf("meeting", "업무", "보고")) assertEquals(1, repo.state.value.query(TaskQuery(View.ALL, text = term)).size)
    }

    @Test fun corruptFileIsPreservedAndSecondWriterRejected() {
        val dir = Files.createTempDirectory("dayflow-corrupt").toFile()
        try {
            val file = File(dir, "tasks.json").apply { writeText("broken") }
            JsonFileStore(file).use { store ->
                assertFails { store.load() }
                assertEquals("broken", file.readText())
                assertFails { JsonFileStore(file) }
            }
        } finally { dir.deleteRecursively() }
    }

    @Test fun futureSchemaNeverLoadsAsEmptyData() {
        assertFailsWith<IllegalArgumentException> { Snapshot(schemaVersion = 3).validate() }
    }
}
