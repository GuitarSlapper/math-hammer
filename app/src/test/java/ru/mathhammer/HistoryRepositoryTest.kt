package ru.mathhammer

import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test
import ru.mathhammer.history.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MemoryHistoryStore : HistoryStore {
    private val data = MutableStateFlow<List<SavedCalculation>>(emptyList())
    private var nextId = 1L
    var failWrites = false
    override fun observeAll() = data.asStateFlow()
    override suspend fun findById(id: Long) = data.value.find { it.id == id }
    private fun publish(values: List<SavedCalculation>) {
        if (failWrites) error("Database unavailable")
        data.value = values.sortedWith(compareByDescending<SavedCalculation> { it.updatedAtEpochMs }.thenByDescending { it.id })
    }
    override suspend fun insert(value: SavedCalculation): Long {
        val id = if (value.id == 0L) nextId++ else value.id
        check(data.value.none { it.id == id })
        publish(data.value + value.copy(id = id))
        return id
    }
    override suspend fun update(value: SavedCalculation): Int {
        if (findById(value.id) == null) return 0
        publish(data.value.map { if (it.id == value.id) value else it }); return 1
    }
    override suspend fun delete(id: Long): Int {
        if (findById(id) == null) return 0
        publish(data.value.filterNot { it.id == id }); return 1
    }
    override suspend fun <T> transaction(block: suspend () -> T): T = block()
}

class MutableClock(var now: Long = 1000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HistoryRepositoryTest {
    private val example = DamageInput(12, 3, 8, -2, 2, 5, 3, 4)

    @Test fun savesEveryInputAndExactResults() = runTest {
        val store = MemoryHistoryStore()
        val repo = HistoryRepository(store, MutableClock(), StandardTestDispatcher(testScheduler))
        val saved = repo.saveNew("  Кириллица vs Interceptors  ", example)
        assertEquals(example, saved.input())
        assertEquals("Кириллица vs Interceptors", saved.title)
        assertEquals(4.0 / 9, saved.averageDamagePerAttack, 1e-9)
        assertEquals(16.0 / 3, saved.averageTotalDamage, 1e-9)
        assertEquals(1000L, saved.createdAtEpochMs)
        assertEquals(listOf(saved), repo.observeAll().first())
    }

    @Test fun duplicateNamesHaveIndependentIds() = runTest {
        val repo = HistoryRepository(MemoryHistoryStore(), MutableClock(), StandardTestDispatcher(testScheduler))
        val a = repo.saveNew("Одинаковое имя", example)
        val b = repo.saveNew("Одинаковое имя", example)
        assertNotEquals(a.id, b.id)
        assertEquals(listOf(b.id, a.id), repo.observeAll().first().map { it.id })
    }

    @Test fun titleValidationCountsUnicodeCodePoints() {
        listOf("", "  \t\n", "я".repeat(81), "😀".repeat(81)).forEach { title ->
            assertThrows(IllegalArgumentException::class.java) { validatedTitle(title) }
        }
        assertEquals("😀".repeat(80), validatedTitle("😀".repeat(80)))
        assertEquals("Русский и English", validatedTitle("  Русский и English  "))
    }

    @Test fun renameReordersWithoutChangingCalculation() = runTest {
        val clock = MutableClock()
        val repo = HistoryRepository(MemoryHistoryStore(), clock, StandardTestDispatcher(testScheduler))
        val a = repo.saveNew("Первый", example)
        val b = repo.saveNew("Второй", example)
        clock.now = 2000
        val renamed = repo.rename(a.id, "Новое имя")
        assertEquals(a.copy(title = "Новое имя", updatedAtEpochMs = 2000), renamed)
        assertEquals(listOf(a.id, b.id), repo.observeAll().first().map { it.id })
    }

    @Test fun explicitUpdateKeepsIdAndRecalculatesBothMetrics() = runTest {
        val repo = HistoryRepository(MemoryHistoryStore(), MutableClock(), StandardTestDispatcher(testScheduler))
        val original = repo.saveNew("Один", example)
        val changed = example.copy(attacks = 100, damage = 3, invulnerableSaveTarget = null)
        val updated = repo.saveChanges(original.id, changed)
        val expected = DamageCalculator.calculateDamage(changed)
        assertEquals(original.id, updated.id)
        assertEquals(changed, updated.input())
        assertEquals(expected.averageDamagePerAttack, updated.averageDamagePerAttack, 1e-9)
        assertEquals(expected.averageTotalDamage, updated.averageTotalDamage, 1e-9)
        assertEquals(1, repo.observeAll().first().size)
        repo.saveNew(original.title, changed)
        assertEquals(2, repo.observeAll().first().size)
    }

    @Test fun deleteAndUndoPreserveOriginalIdAndAllFields() = runTest {
        val repo = HistoryRepository(MemoryHistoryStore(), MutableClock(), StandardTestDispatcher(testScheduler))
        val original = repo.saveNew("Undo", example)
        val deleted = repo.delete(original.id)
        assertTrue(repo.observeAll().first().isEmpty())
        repo.restoreDeleted(deleted)
        assertEquals(listOf(original), repo.observeAll().first())
    }

    @Test fun missingRecordsCannotTurnIntoNewOnes() = runTest {
        val repo = HistoryRepository(MemoryHistoryStore(), MutableClock(), StandardTestDispatcher(testScheduler))
        var rejected = 0
        try { repo.saveChanges(999, example) } catch (_: IllegalArgumentException) { rejected++ }
        try { repo.rename(999, "Не существует") } catch (_: IllegalArgumentException) { rejected++ }
        assertEquals(2, rejected)
        assertTrue(repo.observeAll().first().isEmpty())
    }

    @Test fun repositoryValidatesInputIndependentlyOfUi() = runTest {
        val repo = HistoryRepository(MemoryHistoryStore(), MutableClock(), StandardTestDispatcher(testScheduler))
        var rejected = false
        try { repo.saveNew("Invalid", example.copy(armorPenetration = 2)) } catch (_: IllegalArgumentException) { rejected = true }
        assertTrue(rejected)
        assertTrue(repo.observeAll().first().isEmpty())
    }
}
