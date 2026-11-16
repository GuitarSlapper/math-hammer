package ru.mathhammer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import ru.mathhammer.history.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class HistoryDatabaseTest {
    private val input = DamageInput(12, 3, 8, -2, 2, 5, 3, 4)

    @Test fun realRoomSurvivesReopenAndPreservesSnapshotsAndUndo() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "history-test-${UUID.randomUUID()}.db"
        var database = HistoryDatabase.open(context, name)
        try {
            var repository = HistoryRepository(RoomHistoryStore(database), Clock.fixed(Instant.ofEpochMilli(1000), ZoneOffset.UTC))
            val first = repository.saveNew("Один и тот же заголовок", input)
            val second = repository.saveNew("Один и тот же заголовок", input.copy(invulnerableSaveTarget = null))
            database.close()
            database = HistoryDatabase.open(context, name)
            repository = HistoryRepository(RoomHistoryStore(database), Clock.fixed(Instant.ofEpochMilli(2000), ZoneOffset.UTC))
            assertEquals(listOf(second, first), repository.observeAll().first())
            assertEquals(input, repository.findById(first.id).input())
            assertEquals(4.0 / 9, repository.findById(first.id).averageDamagePerAttack, 1e-9)
            assertEquals(16.0 / 3, repository.findById(first.id).averageTotalDamage, 1e-9)
            repository.rename(first.id, "Новое имя")
            assertEquals(listOf(first.id, second.id), repository.observeAll().first().map { it.id })
            val deleted = repository.delete(first.id)
            assertEquals(1, repository.observeAll().first().size)
            repository.restoreDeleted(deleted)
            assertEquals(deleted, repository.findById(first.id))
            val updated = repository.saveChanges(first.id, input.copy(attacks = 1))
            assertEquals(2, repository.observeAll().first().size)
            assertEquals(first.id, updated.id)
            assertEquals(4.0 / 9, updated.averageTotalDamage, 1e-9)
        } finally { database.close(); context.deleteDatabase(name) }
    }
}
