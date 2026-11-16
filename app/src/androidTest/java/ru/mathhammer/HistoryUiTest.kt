package ru.mathhammer

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.mathhammer.history.*

@RunWith(AndroidJUnit4::class)
class HistoryUiTest {
    @get:Rule val compose = createEmptyComposeRule()
    private lateinit var database: HistoryDatabase
    private lateinit var scenario: ActivityScenario<HistoryHarnessActivity>

    @Before fun launch() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), HistoryDatabase::class.java).build()
        HistoryHarnessGraph.repository = HistoryRepository(RoomHistoryStore(database))
        scenario = ActivityScenario.launch(HistoryHarnessActivity::class.java)
    }
    @After fun close() { scenario.close(); database.close(); HistoryHarnessGraph.repository = null }

    private fun tag(value: String) = compose.onNodeWithTag(value)
    private fun scrollTo(value: String): SemanticsNodeInteraction {
        tag("calculator_list").performScrollToNode(hasTestTag(value))
        return tag(value)
    }
    private fun save(title: String) {
        scrollTo("save_new").performClick()
        tag("name_input").performTextReplacement(title)
        tag("dialog_save").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("name_input").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun emptyHistorySaveRenameOpenAndUndo() {
        tag("nav_history").performClick()
        compose.onNodeWithText("Сохранённых расчётов пока нет").assertIsDisplayed()
        tag("new_calculation").performClick()
        save("Accursed Weapon vs Interceptors")
        tag("nav_history").performClick()
        compose.onNodeWithText("Accursed Weapon vs Interceptors").assertIsDisplayed()
        compose.onNodeWithText("0,444 / атака").assertIsDisplayed()
        compose.onNodeWithText("5,33 всего").assertIsDisplayed()
        tag("more_1").performClick()
        compose.onNodeWithText("Переименовать").performClick()
        tag("name_input").performTextReplacement("Мой расчёт")
        tag("dialog_save").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Мой расчёт").fetchSemanticsNodes().isNotEmpty() }
        tag("history_entry_1").performClick()
        tag("field_A").assertTextEquals("12")
        tag("field_AP").assertTextEquals("-2")
        tag("nav_history").performClick()
        tag("more_1").performClick()
        compose.onNodeWithText("Удалить", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Отменить", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Отменить", useUnmergedTree = true).performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithTag("history_entry_1").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun rotationKeepsDraftAndDiscardRequiresConfirmation() {
        tag("field_A").performTextReplacement("25")
        compose.waitForIdle()
        tag("field_A").assertTextEquals("25")
        scenario.recreate()
        tag("field_A").assertTextEquals("25")
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
        compose.waitUntil(10000) {
            ApplicationProvider.getApplicationContext<Context>().resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        }
        tag("field_A").assertTextEquals("25")
        scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
        compose.waitUntil(10000) {
            ApplicationProvider.getApplicationContext<Context>().resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        }
        tag("field_A").assertTextEquals("25")
        tag("nav_history").performClick()
        tag("new_calculation").performClick()
        compose.onNodeWithText("Сбросить несохранённые изменения?").assertIsDisplayed()
        compose.onNodeWithText("Продолжить редактирование").performClick()
        tag("nav_calculator").performClick()
        tag("field_A").assertTextEquals("25")
        scrollTo("new_calculation").performClick()
        tag("discard_confirm").performClick()
        tag("field_A").assertTextEquals("12")
    }

    @Test fun invalidInputAndInvalidNameCannotBeSaved() {
        tag("field_AP").performScrollTo().performTextReplacement("-")
        scrollTo("save_new").assertIsNotEnabled()
        tag("field_AP").performScrollTo().performTextReplacement("-2")
        scrollTo("save_new").performClick()
        tag("dialog_save").assertIsNotEnabled()
        tag("name_input").performTextReplacement("я".repeat(81))
        tag("dialog_save").assertIsNotEnabled()
        scenario.recreate()
        tag("name_input").assertTextContains("я".repeat(81))
    }
}
