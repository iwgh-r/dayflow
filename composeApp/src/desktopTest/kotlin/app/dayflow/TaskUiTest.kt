package app.dayflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asSkiaBitmap
import app.dayflow.core.*
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class TaskUiTest {
    @get:Rule val compose = createComposeRule()
    private class MemoryStore : SnapshotStore {
        var value = Snapshot()
        override fun load() = value
        override fun save(snapshot: Snapshot) { value = snapshot }
    }

    @Test fun createEditCompleteTrashRestoreThroughRealControls() {
        val store = MemoryStore()
        val repo = runBlocking { TaskRepository.open(store) }
        compose.setContent { DayflowTheme(false) { DayflowApp(repo, false, {}) } }
        compose.onNode(hasSetTextAction() and hasText("할 일 추가")).performTextInput("UI 검증 작업")
        compose.onNodeWithContentDescription("할 일 추가").performClick()
        compose.waitUntil(5000) { repo.state.value.tasks.size == 1 }
        compose.onNodeWithText("UI 검증 작업").assertExists().performClick()
        compose.onNode(hasSetTextAction() and hasText("설명")).performTextInput("편집 결과 저장")
        compose.onNodeWithText("높음", useUnmergedTree = true).performClick()
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(5000) { repo.state.value.tasks.single().priority == 3 }
        assertEquals("편집 결과 저장", store.value.tasks.single().notes)
        compose.onNode(isToggleable()).performClick()
        compose.waitUntil(5000) { repo.state.value.tasks.single().completedAt != null }
        assertNotNull(store.value.tasks.single().completedAt)
        compose.onNodeWithText("완료", useUnmergedTree = true).performClick()
        compose.onNodeWithText("UI 검증 작업").assertExists()
        compose.onNodeWithContentDescription("휴지통으로 이동").performClick()
        compose.waitUntil(5000) { repo.state.value.tasks.single().deletedAt != null }
        compose.onNodeWithText("휴지통", useUnmergedTree = true).performClick()
        compose.onNodeWithContentDescription("복원").performClick()
        compose.waitUntil(8000) { repo.state.value.tasks.single().deletedAt == null }
        assertTrue(store.value.tasks.single().deletedAt == null)
    }

    @Test fun screenCaptureAndSearchAreRealData() {
        val repo = runBlocking {
            TaskRepository.open(MemoryStore()).apply {
                saveTask(TaskItem(title = "오늘의 계획 세우기", notes = "실제 테스트 전용 데이터", dueDate = java.time.LocalDate.now().toString(), priority = 3, tags = listOf("일상")))
                saveTask(TaskItem(title = "프로젝트 자료 정리", dueDate = java.time.LocalDate.now().toString(), priority = 1))
            }
        }
        compose.setContent { DayflowTheme(false) { DayflowApp(repo, false, {}) } }
        compose.onNodeWithText("오늘의 계획 세우기").assertExists()
        val path = File(System.getProperty("dayflow.evidence", "build/ui-evidence"), "tasks-desktop.png")
        path.parentFile.mkdirs()
        val image = compose.onRoot().captureToImage()
        val data = org.jetbrains.skia.Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData()!!
        path.writeBytes(data.bytes)
        compose.onNode(hasSetTextAction() and hasText("제목, 설명, 태그 검색")).performTextInput("자료")
        compose.onNodeWithText("오늘의 계획 세우기").assertDoesNotExist()
        compose.onNodeWithText("프로젝트 자료 정리").assertExists()
    }
}
