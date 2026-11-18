package app.dayflow.core

import java.time.LocalDate

fun Snapshot.descendants(id: String): Set<String> {
    val result = mutableSetOf<String>()
    var current = setOf(id)
    while (current.isNotEmpty()) { current = tasks.filter { it.parentId in current && it.id !in result }.map { it.id }.toSet(); result += current }
    return result
}

suspend fun TaskRepository.saveSection(section: TaskSection) = change { s ->
    require(s.lists.any { it.id == section.listId })
    s.copy(sections = s.sections.filterNot { it.id == section.id } + section.copy(name = section.name.trim()))
}
suspend fun TaskRepository.removeSection(id: String) = change { s -> s.copy(sections = s.sections.filterNot { it.id == id }, tasks = s.tasks.map { if (it.sectionId == id) it.copy(sectionId = null, updatedAt = System.currentTimeMillis()) else it }) }
suspend fun TaskRepository.moveTask(id: String, sectionId: String?, order: Long) = change { s -> s.copy(tasks = s.tasks.map { if (it.id == id) it.copy(sectionId = sectionId, order = order, updatedAt = System.currentTimeMillis()) else it }) }
suspend fun TaskRepository.saveList(list: TaskList) = change { s ->
    require(list.id != INBOX || !list.archived)
    require(s.lists.none { it.id != list.id && it.name.equals(list.name.trim(), true) }) { "같은 이름의 목록이 있습니다." }
    s.copy(lists = s.lists.filterNot { it.id == list.id } + list.copy(name = list.name.trim()))
}
suspend fun TaskRepository.saveSettings(settings: AppSettings) = change { it.copy(settings = settings) }
suspend fun TaskRepository.saveHabit(habit: Habit) = change { s -> s.copy(habits = s.habits.filterNot { it.id == habit.id } + habit.copy(title = habit.title.trim())) }
suspend fun TaskRepository.logHabit(id: String, date: LocalDate, count: Int) = change { s ->
    val habit = s.habits.find { it.id == id } ?: error("습관이 없습니다.")
    require(date >= LocalDate.parse(habit.createdDate) && date <= LocalDate.now()) { "습관 기록 날짜를 확인하세요." }
    s.copy(habitLogs = s.habitLogs.filterNot { it.habitId == id && it.date == date.toString() } + HabitLog(id, date.toString(), count))
}
suspend fun TaskRepository.startFocus(taskId: String? = null, mode: FocusMode = FocusMode.POMODORO) = change { s ->
    require(s.activeFocus == null) { "진행 중인 타이머를 먼저 종료하세요." }
    require(taskId == null || s.tasks.any { it.id == taskId && it.deletedAt == null })
    s.copy(activeFocus = ActiveFocus(taskId = taskId, mode = mode, durationSeconds = when (mode) { FocusMode.POMODORO -> s.settings.focusMinutes * 60; FocusMode.BREAK -> s.settings.breakMinutes * 60; FocusMode.STOPWATCH -> 0 }))
}
suspend fun TaskRepository.pauseFocus(now: Long = System.currentTimeMillis()) = change { s ->
    val f = s.activeFocus ?: error("타이머가 없습니다.")
    s.copy(activeFocus = if (f.runningSince != null) f.copy(elapsedMillis = f.elapsed(now), runningSince = null) else f.copy(runningSince = now))
}
suspend fun TaskRepository.finishFocus(now: Long = System.currentTimeMillis()) = change { s ->
    val f = s.activeFocus ?: error("타이머가 없습니다.")
    val seconds = f.elapsed(now) / 1000
    val elapsed = if (f.durationSeconds > 0) minOf(seconds, f.durationSeconds.toLong()) else seconds
    s.copy(activeFocus = null, focusSessions = s.focusSessions + FocusSession(f.id, f.taskId, f.mode, f.startedAt, maxOf(now, f.startedAt), elapsed, f.durationSeconds > 0 && seconds >= f.durationSeconds))
}
suspend fun TaskRepository.saveCountdown(value: Countdown) = change { it.copy(countdowns = it.countdowns.filterNot { old -> old.id == value.id } + value) }
suspend fun TaskRepository.removeCountdown(id: String) = change { it.copy(countdowns = it.countdowns.filterNot { old -> old.id == id }) }
suspend fun TaskRepository.saveFilter(value: SavedFilter) = change { it.copy(filters = it.filters.filterNot { old -> old.id == value.id } + value) }
suspend fun TaskRepository.removeFilter(id: String) = change { it.copy(filters = it.filters.filterNot { old -> old.id == id }) }
suspend fun TaskRepository.saveTemplate(value: TaskTemplate) = change { it.copy(templates = it.templates.filterNot { old -> old.id == value.id } + value) }
suspend fun TaskRepository.applyTemplate(id: String, listId: String = INBOX) {
    val t = state.value.templates.find { it.id == id } ?: error("템플릿이 없습니다.")
    saveTask(t.task.copy(id = java.util.UUID.randomUUID().toString(), listId = listId, sectionId = null, parentId = null, completedAt = null, deletedAt = null, generatedFrom = null, seriesId = null, createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis()))
}
suspend fun TaskRepository.importBackup(text: String) {
    val incoming = AppCodec.import(text)
    change { current ->
        fun <T> union(a: List<T>, b: List<T>, key: (T) -> String): List<T> = (b + a).associateBy(key).values.toList()
        current.copy(
            lists = union(current.lists, incoming.lists) { it.id },
            sections = union(current.sections, incoming.sections) { it.id },
            tasks = (current.tasks + incoming.tasks).groupBy { it.id }.values.map { it.maxBy { task -> task.updatedAt } },
            habits = union(current.habits, incoming.habits) { it.id },
            habitLogs = (current.habitLogs + incoming.habitLogs).groupBy { it.habitId to it.date }.values.map { it.maxBy { log -> log.count } },
            focusSessions = union(current.focusSessions, incoming.focusSessions) { it.id },
            countdowns = union(current.countdowns, incoming.countdowns) { it.id },
            templates = union(current.templates, incoming.templates) { it.id },
            filters = union(current.filters, incoming.filters) { it.id },
        )
    }
}
