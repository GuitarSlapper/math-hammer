package ru.mathhammer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import ru.mathhammer.history.*

data class Draft(
    val attacks: String = "12", val hit: Int = 3, val strength: String = "8",
    val ap: String = "-2", val damage: String = "2", val toughness: String = "5",
    val save: Int = 3, val inv: Int = 4
) {
    fun inputOrNull(): DamageInput? {
        val a = parseInteger(attacks, 0..9999) ?: return null
        val s = parseInteger(strength, 1..999) ?: return null
        val penetration = parseInteger(ap, -99..0) ?: return null
        val d = parseInteger(damage, 1..999) ?: return null
        val t = parseInteger(toughness, 1..999) ?: return null
        if (hit !in 2..6 || save !in 2..6 || (inv != 0 && inv !in 2..6)) return null
        return DamageInput(a, hit, s, penetration, d, t, save, inv.takeIf { it != 0 })
    }
    fun values() = arrayListOf(attacks, hit.toString(), strength, ap, damage, toughness, save.toString(), inv.toString())
    companion object {
        fun from(input: DamageInput) = Draft(input.attacks.toString(), input.hitTarget, input.strength.toString(),
            input.armorPenetration.toString(), input.damage.toString(), input.toughness.toString(),
            input.saveTarget, input.invulnerableSaveTarget ?: 0)
        fun restore(values: List<String>?) = if (values?.size == 8) Draft(values[0], values[1].toIntOrNull() ?: 3,
            values[2], values[3], values[4], values[5], values[6].toIntOrNull() ?: 3, values[7].toIntOrNull() ?: 0) else Draft()
    }
}

data class NameDialog(val mode: String, val text: String = "", val id: Long? = null, val error: String? = null)
data class EditorState(
    val draft: Draft = Draft(), val baseline: Draft = Draft(), val editingId: Long? = null,
    val title: String? = null, val versionMismatch: Boolean = false,
    val historyVisible: Boolean = false, val dialog: NameDialog? = null,
    val busy: Boolean = false, val pendingAction: String? = null, val pendingId: Long? = null,
    val draftGeneration: Int = 0
) {
    val dirty get() = draft != baseline
    val input get() = draft.inputOrNull()
    val result get() = input?.let(DamageCalculator::calculateDamage)
}
data class HistoryState(val entries: List<SavedCalculation> = emptyList(), val loading: Boolean = true, val error: Boolean = false)
sealed interface UiEvent {
    data class Message(val text: String, val deleted: SavedCalculation? = null) : UiEvent
    data object Finish : UiEvent
}

class CalculatorViewModel(private val repository: HistoryRepository, private val saved: SavedStateHandle) : ViewModel() {
    private val mutableEditor = MutableStateFlow(EditorState(
        draft = Draft.restore(saved["draft"]), baseline = Draft.restore(saved["baseline"]),
        editingId = saved["editingId"], title = saved["title"], versionMismatch = saved["versionMismatch"] ?: false,
        historyVisible = saved["historyVisible"] ?: false,
        dialog = (saved.get<String>("dialogMode"))?.let { NameDialog(it, saved["dialogText"] ?: "", saved["dialogId"]) },
        pendingAction = saved["pendingAction"], pendingId = saved["pendingId"], draftGeneration = saved["draftGeneration"] ?: 0
    ))
    val editor: StateFlow<EditorState> = mutableEditor.asStateFlow()
    private val messages = Channel<UiEvent>(Channel.BUFFERED)
    val events = messages.receiveAsFlow()
    private val refresh = MutableStateFlow(0)
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val history: StateFlow<HistoryState> = refresh.flatMapLatest {
        repository.observeAll().map { HistoryState(it, loading = false) }
            .onStart { emit(HistoryState()) }
            .catch { emit(HistoryState(loading = false, error = true)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())

    private fun update(transform: (EditorState) -> EditorState) {
        val next = transform(mutableEditor.value)
        mutableEditor.value = next
        saved["draft"] = next.draft.values(); saved["baseline"] = next.baseline.values()
        saved["editingId"] = next.editingId; saved["title"] = next.title
        saved["versionMismatch"] = next.versionMismatch; saved["historyVisible"] = next.historyVisible
        saved["dialogMode"] = next.dialog?.mode; saved["dialogText"] = next.dialog?.text; saved["dialogId"] = next.dialog?.id
        saved["pendingAction"] = next.pendingAction; saved["pendingId"] = next.pendingId
        saved["draftGeneration"] = next.draftGeneration
    }

    fun changeDraft(draft: Draft) { if (!editor.value.busy) update { it.copy(draft = draft) } }
    fun showHistory(show: Boolean) = update { it.copy(historyVisible = show) }
    fun retryHistory() { refresh.value++ }
    fun saveAsNew() { if (editor.value.input != null && !editor.value.busy) update { it.copy(dialog = NameDialog("save", it.title ?: "")) } }
    fun rename(value: SavedCalculation) { if (!editor.value.busy) update { it.copy(dialog = NameDialog("rename", value.title, value.id)) } }
    fun changeName(text: String) = update { state -> state.copy(dialog = state.dialog?.copy(text = text, error = null)) }
    fun closeDialog() { if (!editor.value.busy) update { it.copy(dialog = null) } }

    fun submitName() {
        val state = editor.value
        val dialog = state.dialog ?: return
        try { validatedTitle(dialog.text) } catch (e: IllegalArgumentException) {
            update { it.copy(dialog = dialog.copy(error = e.message)) }; return
        }
        val input = state.input
        if (dialog.mode == "save" && input == null) return
        operation(dialogError = true) {
            val value = if (dialog.mode == "save") repository.saveNew(dialog.text, requireNotNull(input))
                else repository.rename(requireNotNull(dialog.id), dialog.text)
            if (dialog.mode == "save") bind(value)
            else if (editor.value.editingId == value.id) update { it.copy(title = value.title) }
            update { it.copy(dialog = null) }
            messages.send(UiEvent.Message(if (dialog.mode == "save") "Расчёт сохранён" else "Название изменено"))
        }
    }

    fun saveChanges() {
        val state = editor.value
        val id = state.editingId ?: return
        val input = state.input ?: return
        operation {
            bind(repository.saveChanges(id, input))
            messages.send(UiEvent.Message("Изменения сохранены"))
        }
    }

    fun revertChanges() { val id = editor.value.editingId ?: return; operation { bind(repository.findById(id)) } }
    fun requestNew() = request("new")
    fun requestOpen(id: Long) = request("open", id)
    fun requestExit() = request("exit")
    private fun request(action: String, id: Long? = null) {
        if (editor.value.busy) return
        if (editor.value.dirty) update { it.copy(pendingAction = action, pendingId = id) }
        else perform(action, id)
    }
    fun cancelDiscard() = update { it.copy(pendingAction = null, pendingId = null) }
    fun confirmDiscard() {
        val action = editor.value.pendingAction ?: return
        val id = editor.value.pendingId
        cancelDiscard(); perform(action, id)
    }
    private fun perform(action: String, id: Long?) {
        when (action) {
            "new" -> update { EditorState(busy = it.busy, draftGeneration = it.draftGeneration + 1) }
            "open" -> operation { bind(repository.findById(requireNotNull(id))) }
            "exit" -> viewModelScope.launch { messages.send(UiEvent.Finish) }
        }
    }
    private fun bind(value: SavedCalculation) {
        DamageCalculator.calculateDamage(value.input())
        val draft = Draft.from(value.input())
        update { it.copy(draft = draft, baseline = draft, editingId = value.id, title = value.title,
            versionMismatch = value.calculationVersion != CALCULATION_VERSION, historyVisible = false,
            draftGeneration = it.draftGeneration + 1) }
    }

    fun delete(id: Long) = operation {
        val deleted = repository.delete(id)
        if (editor.value.editingId == id) update { it.copy(editingId = null, title = null, baseline = Draft(), versionMismatch = false) }
        messages.send(UiEvent.Message("Расчёт удалён", deleted))
    }
    fun undoDelete(value: SavedCalculation) = operation {
        repository.restoreDeleted(value)
        messages.send(UiEvent.Message("Расчёт восстановлен"))
    }

    private fun operation(dialogError: Boolean = false, block: suspend () -> Unit) {
        if (editor.value.busy) return
        update { it.copy(busy = true) }
        viewModelScope.launch {
            try { block() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = if (e is IllegalArgumentException) e.message ?: "Проверьте значения"
                    else "Не удалось выполнить операцию с историей. Попробуйте ещё раз."
                if (dialogError) update { it.copy(dialog = it.dialog?.copy(error = message)) }
                else messages.send(UiEvent.Message(message))
            } finally { update { it.copy(busy = false) } }
        }
    }
}
