package app.dayflow

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import app.dayflow.core.JsonFileStore
import java.io.File

fun main(args: Array<String>) {
    val customDir = args.indexOf("--data-dir").takeIf { it >= 0 }?.let { args.getOrNull(it + 1) }
    val dataDir = customDir?.let(::File) ?: File(System.getenv("LOCALAPPDATA") ?: System.getProperty("user.home"), "Dayflow")
    application {
        Window(onCloseRequest = ::exitApplication, title = "Dayflow", state = rememberWindowState(width = 1180.dp, height = 800.dp)) {
            val store = remember { runCatching { JsonFileStore(File(dataDir, "dayflow.json")) } }
            store.getOrNull()?.let {
                DisposableEffect(it) { onDispose { it.close() } }
                LoadDayflow(it)
            } ?: androidx.compose.material3.Text(store.exceptionOrNull()?.message ?: "데이터를 열 수 없습니다")
        }
    }
}
