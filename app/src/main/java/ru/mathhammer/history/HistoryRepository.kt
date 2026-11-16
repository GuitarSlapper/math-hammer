package ru.mathhammer.history

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.mathhammer.DamageCalculator
import ru.mathhammer.DamageInput
import java.time.Clock

fun titleCodePoints(title: String): Int = title.codePointCount(0, title.length)

fun validatedTitle(title: String): String = title.trim().also {
    require(titleCodePoints(it) in 1..80) { "Введите название от 1 до 80 символов" }
}

class HistoryRepository(
    private val store: HistoryStore,
    private val clock: Clock = Clock.systemUTC(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    fun observeAll() = store.observeAll()
    suspend fun findById(id: Long) = withContext(dispatcher) { existing(id) }

    suspend fun saveNew(title: String, input: DamageInput): SavedCalculation = withContext(dispatcher) {
        val name = validatedTitle(title)
        val result = DamageCalculator.calculateDamage(input)
        val now = clock.millis()
        val value = SavedCalculation(title = name, attacks = input.attacks, hitTarget = input.hitTarget,
            strength = input.strength, armorPenetration = input.armorPenetration, damage = input.damage,
            toughness = input.toughness, saveTarget = input.saveTarget,
            invulnerableSaveTarget = input.invulnerableSaveTarget,
            averageDamagePerAttack = result.averageDamagePerAttack,
            averageTotalDamage = result.averageTotalDamage, createdAtEpochMs = now, updatedAtEpochMs = now)
        value.copy(id = store.insert(value))
    }

    suspend fun saveChanges(id: Long, input: DamageInput): SavedCalculation = withContext(dispatcher) {
        val result = DamageCalculator.calculateDamage(input)
        store.transaction {
            val current = existing(id)
            val next = current.copy(attacks = input.attacks, hitTarget = input.hitTarget,
                strength = input.strength, armorPenetration = input.armorPenetration, damage = input.damage,
                toughness = input.toughness, saveTarget = input.saveTarget,
                invulnerableSaveTarget = input.invulnerableSaveTarget,
                averageDamagePerAttack = result.averageDamagePerAttack, averageTotalDamage = result.averageTotalDamage,
                calculationVersion = CALCULATION_VERSION, updatedAtEpochMs = clock.millis())
            check(store.update(next) == 1) { "Запись не найдена" }
            next
        }
    }

    suspend fun rename(id: Long, title: String): SavedCalculation = withContext(dispatcher) {
        val name = validatedTitle(title)
        store.transaction {
            val next = existing(id).copy(title = name, updatedAtEpochMs = clock.millis())
            check(store.update(next) == 1) { "Запись не найдена" }
            next
        }
    }

    suspend fun delete(id: Long): SavedCalculation = withContext(dispatcher) {
        store.transaction {
            val value = existing(id)
            check(store.delete(id) == 1) { "Запись не найдена" }
            value
        }
    }

    suspend fun restoreDeleted(value: SavedCalculation) = withContext(dispatcher) {
        // Исходный ID, timestamps и snapshot сохраняются; конфликт не перезаписывает чужую запись.
        store.insert(value)
        Unit
    }

    private suspend fun existing(id: Long): SavedCalculation =
        requireNotNull(store.findById(id)) { "Расчёт уже удалён или не найден" }
}
