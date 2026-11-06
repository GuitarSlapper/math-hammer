package ru.mathhammer

data class DamageInput(
    val attacks: Int,
    val hitTarget: Int,
    val strength: Int,
    val armorPenetration: Int,
    val damage: Int,
    val toughness: Int,
    val saveTarget: Int,
    val invulnerableSaveTarget: Int?
)

data class DamageResult(
    val woundTarget: Int,
    val effectiveSaveTarget: Int,
    val hitProbability: Double,
    val woundProbability: Double,
    val failedSaveProbability: Double,
    val successfulAttackProbability: Double,
    val expectedUnsavedAttacks: Double,
    val averageDamagePerAttack: Double,
    val averageTotalDamage: Double
)

object DamageCalculator {
    fun calculateDamage(input: DamageInput): DamageResult = with(input) {
        require(attacks in 0..9999) { "A: целое от 0 до 9999" }
        require(hitTarget in 2..6) { "BS/WS: от 2+ до 6+" }
        require(strength in 1..999) { "S: целое от 1 до 999" }
        require(armorPenetration in -99..0) { "AP: целое от −99 до 0" }
        require(damage in 1..999) { "D: целое от 1 до 999" }
        require(toughness in 1..999) { "T: целое от 1 до 999" }
        require(saveTarget in 2..6) { "Sv: от 2+ до 6+" }
        require(invulnerableSaveTarget == null || invulnerableSaveTarget in 2..6) {
            "Inv: Нет или от 2+ до 6+"
        }
        val woundTarget = when {
            strength.toLong() >= 2L * toughness -> 2
            strength > toughness -> 3
            strength == toughness -> 4
            2L * strength > toughness -> 5
            else -> 6
        }
        val armor = saveTarget - armorPenetration
        val effective = invulnerableSaveTarget?.let { minOf(armor, it) } ?: armor
        val hit = (7 - hitTarget) / 6.0
        val wound = (7 - woundTarget) / 6.0
        val fail = ((effective - 1) / 6.0).coerceIn(1.0 / 6.0, 1.0)
        val success = hit * wound * fail
        val perAttack = success * damage
        DamageResult(woundTarget, effective, hit, wound, fail, success,
            attacks * success, perAttack, attacks * perAttack)
    }
}
