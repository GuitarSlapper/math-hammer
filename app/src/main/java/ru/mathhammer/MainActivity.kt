package ru.mathhammer

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.mathhammer.history.HistoryDatabase
import ru.mathhammer.history.HistoryRepository
import ru.mathhammer.history.RoomHistoryStore
import ru.mathhammer.ui.MathHammerApp
import ru.mathhammer.ui.MathHammerTheme

class MathHammerApplication : Application() {
    private val database by lazy { HistoryDatabase.open(this) }
    val repository by lazy { HistoryRepository(RoomHistoryStore(database)) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(SystemBarStyle.dark(0xFF111820.toInt()), SystemBarStyle.dark(0xFF111820.toInt()))
        val factory = viewModelFactory {
            initializer { CalculatorViewModel((application as MathHammerApplication).repository, createSavedStateHandle()) }
        }
        setContent {
            MathHammerTheme {
                val model: CalculatorViewModel = viewModel(factory = factory)
                MathHammerApp(model, onFinish = { finish() })
            }
        }
    }
}