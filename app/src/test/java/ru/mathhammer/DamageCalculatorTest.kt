package ru.mathhammer

import org.junit.Assert.*
import org.junit.Test

class DamageCalculatorTest {
    private val example = DamageInput(12, 3, 8, -2, 2, 5, 3, 4)
    private fun calculate(input: DamageInput = example) = DamageCalculator.calculateDamage(input)
    private fun close(expected: Double, actual: Double) = assertEquals(expected, actual, 1e-9)

    @Test fun referenceExample() {
        val r = calculate()
        close(4.0 / 9, r.averageDamagePerAttack)
        close(16.0 / 3, r.averageTotalDamage)
        close(8.0 / 3, r.expectedUnsavedAttacks)
        close(2.0 / 9, r.successfulAttackProbability)
        close(4.0 / 6, r.hitProbability)
        close(4.0 / 6, r.woundProbability)
    }
    @Test fun attacksScaleOnlyTotal() {
        val one = calculate(example.copy(attacks = 1))
        val hundred = calculate(example.copy(attacks = 100))
        close(one.averageDamagePerAttack, hundred.averageDamagePerAttack)
        close(one.averageTotalDamage * 100, hundred.averageTotalDamage)
    }
    @Test fun zeroAttacksPreserveHypotheticalDamage() {
        val r = calculate(example.copy(attacks = 0))
        close(0.0, r.averageTotalDamage)
        close(0.0, r.expectedUnsavedAttacks)
        close(4.0 / 9, r.averageDamagePerAttack)
    }
    @Test fun woundBoundaries() {
        listOf(Triple(8, 4, 2), Triple(6, 5, 3), Triple(5, 5, 4), Triple(5, 8, 5), Triple(4, 8, 6))
            .forEach { (s, t, target) ->
                val r = calculate(example.copy(strength = s, toughness = t))
                assertEquals(target, r.woundTarget)
                close((7 - target) / 6.0, r.woundProbability)
            }
    }
    @Test fun invulnerableSaveIsChosenInsteadOfArmor() {
        assertEquals(4, calculate().effectiveSaveTarget)
        close(0.5, calculate().failedSaveProbability)
    }
    @Test fun noInvulnerableSave() {
        val r = calculate(example.copy(invulnerableSaveTarget = null))
        assertEquals(5, r.effectiveSaveTarget)
        close(4.0 / 6, r.failedSaveProbability)
    }
    @Test fun armorCanBeBetterThanInvulnerableSave() {
        val r = calculate(example.copy(saveTarget = 2, armorPenetration = 0, invulnerableSaveTarget = 5))
        assertEquals(2, r.effectiveSaveTarget)
        close(1.0 / 6, r.failedSaveProbability)
    }
    @Test fun impossibleArmorSaveAlwaysFails() {
        val r = calculate(example.copy(armorPenetration = -5, invulnerableSaveTarget = null))
        assertEquals(8, r.effectiveSaveTarget)
        close(1.0, r.failedSaveProbability)
    }
    @Test fun invalidInputsAreRejected() {
        listOf(
            example.copy(hitTarget = 1), example.copy(hitTarget = 7),
            example.copy(toughness = 0), example.copy(toughness = 1000),
            example.copy(armorPenetration = 2), example.copy(armorPenetration = -100),
            example.copy(damage = 0), example.copy(damage = 1000),
            example.copy(invulnerableSaveTarget = 7), example.copy(invulnerableSaveTarget = 1),
            example.copy(attacks = -1), example.copy(attacks = 10000),
            example.copy(strength = 0), example.copy(strength = 1000),
            example.copy(saveTarget = 1), example.copy(saveTarget = 7)
        ).forEach { input -> assertThrows(IllegalArgumentException::class.java) { calculate(input) } }
    }
    @Test fun noIntermediateRounding() { close(5.333333333333333, calculate().averageTotalDamage) }
    @Test fun extremeValidValuesStayFinite() {
        val r = calculate(DamageInput(9999, 2, 999, -99, 999, 1, 6, null))
        assertTrue(r.averageTotalDamage.isFinite())
        close(5.0 / 6 * (5.0 / 6) * 999 * 9999, r.averageTotalDamage)
    }
    @Test fun hitThresholds() {
        (2..6).forEach { close((7 - it) / 6.0, calculate(example.copy(hitTarget = it)).hitProbability) }
    }
    @Test fun invalidTextNeverBecomesZero() {
        listOf("", "-", " ", " 2", "2 ", "1.5", "abc", "999999999999999999999", "+2")
            .forEach { assertNull(parseInteger(it, 0..9999)) }
        assertNull(parseInteger("2", -99..0))
        assertEquals(-2, parseInteger("-2", -99..0))
        assertEquals(0, parseInteger("0", 0..9999))
        assertEquals(9999, parseInteger("9999", 0..9999))
    }
}
