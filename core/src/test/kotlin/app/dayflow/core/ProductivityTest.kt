package app.dayflow.core

import kotlinx.coroutines.runBlocking
import org.junit.Test
import java.time.*
import kotlin.test.*

class ProductivityTest {
    private class Store(var value: Snapshot = Snapshot()) : SnapshotStore { override fun load() = value; override fun save(snapshot: Snapshot) { value = snapshot } }
    @Test fun versionOneMigrationRetainsIdsAndChecklist() {
        val old = """{"schemaVersion":1,"lists":[{"id":"inbox","name":"수집함"}],"tasks":[{"id":"original","title":"한글","checklist":[{"id":"check","title":"항목","done":true}]}]}"""
        val migrated = AppCodec.decode(old)
        assertEquals(2, migrated.schemaVersion)
        assertEquals("original", migrated.tasks.single().id)
        assertTrue(migrated.tasks.single().checklist.single().done)
        assertEquals(migrated, AppCodec.decode(AppCodec.encode(migrated)))
    }
    @Test fun monthlyAndYearlyRecurrencePreserveAnchor() {
        val rule = Recurrence(RepeatFrequency.MONTHLY, anchorDate = "2024-01-31")
        val feb = rule.next(LocalDate.parse("2024-01-31"), 1)!!
        assertEquals("2024-02-29", feb.toString())
        assertEquals("2024-03-31", rule.next(feb, 2).toString())
        val leap = Recurrence(RepeatFrequency.YEARLY, anchorDate = "2024-02-29")
        assertEquals("2028-02-29", leap.next(LocalDate.parse("2027-02-28"), 4).toString())
        assertNull(rule.copy(count = 1).next(feb, 1))
    }
    @Test fun weeklyRuleAndDstReminderUseCalendarTime() {
        val rule = Recurrence(RepeatFrequency.WEEKLY, interval = 2, weekdays = setOf(1, 5), anchorDate = "2026-11-16")
        assertEquals("2026-11-20", rule.next(LocalDate.parse("2026-11-16"), 1).toString())
        assertEquals("2026-11-30", rule.next(LocalDate.parse("2026-11-20"), 2).toString())
        val task = TaskItem(title = "DST", dueDate = "2026-03-08", dueTime = "02:30", zoneId = "America/New_York", reminderMinutes = listOf(0, 10))
        assertEquals(Instant.parse("2026-03-08T07:30:00Z"), task.reminderInstants().last())
        assertEquals(2, task.reminderInstants().size)
    }
    @Test fun recurringCompletionCreatesOneNextAndUndoDoesNotDuplicate() = runBlocking {
        val repo = TaskRepository.open(Store())
        val task = TaskItem(title = "매일", dueDate = "2026-11-18", recurrence = Recurrence(RepeatFrequency.DAILY, anchorDate = "2026-11-18"))
        repo.saveTask(task); repo.toggleComplete(task.id)
        assertEquals(2, repo.state.value.tasks.size)
        assertEquals("2026-11-19", repo.state.value.tasks.find { it.id != task.id }!!.dueDate)
        repo.toggleComplete(task.id)
        assertEquals(1, repo.state.value.tasks.size)
        repo.toggleComplete(task.id)
        val next = repo.state.value.tasks.find { it.id != task.id }!!
        repo.saveTask(next.copy(notes = "사용자가 수정"))
        assertFails { repo.toggleComplete(task.id) }
        assertEquals(2, repo.state.value.tasks.size)
    }
    @Test fun parentCycleRejectedAndTrashRestoresOnlyItsOwnDescendants() = runBlocking<Unit> {
        val repo = TaskRepository.open(Store())
        val parent = TaskItem(title = "상위")
        val child = TaskItem(title = "하위", parentId = parent.id)
        repo.saveTask(parent); repo.saveTask(child)
        assertFails { repo.saveTask(parent.copy(parentId = child.id)) }
        assertFails { repo.toggleComplete(parent.id) }
        repo.trash(child.id); repo.trash(parent.id); repo.restore(parent.id)
        assertNotNull(repo.state.value.tasks.find { it.id == child.id }!!.deletedAt)
    }
    @Test fun backupMergeKeepsNewerTaskAndRejectsTampering() = runBlocking {
        val old = TaskItem(title = "기존", updatedAt = 1)
        val incoming = Snapshot(tasks = listOf(old, TaskItem(title = "새 작업")))
        val backup = AppCodec.export(incoming)
        val repo = TaskRepository.open(Store(Snapshot(tasks = listOf(old.copy(title = "최근 변경", updatedAt = 2)))))
        repo.importBackup(backup)
        assertEquals(2, repo.state.value.tasks.size)
        assertEquals("최근 변경", repo.state.value.tasks.find { it.id == old.id }!!.title)
        assertFails { AppCodec.import(backup.replace("sha256", "broken")) }
        assertEquals(2, repo.state.value.tasks.size)
    }
    @Test fun pausedFocusDoesNotAccumulateAndCompletionIsBounded() = runBlocking {
        val f = ActiveFocus(startedAt = 1000, runningSince = null, elapsedMillis = 60000, durationSeconds = 1500)
        val repo = TaskRepository.open(Store(Snapshot(activeFocus = f)))
        assertEquals(60000, f.elapsed(100000))
        repo.pauseFocus(100000)
        repo.finishFocus(2000000)
        assertEquals(1500, repo.state.value.focusSessions.single().elapsedSeconds)
        assertTrue(repo.state.value.focusSessions.single().completed)
    }
    @Test fun streakSkipsUnscheduledDaysAndCountsRealLogs() {
        val h = Habit(title = "평일", weekdays = setOf(1,2,3,4,5), createdDate = "2026-11-13")
        val s = Snapshot(habits = listOf(h), habitLogs = listOf(HabitLog(h.id, "2026-11-13", 1), HabitLog(h.id, "2026-11-16", 1)))
        assertEquals(2, s.habitStreak(h, LocalDate.parse("2026-11-16")))
        assertEquals(0, s.habitCount(h.id, LocalDate.parse("2026-11-15")))
    }
}
