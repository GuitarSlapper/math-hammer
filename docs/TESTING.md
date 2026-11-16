# Сборка и тестирование

## Среда

| Компонент | Версия |
|---|---|
| JDK | 21 |
| Gradle Wrapper | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin / Compose compiler | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| Compile / target SDK | 35 |
| Minimum SDK | 26 |
| Compose BOM / Material 3 | 2024.10.00 / 1.3.0 |
| Room | 2.7.1 |
| Lifecycle | 2.8.7 |
| Coroutines | 1.10.1 |

Версии находятся в корневом и модульном Gradle-файлах. Material 3 получает версию из BOM.
Не обновляйте один компонент без проверки совместимости остальных.
Локальное расположение SDK задаётся Android Studio или исключённым из Git `local.properties`.

В Windows в `gradle.properties` используется `file.encoding=COMPAT`: это предотвращает ошибку загрузки
Gradle Worker при кириллице в пути пользовательского кэша. Исходники и ресурсы сохраняйте в UTF-8.

## Команды

Из PowerShell с настроенным JDK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
.\gradlew.bat connectedDebugAndroidTest
.\gradlew.bat assembleRelease lintRelease
```

На Linux/macOS используйте `sh gradlew`. Для `connectedDebugAndroidTest` нужен запущенный эмулятор
или подключённое устройство с разрешённой отладкой. При нескольких устройствах задайте `ANDROID_SERIAL`.
AndroidX Test 1.7.0 / Espresso 3.7.0 закреплены для совместимости с актуальным InputManager.

Артефакты сборки:

- Debug: `app/build/outputs/apk/debug/app-debug.apk` — подписан стандартным debug-ключом.
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk` — требует собственной подписи.
- JVM-отчёт: `app/build/reports/tests/testDebugUnitTest/index.html`.
- Android-отчёт: `app/build/reports/androidTests/connected/debug/index.html`.
- Lint: `app/build/reports/lint-results-release.html`.

Эти файлы генерируются локально и не входят в коммит.

## Что проверяют тесты

| Набор | Покрытие |
|---|---|
| `DamageCalculatorTest` | Контрольный пример, A=0, линейность, границы ранения, выбор сейва, точность и невалидный ввод |
| `HistoryRepositoryTest` | Все входы и результаты, Unicode-имена, дубли, сортировка, обновление, удаление/Undo и неизвестные ID |
| `CalculatorViewModelTest` | Независимость черновика, сохранённое состояние, ошибки записи, защита изменений и версии формул |
| `HistoryDatabaseTest` | Реальный Room/SQLite: повторное открытие БД, сохранность записей и ID, сортировка и Undo |
| `HistoryUiTest` | Пустая история, сохранение, переименование, открытие, Undo, ошибки ввода, диалоги и поворот экрана |

JVM-тесты находятся в `app/src/test`; инструментальные — в `app/src/androidTest`.
UI-тесты используют отдельную БД и debug-only `HistoryHarnessActivity`, чтобы не менять пользовательскую историю.
В Release этого экрана нет.

## Проверка вручную

1. Контрольный пример должен показывать **0,444** на атаку и **5,33** всего.
2. Пройдите сохранение → история → открытие → изменение → явное сохранение → переименование → удаление → Undo.
3. Проверьте, что новый расчёт и открытие другой записи требуют подтверждения при изменённом черновике.
4. Повторите цикл в авиарежиме; перезапустите приложение и откройте историю.
5. Проверьте поворот, крупный системный шрифт, длинное имя, клавиатуру и TalkBack.
6. Проверьте отсутствие INTERNET/ACCESS_NETWORK_STATE в итоговом merged manifest или APK.

Для просмотра разрешений собранного APK можно использовать `aapt2 dump permissions` из Android SDK Build Tools.
Правила backup/device transfer заданы в `app/src/main/res/xml/` и подключены в манифесте.
Проверка конфигурации не заменяет проверку переноса на конкретном устройстве производителя.

## Схемы и миграции

Схема первой БД экспортирована в `app/schemas/ru.mathhammer.history.HistoryDatabase/1.json`.
Версия v1 приложения БД не имела. При изменении схемы:

1. Увеличьте версию Room и добавьте явную `Migration`.
2. Сохраните новый schema JSON в репозитории.
3. Добавьте тест миграции с сохранением пользовательских записей.
4. Не используйте destructive fallback.

`calculationVersion` — версия математической модели, а не версия приложения или схемы БД.
Старый snapshot обновляется только после явного сохранения пересчитанных значений.
