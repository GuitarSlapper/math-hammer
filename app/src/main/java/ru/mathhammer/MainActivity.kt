package ru.mathhammer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material3.MenuAnchorType

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val colors = if (isSystemInDarkTheme()) darkColorScheme(
                primary = Color(0xFFA3D4BC), secondary = Color(0xFFE3C281)
            ) else lightColorScheme(
                primary = Color(0xFF285E49), secondary = Color(0xFF795A20),
                background = Color(0xFFF5F5EE), surface = Color(0xFFFAFAF5)
            )
            MaterialTheme(colorScheme = colors) { MathHammerScreen() }
        }
    }
}

@Composable
fun MathHammerScreen() {
    var attacks by rememberSaveable { mutableStateOf("12") }
    var hit by rememberSaveable { mutableIntStateOf(3) }
    var strength by rememberSaveable { mutableStateOf("8") }
    var ap by rememberSaveable { mutableStateOf("-2") }
    var damage by rememberSaveable { mutableStateOf("2") }
    var toughness by rememberSaveable { mutableStateOf("5") }
    var save by rememberSaveable { mutableIntStateOf(3) }
    var inv by rememberSaveable { mutableIntStateOf(4) }
    var details by rememberSaveable { mutableStateOf(false) }

    val a = parseInteger(attacks, 0..9999)
    val s = parseInteger(strength, 1..999)
    val penetration = parseInteger(ap, -99..0)
    val d = parseInteger(damage, 1..999)
    val t = parseInteger(toughness, 1..999)
    val result = if (a != null && s != null && penetration != null && d != null && t != null) {
        DamageCalculator.calculateDamage(DamageInput(a, hit, s, penetration, d, t, save, inv.takeIf { it != 0 }))
    } else null

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("MathHammer", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Быстрый расчёт • Hit → Wound → Save → Damage", style = MaterialTheme.typography.bodySmall)
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Результат", style = MaterialTheme.typography.titleMedium)
                    Text("Средний суммарный урон", style = MaterialTheme.typography.bodyMedium)
                    Text(result?.let { format(it.averageTotalDamage, 2) } ?: "—",
                        style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
                    Text("За ${a ?: "—"} атак", style = MaterialTheme.typography.labelLarge)
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Text("Средний урон за 1 атаку", style = MaterialTheme.typography.bodyMedium)
                    Text(result?.let { format(it.averageDamagePerAttack, 3) } ?: "—",
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    if (result == null) Text("Проверьте значения", color = MaterialTheme.colorScheme.error)
                    Text("1 атака — один атакующий кубик, с учётом попадания, ранения и сейва.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
            InputCard("Атакующий") {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("A · Число атак", attacks, { attacks = it }, 0..9999, Modifier.weight(1f))
                    SaveSelector("BS/WS · Попадание", hit, { hit = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField("S · Сила", strength, { strength = it }, 1..999, Modifier.weight(1f))
                    NumberField("AP · Пробитие", ap, { ap = it }, -99..0, Modifier.weight(1f))
                }
                NumberField("D · Урон", damage, { damage = it }, 1..999, Modifier.fillMaxWidth())
            }
            InputCard("Защитник") {
                NumberField("T · Стойкость", toughness, { toughness = it }, 1..999, Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SaveSelector("Sv · Броня", save, { save = it }, Modifier.weight(1f))
                    SaveSelector("Inv · Инвуль", inv, { inv = it }, Modifier.weight(1f), allowNone = true)
                }
            }
            TextButton(onClick = { details = !details }) {
                Text(if (details) "Скрыть детали расчёта ▴" else "Детали расчёта ▾")
            }
            if (details && result != null) {
                Text("Попадание: ${format(result.hitProbability * 100, 1)}%\n" +
                    "Ранение (${result.woundTarget}+): ${format(result.woundProbability * 100, 1)}%\n" +
                    "Выбранный сейв: ${result.effectiveSaveTarget}+\n" +
                    "Провал сейва: ${format(result.failedSaveProbability * 100, 1)}%\n" +
                    "Среднее число атак, прошедших сейв: ${format(result.expectedUnsavedAttacks, 2)}",
                    style = MaterialTheme.typography.bodyMedium)
            }
            Text("Ожидаемый урон после сейвов; не учитывает избыточный урон и количество Wounds цели.",
                style = MaterialTheme.typography.bodySmall)
            Text("Упрощённая модель: одинаковые независимые атаки по одной однородной цели. Без особых способностей и модификаторов.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun InputCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    OutlinedCard {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, range: IntRange, modifier: Modifier) {
    val invalid = parseInteger(value, range) == null
    OutlinedTextField(
        value = value, onValueChange = onChange, modifier = modifier, label = { Text(label) },
        singleLine = true, isError = invalid,
        supportingText = if (invalid) ({ Text("Целое: ${range.first}…${range.last}") }) else null,
        keyboardOptions = KeyboardOptions(keyboardType = if (range.first < 0) KeyboardType.Text else KeyboardType.Number)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SaveSelector(label: String, value: Int, onChange: (Int) -> Unit, modifier: Modifier, allowNone: Boolean = false) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = if (value == 0) "Нет" else "$value+", onValueChange = {}, readOnly = true,
            label = { Text(label) }, singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            val choices = if (allowNone) listOf(0, 2, 3, 4, 5, 6) else (2..6).toList()
            choices.forEach { target ->
                DropdownMenuItem(text = { Text(if (target == 0) "Нет" else "$target+") },
                    onClick = { onChange(target); expanded = false })
            }
        }
    }
}

private fun format(value: Double, decimals: Int): String =
    String.format(Locale.forLanguageTag("ru-RU"), "%.${decimals}f", value)
