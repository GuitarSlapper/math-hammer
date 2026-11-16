package ru.mathhammer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import ru.mathhammer.history.HistoryRepository

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class CalculatorViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val store = ViewModelStore()
    @Before fun setup() { Dispatchers.setMain(dispatcher) }
    @After fun teardown() { store.clear(); Dispatchers.resetMain() }
    private fun model(memory: MemoryHistoryStore, state: SavedStateHandle = SavedStateHandle()): CalculatorViewModel =
        CalculatorViewModel(HistoryRepository(memory, MutableClock(), dispatcher), state).also {
            store.put("editor${store.keys().size}", it)
        }

    @Test fun unsavedDraftNeverWritesHistoryAndSurvivesTabs() = runTest(dispatcher) {
        val memory = MemoryHistoryStore()
        val vm = model(memory)
        vm.saveAsNew(); vm.changeName("Example"); vm.submitName(); advanceUntilIdle()
        val id = requireNotNull(vm.editor.value.editingId)
        vm.changeDraft(vm.editor.value.draft.copy(attacks = "100"))
        vm.showHistory(true); vm.showHistory(false)
        assertEquals("100", vm.editor.value.draft.attacks)
        assertEquals(12, memory.findById(id)?.attacks)
        vm.requestNew()
        assertEquals("new", vm.editor.value.pendingAction)
        vm.cancelDiscard()
        assertEquals("100", vm.editor.value.draft.attacks)
        vm.saveChanges(); advanceUntilIdle()
        assertEquals(100, memory.findById(id)?.attacks)
        assertFalse(vm.editor.value.dirty)
    }

    @Test fun failedSaveRetainsDialogNameAndDoesNotReportSuccess() = runTest(dispatcher) {
        val memory = MemoryHistoryStore().apply { failWrites = true }
        val vm = model(memory)
        val events = mutableListOf<UiEvent>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.events.collect(events::add) }
        vm.saveAsNew(); vm.changeName("Не потерять имя"); vm.submitName(); advanceUntilIdle()
        assertEquals("Не потерять имя", vm.editor.value.dialog?.text)
        assertNotNull(vm.editor.value.dialog?.error)
        assertNull(vm.editor.value.editingId)
        assertTrue(events.none { it is UiEvent.Message && it.text == "Расчёт сохранён" })
    }

    @Test fun savedStateRestoresInvalidDraftDialogAndDiscardPrompt() = runTest(dispatcher) {
        val memory = MemoryHistoryStore()
        val handle = SavedStateHandle()
        val vm = model(memory, handle)
        vm.saveAsNew(); vm.changeName("Диалог")
        vm.changeDraft(vm.editor.value.draft.copy(ap = "-"))
        vm.requestNew()
        val restored = model(memory, SavedStateHandle(handle.keys().associateWith { handle.get<Any?>(it) }))
        assertEquals("-", restored.editor.value.draft.ap)
        assertEquals("Диалог", restored.editor.value.dialog?.text)
        assertEquals("new", restored.editor.value.pendingAction)
        restored.confirmDiscard()
        assertEquals(Draft(), restored.editor.value.draft)
    }

    @Test fun openingRestoresEveryFieldAndSaveAsNewDoesNotOverwrite() = runTest(dispatcher) {
        val memory = MemoryHistoryStore()
        val vm = model(memory)
        val draft = Draft("17", 6, "15", "-4", "3", "12", 2, 0)
        vm.changeDraft(draft); vm.saveAsNew(); vm.changeName("Исходный"); vm.submitName(); advanceUntilIdle()
        val id = requireNotNull(vm.editor.value.editingId)
        vm.requestNew(); vm.requestOpen(id); advanceUntilIdle()
        assertEquals(draft, vm.editor.value.draft)
        vm.changeDraft(draft.copy(attacks = "1"))
        val perAttack = vm.editor.value.result?.averageDamagePerAttack
        assertEquals(DamageCalculator.calculateDamage(requireNotNull(draft.inputOrNull())).averageDamagePerAttack, requireNotNull(perAttack), 1e-9)
        vm.saveAsNew(); vm.changeName("Копия"); vm.submitName(); advanceUntilIdle()
        assertNotEquals(id, vm.editor.value.editingId)
        assertEquals(17, memory.findById(id)?.attacks)
    }

    @Test fun olderSnapshotsAreRecomputedButOnlyReplacedOnExplicitSave() = runTest(dispatcher) {
        val memory = MemoryHistoryStore()
        val repository = HistoryRepository(memory, MutableClock(), dispatcher)
        val record = repository.saveNew("Старая версия", requireNotNull(Draft().inputOrNull()))
        val historical = record.copy(calculationVersion = 0, averageDamagePerAttack = 99.0, averageTotalDamage = 100.0)
        memory.update(historical)
        val vm = model(memory)
        vm.requestOpen(record.id); advanceUntilIdle()
        assertTrue(vm.editor.value.versionMismatch)
        assertEquals(4.0 / 9, requireNotNull(vm.editor.value.result).averageDamagePerAttack, 1e-9)
        assertEquals(99.0, requireNotNull(memory.findById(record.id)).averageDamagePerAttack, 1e-9)
        vm.saveChanges(); advanceUntilIdle()
        assertFalse(vm.editor.value.versionMismatch)
        assertEquals(4.0 / 9, requireNotNull(memory.findById(record.id)).averageDamagePerAttack, 1e-9)
    }
}
