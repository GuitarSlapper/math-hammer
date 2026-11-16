package ru.mathhammer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.mathhammer.history.HistoryRepository
import ru.mathhammer.ui.MathHammerApp
import ru.mathhammer.ui.MathHammerTheme

/** Отдельный debug-only экран: UI-тесты никогда не меняют пользовательскую БД. */
object HistoryHarnessGraph { var repository: HistoryRepository? = null }

class HistoryHarnessActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = HistoryHarnessGraph.repository ?: run { finish(); return }
        val factory = viewModelFactory { initializer { CalculatorViewModel(repository, createSavedStateHandle()) } }
        setContent {
            MathHammerTheme {
                val model: CalculatorViewModel = viewModel(factory = factory)
                MathHammerApp(model, onFinish = { finish() })
            }
        }
    }
}
