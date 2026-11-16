package ru.mathhammer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import ru.mathhammer.*
import ru.mathhammer.R
import ru.mathhammer.history.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

fun formatNumber(value: Double, decimals: Int) = String.format(Locale.forLanguageTag("ru-RU"), "%.${decimals}f", value)

@Composable
fun MathHammerApp(model: CalculatorViewModel, onFinish: () -> Unit) {
    val state by model.editor.collectAsStateWithLifecycle()
    val history by model.history.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val currentFinish by rememberUpdatedState(onFinish)
    LaunchedEffect(model) {
        model.events.collectLatest { event ->
            when (event) {
                UiEvent.Finish -> currentFinish()
                is UiEvent.Message -> {
                    val answer = snackbar.showSnackbar(event.text, actionLabel = if (event.deleted != null) "Отменить" else null,
                        withDismissAction = true, duration = SnackbarDuration.Short)
                    if (answer == SnackbarResult.ActionPerformed && event.deleted != null) model.undoDelete(event.deleted)
                }
            }
        }
    }
    BackHandler { if (state.historyVisible) model.showHistory(false) else model.requestExit() }
    Scaffold(
        modifier = Modifier.fillMaxSize().imePadding(), containerColor = Background,
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = { AppHeader(state.historyVisible, onHistory = { model.showHistory(true) }, onBack = { model.showHistory(false) }) },
        bottomBar = {
            NavigationBar(containerColor = ColorNav) {
                NavigationBarItem(selected = !state.historyVisible, onClick = { model.showHistory(false) },
                    modifier = Modifier.testTag("nav_calculator"), icon = { KitIcon(R.drawable.ic_dice, null) },
                    label = { Text("Калькулятор", fontSize = 14.sp) }, colors = navColors())
                NavigationBarItem(selected = state.historyVisible, onClick = { model.showHistory(true) },
                    modifier = Modifier.testTag("nav_history"), icon = { ListGlyph() },
                    label = { Text("История", fontSize = 14.sp) }, colors = navColors())
            }
        }, snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (state.historyVisible) HistoryScreen(history, state.busy, Modifier.padding(padding), model::requestOpen,
            model::rename, model::delete, model::requestNew, model::retryHistory)
        else CalculatorScreen(state, model, Modifier.padding(padding))
    }
    state.dialog?.let { NameDialogUi(it, state.busy, state.result, model::changeName, model::submitName, model::closeDialog) }
    if (state.pendingAction != null) AlertDialog(
        onDismissRequest = model::cancelDiscard,
        title = { Text("Сбросить несохранённые изменения?") },
        text = { Text("Изменённый черновик будет потерян. Сохранённые расчёты останутся в истории.") },
        confirmButton = { TextButton(model::confirmDiscard, Modifier.testTag("discard_confirm")) { Text("Сбросить") } },
        dismissButton = { TextButton(model::cancelDiscard) { Text("Продолжить редактирование") } })
}

private val ColorNav = androidx.compose.ui.graphics.Color(0xFF131D26)
@Composable private fun navColors() = NavigationBarItemDefaults.colors(selectedIconColor = Gold,
    selectedTextColor = Gold, unselectedIconColor = Muted, unselectedTextColor = Muted, indicatorColor = Panel)

@Composable
private fun KitIcon(resource: Int, description: String?, modifier: Modifier = Modifier) {
    Icon(painterResource(resource), description, modifier.size(26.dp), tint = LocalContentColor.current)
}

@Composable
private fun ListGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(26.dp)) {
        repeat(3) { row ->
            val y = size.height * (.22f + row * .28f)
            drawCircle(color, 1.5.dp.toPx(), Offset(3.dp.toPx(), y))
            drawLine(color, Offset(8.dp.toPx(), y), Offset(size.width, y), 2.dp.toPx())
        }
    }
}

@Composable
private fun AppHeader(history: Boolean, onHistory: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CompositionLocalProvider(LocalContentColor provides Gold) { KitIcon(R.drawable.ic_dice, null, Modifier.size(30.dp)) }
            Column(Modifier.weight(1f)) {
                Text("MathHammer", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("Калькулятор среднего урона", fontSize = 14.sp, color = Muted)
            }
            if (history) IconButton(onBack, Modifier.semantics { contentDescription = "Вернуться в калькулятор" }) {
                Text("←", fontSize = 28.sp, color = Muted)
            } else IconButton(onHistory, Modifier.semantics { contentDescription = "Открыть историю" }) { ListGlyph() }
        }
        HorizontalDivider(color = Border)
    }
}

@Composable
private fun CalculatorScreen(state: EditorState, model: CalculatorViewModel, modifier: Modifier) {
    val draft = state.draft
    val listState = key(state.draftGeneration) { rememberLazyListState() }
    LazyColumn(modifier.fillMaxSize().testTag("calculator_list"), state = listState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (state.editingId != null) item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(state.title.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                if (state.dirty) Text("Есть несохранённые изменения", color = Gold, fontSize = 14.sp)
                if (state.versionMismatch) Text("Версия расчёта изменилась. Результат пересчитан; snapshot обновится только после сохранения.",
                    color = Gold, fontSize = 14.sp)
            }
        }
        item { SectionCard("АТАКУЮЩИЙ", R.drawable.ic_attack,
            "A — общее число атакующих кубиков; BS/WS — попадание; S — сила; AP — пробитие (0 или минус); D — урон.") {
            FieldGrid(5) { index, field ->
                when (index) {
                    0 -> NumericField("A", "Число атак", draft.attacks, 0..9999, state.busy, field) { model.changeDraft(draft.copy(attacks = it)) }
                    1 -> ThresholdField("BS/WS", "Попадание", draft.hit, state.busy, field) { model.changeDraft(draft.copy(hit = it)) }
                    2 -> NumericField("S", "Сила", draft.strength, 1..999, state.busy, field) { model.changeDraft(draft.copy(strength = it)) }
                    3 -> NumericField("AP", "Пробитие", draft.ap, -99..0, state.busy, field) { model.changeDraft(draft.copy(ap = it)) }
                    4 -> NumericField("D", "Урон", draft.damage, 1..999, state.busy, field) { model.changeDraft(draft.copy(damage = it)) }
                }
            }
        } }
        item { SectionCard("ЗАЩИТНИК", R.drawable.ic_defender,
            "T — стойкость; Sv — броня; Inv — неуязвимый сейв или «Нет». Используется один лучший сейв.") {
            FieldGrid(3) { index, field ->
                when (index) {
                    0 -> NumericField("T", "Стойкость", draft.toughness, 1..999, state.busy, field) { model.changeDraft(draft.copy(toughness = it)) }
                    1 -> ThresholdField("Sv", "Броня", draft.save, state.busy, field) { model.changeDraft(draft.copy(save = it)) }
                    2 -> ThresholdField("Inv", "Инвуль", draft.inv, state.busy, field, allowNone = true) { model.changeDraft(draft.copy(inv = it)) }
                }
            }
        } }
        item { Results(state.result) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.editingId == null) {
                    Button(model::saveAsNew, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_new"),
                        enabled = state.input != null && !state.busy, shape = RoundedCornerShape(12.dp)) {
                        BookmarkGlyph()
                        Spacer(Modifier.width(12.dp))
                        Text("СОХРАНИТЬ РАСЧЁТ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Button(model::saveChanges, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_changes"),
                        enabled = state.input != null && !state.busy, shape = RoundedCornerShape(12.dp)) {
                        Text(if (state.busy) "Сохранение…" else "Сохранить изменения", fontSize = 16.sp)
                    }
                    OutlinedButton(model::saveAsNew, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("save_new"), enabled = state.input != null && !state.busy) {
                        Text("Сохранить как новый", fontSize = 14.sp)
                    }
                    TextButton(model::revertChanges, Modifier.fillMaxWidth(), enabled = state.dirty && !state.busy) { Text("Отменить изменения", fontSize = 14.sp) }
                }
                TextButton(model::requestNew, Modifier.fillMaxWidth().testTag("new_calculation"), enabled = !state.busy) { Text("Новый расчёт", fontSize = 14.sp) }
            }
        }
        item { CalculationDetails(state.result) }
        item { Text("Ожидаемый урон после сейвов; не учитывает избыточный урон и количество Wounds цели.",
            color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun BookmarkGlyph() {
    val color = LocalContentColor.current
    Canvas(Modifier.size(22.dp)) {
        val path = Path().apply {
            moveTo(size.width * .25f, 0f); lineTo(size.width * .75f, 0f)
            lineTo(size.width * .75f, size.height); lineTo(size.width * .5f, size.height * .75f)
            lineTo(size.width * .25f, size.height); close()
        }
        drawPath(path, color, style = Stroke(1.5.dp.toPx()))
    }
}

@Composable
private fun FieldGrid(count: Int, field: @Composable (Int, Modifier) -> Unit) {
    val scale = LocalDensity.current.fontScale
    BoxWithConstraints {
        val columns = when {
            scale > 1.8f -> 1
            scale > 1.35f || maxWidth < 270.dp -> 2
            count == 5 && maxWidth >= 320.dp && scale <= 1.15f -> 5
            else -> 3
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            (0 until count).chunked(columns).forEach { indices ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    indices.forEach { index -> field(index, Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, icon: Int, explanation: String, content: @Composable ColumnScope.() -> Unit) {
    var help by rememberSaveable { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(12.dp), color = Panel, border = BorderStroke(1.dp, Border),
        modifier = Modifier.fillMaxWidth().drawWithContent {
            drawContent()
            val curve = Path().apply { moveTo(0f, 24.dp.toPx()); quadraticTo(0f, 0f, 24.dp.toPx(), 0f); lineTo(size.width * .32f, 0f) }
            drawPath(curve, Gold, style = Stroke(2.dp.toPx()))
        }) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { help = !help }
                .semantics { contentDescription = "Пояснения к характеристикам: $title" },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CompositionLocalProvider(LocalContentColor provides Gold) { KitIcon(icon, null) }
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text("ⓘ", color = Muted, fontSize = 18.sp)
            }
            HorizontalDivider(color = Border)
            content()
            if (help) Text(explanation, fontSize = 14.sp, color = Muted)
        }
    }
}

@Composable
private fun NumericField(label: String, description: String, value: String, range: IntRange, busy: Boolean,
    modifier: Modifier, onChange: (String) -> Unit) {
    val invalid = parseInteger(value, range) == null
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val outline = if (invalid) MaterialTheme.colorScheme.error else if (focused) Gold else Border
    Column(modifier) {
        Text(label, fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        BasicTextField(value, { onChange(it.take(128)) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(androidx.compose.ui.graphics.Color(0xFF202C37), RoundedCornerShape(8.dp))
            .border(1.dp, outline, RoundedCornerShape(8.dp)).testTag("field_$label")
            .semantics { contentDescription = "$label · $description"; if (invalid) error("Целое: ${range.first}…${range.last}") }, enabled = !busy,
            textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center),
            singleLine = true, interactionSource = interaction, cursorBrush = SolidColor(Gold),
            keyboardOptions = KeyboardOptions(keyboardType = if (range.first < 0) KeyboardType.Text else KeyboardType.Number),
            decorationBox = { inner -> Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { inner() } })
        if (invalid) Text("Целое: ${range.first}…${range.last}", color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
    }
}

@Composable private fun fieldColors() = OutlinedTextFieldDefaults.colors(unfocusedContainerColor = ColorNav,
    focusedContainerColor = ColorNav, errorContainerColor = ColorNav, unfocusedBorderColor = Border,
    focusedBorderColor = Gold, cursorColor = Gold)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThresholdField(label: String, description: String, value: Int, busy: Boolean, modifier: Modifier,
    allowNone: Boolean = false, onChange: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label, fontSize = 14.sp, color = Muted, fontWeight = FontWeight.SemiBold)
        ExposedDropdownMenuBox(expanded, { if (!busy) expanded = it }) {
            BasicTextField(if (value == 0) "Нет" else "$value+", {}, readOnly = true, enabled = !busy,
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth().heightIn(min = 48.dp)
                    .background(androidx.compose.ui.graphics.Color(0xFF202C37), RoundedCornerShape(8.dp))
                    .border(1.dp, Border, RoundedCornerShape(8.dp)).testTag("field_$label")
                    .semantics { contentDescription = "$label · $description" },
                textStyle = MaterialTheme.typography.titleLarge.copy(color = Ink, fontSize = 22.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold),
                singleLine = true, cursorBrush = SolidColor(Gold),
                decorationBox = { inner -> Box(Modifier.padding(horizontal = 4.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { inner() } })
            ExposedDropdownMenu(expanded, { expanded = false }) {
                (if (allowNone) listOf(0, 2, 3, 4, 5, 6) else (2..6).toList()).forEach { target ->
                    DropdownMenuItem(text = { Text(if (target == 0) "Нет" else "$target+", fontSize = 16.sp) },
                        onClick = { onChange(target); expanded = false })
                }
            }
        }
    }
}

@Composable
private fun Results(result: DamageResult?) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("СРЕДНИЙ ПОТЕНЦИАЛЬНЫЙ УРОН", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Muted)
        val total = result?.let { formatNumber(it.averageTotalDamage, 2) } ?: "—"
        val single = result?.let { formatNumber(it.averageDamagePerAttack, 3) } ?: "—"
        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints {
            if (maxWidth < 330.dp || fontScale > 1.25f || total.length > 7) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ResultTile("НА 1 АТАКУ", single, Modifier.fillMaxWidth())
                    ResultTile("ВСЕГО", total, Modifier.fillMaxWidth())
                }
            } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultTile("НА 1 АТАКУ", single, Modifier.weight(1f))
                ResultTile("ВСЕГО", total, Modifier.weight(1f))
            }
        }
        Text(if (result == null) "Проверьте значения" else "Ожидаемый урон после сейвов", fontSize = 14.sp,
            color = if (result == null) MaterialTheme.colorScheme.error else Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("1 атака — один атакующий кубик.",
            color = Muted, fontSize = 14.sp)
    }
}

@Composable
private fun ResultTile(title: String, value: String, modifier: Modifier) {
    Surface(modifier, color = androidx.compose.ui.graphics.Color(0xFF362226), shape = RoundedCornerShape(12.dp), border = BorderStroke(1.5.dp, Danger)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(value, color = Gold, fontSize = if (value.length > 7) 32.sp else 42.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun CalculationDetails(result: DamageResult?) {
    var details by rememberSaveable { mutableStateOf(false) }
    Column {
        TextButton({ details = !details }, Modifier.fillMaxWidth()) { Text(if (details) "Скрыть детали расчёта ▴" else "Детали расчёта ▾", fontSize = 14.sp) }
            if (details && result != null) Text("Одинаковые независимые атаки по одной цели, без особых способностей. Один кубик учитывается до проверки попадания.\n" +
            "Попадание: ${formatNumber(result.hitProbability * 100, 1)}%\n" +
            "Ранение (${result.woundTarget}+): ${formatNumber(result.woundProbability * 100, 1)}%\n" +
            "Выбранный сейв: ${result.effectiveSaveTarget}+\nПровал сейва: ${formatNumber(result.failedSaveProbability * 100, 1)}%\n" +
            "Среднее число атак, прошедших сейв: ${formatNumber(result.expectedUnsavedAttacks, 2)}", fontSize = 14.sp, color = Muted)
    }
}

@Composable
private fun HistoryScreen(state: HistoryState, busy: Boolean, modifier: Modifier, onOpen: (Long) -> Unit,
    onRename: (SavedCalculation) -> Unit, onDelete: (Long) -> Unit, onNew: () -> Unit, onRetry: () -> Unit) {
    Column(modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("СОХРАНЁННЫЕ РАСЧЁТЫ", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (!state.loading && !state.error) Text("${state.entries.size}", color = Muted, fontSize = 14.sp)
        }
        when {
            state.loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                Text("Не удалось загрузить историю. Данные не удалены.", fontSize = 18.sp)
                TextButton(onRetry) { Text("Повторить") }
            }
            state.entries.isEmpty() -> Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                CompositionLocalProvider(LocalContentColor provides Gold) { KitIcon(R.drawable.ic_result, null, Modifier.size(64.dp)) }
                Spacer(Modifier.height(20.dp))
                Text("Сохранённых расчётов пока нет", textAlign = TextAlign.Center, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(12.dp))
                Text("Сделайте расчёт и нажмите «Сохранить»", textAlign = TextAlign.Center, fontSize = 16.sp, color = Muted)
            }
            else -> LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.entries, key = { it.id }) { entry -> HistoryCard(entry, busy, onOpen, onRename, onDelete) }
            }
        }
        Button(onNew, Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("new_calculation"), enabled = !busy, shape = RoundedCornerShape(12.dp)) {
            Text("＋  Новый расчёт", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun HistoryCard(value: SavedCalculation, busy: Boolean, onOpen: (Long) -> Unit,
    onRename: (SavedCalculation) -> Unit, onDelete: (Long) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(Modifier.fillMaxWidth().testTag("history_entry_${value.id}").clickable(enabled = !busy) { onOpen(value.id) },
        color = Panel, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Border)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(value.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(Instant.ofEpochMilli(value.updatedAtEpochMs).atZone(ZoneId.systemDefault())
                        .format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)), fontSize = 14.sp, color = Muted)
                }
                Box {
                    IconButton({ menu = true }, Modifier.testTag("more_${value.id}").semantics { contentDescription = "Действия с расчётом ${value.title}" }, enabled = !busy) {
                        Text("⋮", fontSize = 28.sp, color = Muted)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Переименовать") }, onClick = { menu = false; onRename(value) })
                        DropdownMenuItem(text = { Text("Удалить") }, onClick = { menu = false; onDelete(value.id) })
                    }
                }
            }
            Text("A${value.attacks} · BS/WS${value.hitTarget}+ · S${value.strength} · AP${value.armorPenetration} · D${value.damage}\n" +
                "T${value.toughness} · Sv${value.saveTarget}+ · Inv${value.invulnerableSaveTarget?.let { "$it+" } ?: "Нет"}", color = Muted, fontSize = 14.sp)
            HorizontalDivider(color = Border)
            val scale = LocalDensity.current.fontScale
            BoxWithConstraints {
                if (maxWidth < 300.dp || scale > 1.25f || formatNumber(value.averageTotalDamage, 2).length > 7) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${formatNumber(value.averageDamagePerAttack, 3)} / атака", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${formatNumber(value.averageTotalDamage, 2)} всего", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                } else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("${formatNumber(value.averageDamagePerAttack, 3)} / атака", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${formatNumber(value.averageTotalDamage, 2)} всего", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun NameDialogUi(state: NameDialog, busy: Boolean, result: DamageResult?, onName: (String) -> Unit,
    onSave: () -> Unit, onDismiss: () -> Unit) {
    val valid = runCatching { validatedTitle(state.text) }.isSuccess
    AlertDialog(onDismissRequest = { if (!busy) onDismiss() }, shape = RoundedCornerShape(12.dp), containerColor = Panel,
        modifier = Modifier.border(1.dp, Gold, RoundedCornerShape(12.dp)),
        icon = { CompositionLocalProvider(LocalContentColor provides Gold) { BookmarkGlyph() } },
        title = { Text(if (state.mode == "save") "Сохранить расчёт" else "Переименовать расчёт", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(state.text, onName, Modifier.fillMaxWidth().testTag("name_input"),
                    enabled = !busy, label = { Text("Название") }, minLines = 2, maxLines = 4,
                    isError = !valid || state.error != null, colors = fieldColors(),
                    supportingText = { Text(if (!valid) "Введите название от 1 до 80 символов" else "${titleCodePoints(state.text.trim())} / 80", fontSize = 14.sp) })
                if (state.error != null) Text(state.error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                if (state.mode == "save" && result != null) Text("Будут сохранены все 8 характеристик и оба результата: " +
                    "${formatNumber(result.averageDamagePerAttack, 3)} / атака и ${formatNumber(result.averageTotalDamage, 2)} всего.", color = Muted, fontSize = 14.sp)
            }
        },
        confirmButton = { Button(onSave, Modifier.testTag("dialog_save"), enabled = valid && !busy, shape = RoundedCornerShape(8.dp)) {
            Text(if (busy) "Сохранение…" else if (state.mode == "save") "Сохранить" else "Переименовать")
        } }, dismissButton = { OutlinedButton(onDismiss, enabled = !busy, shape = RoundedCornerShape(8.dp)) { Text("Отмена") } })
}
