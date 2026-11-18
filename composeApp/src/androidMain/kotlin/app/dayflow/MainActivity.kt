package app.dayflow

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.dayflow.core.JsonFileStore
import java.io.File

class DayflowApplication : Application() {
    val store by lazy { JsonFileStore(File(filesDir, "dayflow.json")) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { LoadDayflow((application as DayflowApplication).store) }
    }
}
