package app.dayflow

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Runs against the actual activity and file-backed application repository. */
@RunWith(AndroidJUnit4::class)
class AndroidTaskTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun navigate(label: String) {
        compose.onNodeWithContentDescription("목록 열기").performClick()
        compose.onNodeWithText(label, useUnmergedTree = true).performClick()
    }

    @Test fun koreanTaskCrudAndRotationKeepRealData() {
        val title = "에뮬레이터 한글 저장 검증"
        compose.waitUntil(10_000) { compose.onAllNodesWithText("할 일 추가").fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(hasSetTextAction() and hasText("할 일 추가")).performTextInput(title)
        compose.onNodeWithContentDescription("할 일 추가").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(title).performClick()
        compose.onNode(hasSetTextAction() and hasText("설명")).performTextInput("회전과 재실행 뒤에도 남는 설명")
        compose.onNodeWithText("높음", useUnmergedTree = true).performClick()
        compose.onNodeWithText("저장").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("할 일 수정").fetchSemanticsNodes().isEmpty() }
        compose.activityRule.scenario.recreate()
        compose.waitUntil(5000) { compose.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        compose.onNode(isToggleable()).performClick()
        navigate("완료")
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithContentDescription("휴지통으로 이동").performClick()
        navigate("휴지통")
        compose.onNodeWithText(title).assertExists()
        compose.onNodeWithContentDescription("복원").performClick()
        navigate("완료")
        compose.onNodeWithText(title).assertExists()
        compose.onNode(isToggleable()).performClick()
        navigate("오늘")
        compose.onNodeWithText(title).assertExists()
    }
}
