# MathHammer для Android

Офлайн-калькулятор среднего урона: Kotlin, Jetpack Compose, Material 3.
Русский интерфейс, светлая и тёмная темы, автоматический пересчёт, сохранение ввода при пересоздании Activity.
Приложение не запрашивает разрешения и не использует сеть.

## Запуск

1. Откройте корень проекта в Android Studio с поддержкой AGP 8.9.2.
2. Выберите Gradle JDK 17 (или совместимый встроенный JDK).
3. Установите Android SDK Platform 35 через SDK Manager. Укажите SDK в Android Studio или в `local.properties`: `sdk.dir=C:/Users/USERNAME/AppData/Local/Android/Sdk`.
4. Дождитесь Gradle Sync и запустите `app` на устройстве/эмуляторе Android 8.0 (API 26) или новее.

Закреплены Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20, Compose BOM 2024.10.00 и Activity Compose 1.9.3.
Версия Material 3 (1.3.0) управляется BOM; отдельно её задавать не нужно.
Для menuAnchor используется API этой версии: MenuAnchorType.PrimaryNotEditable.
compileSdk/targetSdk = 35.
Первый Gradle Sync требует интернет для загрузки инструментов и зависимостей; установленному приложению интернет не нужен.

Из PowerShell при настроенных `JAVA_HOME` и Android SDK:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

Используйте JDK 17 или 21 для Gradle. Встроенный JDK 25 новых Android Studio
не подходит для закреплённой версии Gradle. В Android Studio выберите
Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK.
В этой среде уже настроен `C:/Users/Роман/.jdks/jbr-21.0.11`.
Для запуска из PowerShell можно задать его только на текущую сессию:

```powershell
$env:JAVA_HOME = 'C:\Users\Роман\.jdks\jbr-21.0.11'
.\gradlew.bat testDebugUnitTest assembleDebug
```

Linux/macOS: `sh gradlew testDebugUnitTest assembleDebug`.
APK: `app/build/outputs/apk/debug/app-debug.apk`.

## Как считать

Введите восемь характеристик. `A` — общее число одинаковых атакующих кубиков;
`BS/WS` — порог попадания; `S` — сила; `AP` — пробитие от −99 до 0;
`D` — фиксированный урон. Для цели задайте `T`, `Sv`, `Inv` (либо «Нет»).
Пустые и некорректные поля показывают ошибки; результат в этом случае — «—».
Одна атака — один атакующий кубик, ещё до проверки попадания.

```text
pHit = (7 − BS/WS) / 6
pWound = (7 − woundTarget) / 6
armorSaveTarget = Sv − AP
effectiveSaveTarget = min(armorSaveTarget, Inv), если Inv задан
pFailSave = clamp((effectiveSaveTarget − 1) / 6, 1/6, 1)
урон за атаку = pHit × pWound × pFailSave × D
суммарный урон = A × урон за атаку
```

Порог ранения: 2+ при S ≥ 2T; 3+ при S > T; 4+ при S = T;
5+ при 2S > T; иначе 6+. Используется один лучший сейв.
Промежуточные значения не округляются. Итог отображается с 2 знаками, урон за атаку с 3.

Стартовый пример: `A=12, BS/WS=3+, S=8, AP=-2, D=2, T=5, Sv=3+, Inv=4+`.
Результаты: **5,33** суммарно и **0,444** за атаку.
При A=0 суммарный урон равен нулю; гипотетический урон за атаку сохраняется.

Это потенциальный урон после сейвов по однородной цели. Калькулятор не считает снятые Wounds,
избыточный урон или вероятность уничтожения моделей. Особые способности, рероллы,
модификаторы, Cover, Feel No Pain, случайные A/D не входят в модель.

## Структура и проверки

- `app/src/main/java/ru/mathhammer/DamageCalculator.kt` — чистые Kotlin-модели и расчёт с проверкой диапазонов.
- `InputValidation.kt` — безопасный разбор числовых полей.
- `MainActivity.kt` — Compose-экран, состояние `rememberSaveable`, форматирование.
- `app/src/test/java/ru/mathhammer/DamageCalculatorTest.kt` — JVM/JUnit-тесты: контрольный пример, линейность, A=0, границы ранения, выбор сейва, диапазоны, точность и невалидный текст. Допуск Double — 1e-9.

Требования и источники игровой модели зафиксированы в `CODEX_ANDROID_MATHHAMMER.md`.

## Статус проверки в среде создания

На установленном JDK 21 и Android SDK 35 сборка APK и JVM-тесты проверены.
В `gradle.properties` используется `file.encoding=COMPAT`: это устраняет ошибку
`GradleWorkerMain` при кириллице в пути пользовательского Gradle-кэша Windows.
Исходники остаются в UTF-8. Проверка приложения на устройстве не выполнялась.
Gradle Wrapper загружен из официального репозитория; SHA-256 JAR проверен.
