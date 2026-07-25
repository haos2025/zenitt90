# Zenith OTT — Журнал исправлений (сессия 2026-07-15)

Контекст: продолжение работы над `setup_zenith_v3_final.py` (ветка Mimo, 137 файлов).
Скрипт-генератор создаёт весь Android TV/мобильный IPTV-проект одним запуском.

---

## Что было сделано

Скачаны логи двух сборок GitHub Actions (`build-debug`), проанализированы ошибки,
исправлен скрипт-генератор `setup_zenith_v3_final.py` → `setup_zenith_v3_final_fixed.py`.

---

## Найденные и исправленные ошибки

### Ошибка 1: Python-экранирование в `buildConfigField` (сборка упала)

**Лог:**
```
e: file:///.../app/build.gradle.kts:28:52: Expecting ')'
e: Too many arguments for buildConfigField(type, name, value)
```

**Причина:** Python `\"` внутри тройной кавычки `"""..."""` превращается в `"` на выходе.
Вместо корректного Kotlin `"\"value\""` генерировалось `""value""` — четыре отдельных токена.

**Затронутые строки в скрипте:**
- Строка 118: `buildConfigField("String", "GIT_COMMIT", "\"${...}\"")`
- Строка 120: `buildConfigField("String", "TMDB_API_KEY", "\"${tmdbKeyValue}\"")`

**Фикс:** `"\"` → `"\\"` в Python-источнике, чтобы на выходе получалось `\"`.

---

### Ошибка 2: Python-экранирование в `removeSurrounding` (не добралось до компиляции)

**Причина:** Та же — `\"` в Python triple-quote → `"` на выходе → `"""` в Kotlin (raw string delimiter = синтаксическая ошибка).

**Затронутые строки:**
- Строка 508: `removeSurrounding("\"")` в `PluginManifest.kt`
- Строка 513: `removeSurrounding("\"")` в `PluginManifest.kt`

**Фикс:** `"\"` → `"\\"`.

---

### Ошибка 3: Python-экранирование в `replace` (не добралось до компиляции)

**Причина:** `\\` в Python triple-quote → `\` на выходе, и следующая `"` создавала невалидный Kotlin-сайтинг.

**Затронутые строки:**
- Строка 762: `replace("\\", "\\\\")` + `replace("'", "\\'")` + `replace("\n", "\\n")` + `replace("\r", "\\r")`
- Строка 770: `replace("\\", "\\\\")` + `replace("'", "\\'")`

**Фикс:** Удвоено экранирование: `\\` → `\\\\`, `\\'` → `\\\\'`, `\\n` → `\\\\n`, `\\r` → `\\\\r`.

---

### Ошибка 4: Неправильный артефакт LeakCanary (сборка упала)

**Лог:**
```
Could not find com.squareup.leakcanary:android:2.14
```

**Причина:** В `libs.versions.toml` артефакт указан как `android`, а правильное имя — `leakcanary-android`.

**Затронутая строка:** ~3733 в скрипте.

**Фикс:** `name = "android"` → `name = "leakcanary-android"`.

---

### Ошибка 5: Несуществующая версия ZXing core (сборка упала)

**Лог:**
```
Could not find com.google.zxing:core:4.3.0
```

**Причина:** `com.google.zxing:core` и `com.journeyapps:zxing-android-embedded` — разные библиотеки с разными схемам версий. Core latest — 3.5.4, embedded latest — 4.3.0. Скрипт использовал одну переменную `zxing = "4.3.0"` для обеих.

**Фикс:** Разделено на две переменные:
- `zxingCore = "3.5.4"` для `com.google.zxing:core`
- `zxingEmbedded = "4.3.0"` для `com.journeyapps:zxing-android-embedded`

---

## Что было проверено и подтверждено рабочим (не требует фикса)

| Проверка | Статус |
|---|---|
| Все 24 зависимости существуют в Maven Central / Google Maven | ✅ |
| AGP 8.6.0, Kotlin 2.1.20, KSP 2.1.20-2.0.1, Room 2.7.1 | ✅ |
| DAO DELETE/UPDATE методы с явным `: Int` (11 штук) | ✅ |
| `providers.exec` / `providers.environmentVariable` (не `project.exec` / `System.getenv`) | ✅ |
| `ClickableSurfaceDefaults` (не `SurfaceDefaults`) | ✅ |
| `assembleRelease` с флагами подписи в `release.yml` | ✅ |
| Balanced braces во всех `.kt`/`.kts` | ✅ |
| Triple quotes — чётное количество | ✅ |
| Все импорты в `ZenithNavHost.kt` (19 экранов) | ✅ |
| Все ключевые файлы существуют | ✅ |
| Gradle wrapper 8.7 | ✅ |

---

## Паттерн ошибок (для следующего раза)

### Категория A: Python-экранирование в triple-quoted strings

**Правило:** В Python `"""..."""` последовательность `\"` превращается в `"` на выходе.
Чтобы получить `\"` в выходном файле, нужно писать `"\\"`.

**Где искать:** Всё внутри `project_files = { ... }` словаря в `setup_zenith_v3_final.py`.
Особенно подозрительны:
- `buildConfigField(...)` — третий аргумент с кавычками
- `removeSurrounding("\"")` — экранированная кавычка
- `replace("\\", "\\\\")` — обратные слэши
- Любая строка с `\"` или `\\` внутри Kotlin-кода

**Как проверять:** Запустить `python3 setup_zenith_v3_final.py` в пустой папке,
затем `grep -n` по сгенерированным `.kt`/`.kts` файлам на предмет `""`, невалидных строк,
отсутствующих `\"`.

### Категория B: Несуществующие зависимости

**Правило:** Не доверять версиям из тренировочных данных. Всегда проверять через
Maven Central API или GitHub tags.

**Как проверять:**
```bash
# Maven Central
curl -s -o /dev/null -w "%{http_code}" "https://repo.maven.apache.org/maven2/{group/path}/{artifact}/{version}/{artifact}-{version}.pom"

# Google Maven (AndroidX)
curl -s -o /dev/null -w "%{http_code}" "https://dl.google.com/dl/android/maven2/{group/path}/{artifact}/{version}/{artifact}-{version}.pom"

# GitHub tags
curl -s "https://api.github.com/repos/{owner}/{repo}/tags?per_page=5" | grep '"name"'
```

**Подвох:** У разных библиотек могут быть разные схемы версий (core vs embedded).
Если одна `version.ref` используется для двух разных артефактов — проверить ОБА.

### Категория C: Configuration-cache несовместимые вызовы

Уже исправлено в предыдущих раундах, но стоит перепроверять:
- `project.exec` → `providers.exec`
- `System.getenv()` → `providers.environmentVariable()`

---

## Файлы

- `setup_zenith_v3_final_fixed.py` — исправленный скрипт-генератор (5 фиксов)
- `ZENITH_OTT_HANDOFF.md` — исходный сводный документ (раунды 1–14)
- `ZENITH_FIX_LOG.md` — этот файл

---

## Если сборка снова упала

1. Скачай лог `0_build-debug.txt`
2. `grep -A20 "What went wrong" 0_build-debug.txt` — ищи точную ошибку
3. Если `Could not find` — проверь версию/артефакт через Maven/Google API
4. Если `Expecting ')'` / `Too many arguments` — проверь Python-экранирование
5. Если `Unresolved reference` — проверь импорт и существование файла/класса
6. Если `KSP2 bug` — проверь что DAO DELETE/UPDATE имеют `: Int`
7. Если `Unsupported escape sequence` — проверь regex-паттерны (`\s` → `\\\\s` в Python)
8. Если `Syntax error: Expecting '"'` — проверь `\n` в строковых литералах
9. После фикса — запусти скрипт локально, проверь сгенерированные файлы

---

## Дополнительные фиксы (сессия 2026-07-15, второй раунд)

### Ошибка 6: Regex escape sequences (9 ошибок компиляции)

**Лог:** `Unsupported escape sequence` в PluginManifest.kt

**Причина:** `\s`, `\S`, `\[`, `\]` в Python triple-quote → `\s`, `\S`, `\[`, `\]` на выходе.
Kotlin не поддерживает `\s` как escape-последовательность (только `\t`, `\b`, `\n`, `\r`, `\"`, `\\`, `\'`, `\$`).

**Фикс:** `\\s` → `\\\\s` в Python (produces `\\s` in Kotlin → regex `\s`).

### Ошибка 7: Newline в строковом литерале (4 ошибки компиляции)

**Лог:** `Syntax error: Expecting '"'` в ZenithApplication.kt

**Причина:** `\n` в Python triple-quote → реальный перенос строки → ломает Kotlin-строку.

**Фикс:** `\n` → `\\n` в Python (produces `\n` in Kotlin).

### Ошибка 8: Unescaped quotes в строке (4 ошибки компиляции)

**Лог:** `Unresolved reference 'value'` в PluginManager.kt

**Причина:** `'{"value":""}'` в Python → `'{'"value":""}'` в Kotlin → `"` ломает строку.

**Фикс:** `'{"value":""}'` → `'{\\"value\\":\\"\\"}'` в Python.

### Ошибка 9: Unresolved 'History' (12 ошибок компиляции)

**Лог:** `Unresolved reference 'History'` в 6 phone-экранах

**Причина:** `Icons.Default.History` требует `material-icons-extended`.

**Фикс:** Заменён на `Icons.Default.AccessTime` (доступен в базовом пакете).

### Ошибка 10: Unresolved 'TextButton' / 'LinearProgressIndicator' (6 ошибок)

**Лог:** `Unresolved reference 'TextButton'` в HomeScreen, `LinearProgressIndicator` в HistoryScreen

**Причина:** Экраны импортируют `androidx.tv.material3.*`, но эти компоненты в `androidx.compose.material3`.

**Фикс:** Добавлены явные импорты `androidx.compose.material3.TextButton` и `androidx.compose.material3.LinearProgressIndicator`.

### Ошибка 11: Unresolved 'PluginViewModel' (9 ошибок)

**Лог:** `Unresolved reference 'PluginViewModel'` в PhonePluginCatalogScreen

**Причина:** Phone-экраны не импортируют ViewModel.

**Фикс:** Добавлен `import com.platinum.ott.presentation.screens.plugins.PluginViewModel`.

### Ошибка 12: Private 'sharedClient' access

**Лог:** `Cannot access 'val sharedClient': it is private`

**Фикс:** `private val sharedClient` → `internal val sharedClient`.

---

## Дополнительные фиксы (сессия 2026-07-15, третий раунд)

### Ошибка 13: ScriptProvider — QuickJS API (2 ошибки)

**Лог:** `Unresolved reference 'JsFunction'`, `Unresolved reference 'call'`

**Причина:** QuickJS 0.9.2 не имеет класса `JsFunction`. API изменился.

**Фикс:** Полностью переписан `evaluateScript` — использует char-коды (`34.toChar()` для кавычки, `92.toChar()` для обратного слэша) вместо строковых литералов, чтобы избежать проблем с экранированием.

### Ошибка 14: Experimental Material3 API (6 ошибок)

**Лог:** `This material API is experimental` в PhonePluginCatalogScreen, PhonePluginDetailScreen

**Причина:** `Scaffold`, `TopAppBar`, `TabRow` в `androidx.compose.material3` — экспериментальные API.

**Фикс:** Добавлен `@OptIn(ExperimentalMaterial3Api::class)` на все 8 phone-экранов.

### Ошибка 15: SetupScreen Text() candidate mismatch (1 ошибка)

**Лог:** `None of the following candidates is applicable: fun Text(text: String, modifier: Modifier, ...)`

**Причина:** `Text(placeholder, TextStyle(...))` — второй позиционный аргумент это `modifier`, не `style`.

**Фикс:** `Text(placeholder, TextStyle(...))` → `Text(placeholder, style = TextStyle(...))`.

---

## Итого: все ошибки исправлены

| Категория | Кол-во ошибок | Статус |
|---|---|---|
| Python-экранирование (buildConfigField, removeSurrounding, replace) | 8 | ✅ |
| Несуществующие зависимости (leakcanary, zxing) | 3 | ✅ |
| Regex escape sequences (\s, \S) | 9 | ✅ |
| String/quote escaping (\n, \") | 8 | ✅ |
| Unresolved references (History, PluginViewModel, TextButton, etc.) | 25+ | ✅ |
| QuickJS API (JsFunction) | 2 | ✅ |
| Experimental API warnings | 6 | ✅ |
| SetupScreen Text() mismatch | 1 | ✅ |

---

## Почему возникают ошибки ИИ

Все ошибки сводятся к **3 корневым причинам**:

### Причина 1: Python-экранирование в тройных кавычках

Скрипт-генератор хранит весь Kotlin-код в Python `"""..."""`. Проблема:

```
Python """...""" обрабатывает escape-последовательности:
  \"  →  "     (quote)
  \\  →  \     (backslash)
  \n  →  [newline]

Но Kotlin тоже имеет свои escape-последовательности:
  \"  →  "     (quote)  
  \\  →  \     (backslash)
  \n  →  [newline]
  \s  →  ОШИБКА (нет такого в Kotlin!)
```

Когда Python встречает `\"` в тройной кавычке, он превращает это в `"` на выходе. Но нам нужно `\"` в Kotlin-файле (escaped quote). Получается двойное экранирование: Python → файл → Kotlin, и каждое преобразование теряет один уровень слэшей.

**Почему не было caught раньше:** скрипт генерировал файлы, но они никогда не компилировались — нет Android SDK в среде разработки.

### Причина 2: Выдуманные версии и API

ИИ-модели генерируют код на основе тренировочных данных, которые:
- Имеют cutoff date — не знают актуальных версий
- Галлюцинируют версии (zxing 4.3.0 не существует, leakcanary артефакт `android` вместо `leakcanary-android`)
- Выдумывают API (`JsFunction` в QuickJS, `Icons.Default.History` в базовом пакете)

**Почему не было caught раньше:** зависимости резолвились бы при первой сборке, но до этого не доходило.

### Причина 3: Смешение TV и Phone API

Проект имеет TV-экраны (`androidx.tv.material3.*`) и Phone-экраны (`androidx.compose.material3.*`). ИИ генерирует phone-экраны копируя паттерны из TV-экранов, но:
- `Tab` в TV требует `onFocus` параметр (в phone — нет)
- `TextButton` есть в `compose.material3` но не в `tv.material3`
- `BasicTextField` имеет другой порядок параметров
- `Scaffold`, `TopAppBar` — экспериментальные в `compose.material3`

**Почему не было caught раньше:** phone-экраны добавлены в v3 (Mimo), а TV-экраны проверены 14 раундами.

---

## Как защитить проект от ошибок ИИ

### 1. Компиляция как единственный источник правды

```
❌ ИИ сказал "✅ 0 ошибок"  →  НЕ верить
✅ GitHub Actions собрался   →  Верить
```

**Правило:** ни одна строчка кода не считается рабочей, пока не скомпилировалась. Не важно, сколько ИИ аудиторов её проверили.

### 2. Инкрементальная сборка

Не генерировать 137 файлов за раз. Вместо этого:

```
1. Сгенерировал 5 файлов → собрал → починил
2. Добавил ещё 5 → собрал → починил
3. Повторять
```

Каждая ошибка ловится сразу, а не копится 14 раундов.

### 3. CI-валидация на каждый PR

```yaml
# build.yml — уже есть, но добавить:
on:
  pull_request:
    branches: ["main"]
  push:
    branches: ["main"]
```

**Правило:** ни один PR не мержится без зелёной сборки. Shadow уже настроил это.

### 4. Lock-файл для зависимостей

```toml
# libs.versions.toml — версии зафиксированы
# Но Dependabot может сломать (раунд 11!)
```

**Решение:** группировка Dependabot (уже настроена). Плюс — ручная проверка каждого PR от Dependabot.

### 5. Smoke-тест для скрипта-генератора

Добавить в CI шаг, который запускает сам скрипт-генератор и проверяет вывод:

```yaml
- name: Verify generator
  run: |
    python setup_zenith_v3_final_fixed.py
    # Проверить что ключевые файлы существуют
    test -f app/build.gradle.kts
    test -f app/src/main/java/com/platinum/ott/ZenithApplication.kt
    # Проверить что нет сломанного экранирования
    ! grep -r '"""' app/src/ --include="*.kt"
    ! grep -r '""' app/src/ --include="*.kt" | grep -v '= ""'
```

### 6. Запрет на генерацию "вслепую"

Для каждого ИИ-ассистента, работающего с проектом:

```
ПЕРЕД генерацией кода:
  1. Прочитать ZENITH_OTT_HANDOFF.md
  2. Прочитать ZENITH_FIX_LOG.md  
  3. Понять ПОЧЕМУ каждое решение принято

ПОСЛЕ генерации:
  1. Запустить скрипт локально
  2. Проверить сгенерированные файлы grep-ом
  3. НЕ писать "✅ готово" без компиляции
```

### 7. Тест на экранирование

Автоматический тест, который проверяет Python-генератор:

```python
# test_generator.py
import ast, subprocess, tempfile, os

def test_no_broken_escaping():
    """Проверяет что сгенерированные .kt файлы не содержат сломанных escape-последовательностей"""
    with tempfile.TemporaryDirectory() as d:
        os.chdir(d)
        exec(open('setup_zenith_v3_final_fixed.py').read())
        
        for root, dirs, files in os.walk('app/src'):
            for f in files:
                if f.endswith('.kt') or f.endswith('.kts'):
                    path = os.path.join(root, f)
                    content = open(path).read()
                    
                    # Проверить баланс скобок
                    assert content.count('{') == content.count('}'), f"Brace mismatch in {path}"
                    
                    # Проверить triple quotes
                    assert content.count('"""') % 2 == 0, f"Odd triple-quotes in {path}"
                    
                    # Проверить что нет unsupported escape sequences
                    # (упрощённая проверка)
```

### 8. Меньше файлов = меньше ошибок

137 файлов — это слишком для одного скрипта-генератора. Разбить на модули:

```
generate_core.py        # 20 файлов — базовая инфраструктура
generate_screens_tv.py  # 30 файлов — TV-экраны
generate_screens_phone.py  # 30 файлов — Phone-экраны
generate_plugins.py     # 20 файлов — система плагинов
generate_ci.py          # 10 файлов — CI/CD
```

Каждый модуль генерируется и тестируется отдельно.

---

## Главное правило

> ИИ — инструмент, не оракул. Каждую строчку кода от ИИ нужно проверять так же, как код от стажёра: компилируй, тестируй, не верь на слово.

---

## Перепроектирование проекта для fewer ошибок

### Проблема текущей архитектуры

```
setup_zenith_v3_final_fixed.py (209KB, 3800 строк)
  └── project_files = { ... }  — один гигантский dict с 137 файлами
       └── Всё внутри Python """...""" тройных кавычек
            └── Kotlin-код с escape-последовательностями
                 └── Которые ломаются при двойном экранировании
```

**Корень:** один файл = одна точка отказа. Ошибка в строке 2000 ломает весь проект.

### Новая архитектура

```
zenith/
├── generator.py              # Оркестратор (~100 строк)
├── templates/
│   ├── build.gradle.kts.j2   # Jinja2 шаблоны
│   ├── AndroidManifest.xml.j2
│   └── ...
├── src/
│   ├── core/                  # Реальный Kotlin-код
│   │   ├── ServiceLocator.kt
│   │   └── ...
│   ├── screens/
│   │   ├── home/HomeScreen.kt
│   │   └── ...
│   └── ...
├── gradle/
│   └── libs.versions.toml
└── .github/
    └── workflows/
        ├── build.yml
        └── release.yml
```

**Ключевое изменение:** Kotlin-код хранится как **реальные `.kt` файлы**, а не как строки внутри Python.

### Как это работает

```python
# generator.py — маленький, простой, без escaping
import shutil, os

def generate(output_dir="."):
    # 1. Копировать исходники
    shutil.copytree("src", f"{output_dir}/app/src")
    
    # 2. Скопировать Gradle файлы
    shutil.copy("gradle/libs.versions.toml", f"{output_dir}/gradle/")
    shutil.copy("build.gradle.kts", output_dir)
    
    # 3. Сгенерировать только то что нужно генерировать
    generate_keystore(output_dir)
    generate_ci(output_dir)
    download_gradle_wrapper(output_dir)
    
    # 4. Git init
    os.system(f"cd {output_dir} && git init && git add . && git commit -m 'init'")
```

### Преимущества

| Старый подход | Новый подход |
|---|---|
| Kotlin-код в Python-строках | Kotlin-код в `.kt` файлах |
| Escape-地狱 (`\\\\\\\\s`) | Никакого экранирования |
| 1 файл на 3800 строк | Маленькие модульные файлы |
| Ошибка → весь проект сломан | Ошибка → один файл сломан |
| Нельзя открыть в Android Studio | Можно открыть и проверить |
| Нельзя использовать линтер | Можно использовать detekt/ktlint |
| Diff показывает изменение строки | Diff показывает изменение файла |

### Миграция пошагово

**Шаг 1:** Извлечь существующий код
```bash
python setup_zenith_v3_final_fixed.py
mv app/src/ zenith/src/
mv gradle/ zenith/gradle/
mv .github/ zenith/.github/
```

**Шаг 2:** Создать generator.py
```python
# Простой скрипт который собирает проект из файлов
# Без тройных кавычек, без escaping
```

**Шаг 3:** Шаблоны для генерируемых частей
```
# Только то что МЕНЯЕТСЯ генерируется:
# - keystore.properties (из env)
# - versionCode (из GitHub Run Number)
# - build.yml (из конфига)
```

**Шаг 4:** Проверка в CI
```yaml
- name: Generate and build
  run: |
    python generator.py
    gradle assembleDebug
```

### Что генерировать, а что хранить как файлы

| Хранить как файлы | Генерировать |
|---|---|
| Все `.kt` файлы | `keystore.properties` |
| `libs.versions.toml` | `versionCode` / `versionName` |
| `build.gradle.kts` | `build.yml` / `release.yml` |
| `AndroidManifest.xml` | `debug.keystore` (из base64) |
| XML ресурсы | `gradle-wrapper.jar` (скачать) |
| Тесты | |

### Итог

```
Было:  Python-скрипт (3800 строк) → 137 файлов → ошибки экранирования
Стало: Реальные файлы + маленький generator (100 строк) → 0 ошибок экранирования
```

**Главный принцип:** код, который должен компилироваться, не должен быть строкой внутри другого языка.

---

## Сравнение всех версий проекта

### Версия 1: v1.9 (10 Python-скриптов)

```
Файлов: ~50
Архитектура: всё в 10 отдельных .py скриптах
Компиляция: никогда
```

**Ошибки:** неизвестно — код никогда не собирался.

### Версия 2: v2.0 (PowerShell + слияние)

```
Файлов: ~90
Архитектура: 1 Python-скрипт-генератор
Компиляция: первая в GitHub Actions
```

**Ошибки (раунды 1-14):**

| Раунд | Ошибка | Тип |
|---|---|---|
| 1 | Базовое слияние 10 скриптов | — |
| 2 | Нет AAB, плоская adaptive-icon | Функционал |
| 3 | Нет Version Catalog | Инфраструктура |
| 4 | CI зависел от setup-gradle | Инфраструктура |
| 5 | Нет committed wrapper jar | Инфраструктура |
| 6 | Мёртвая ссылка на wrapper jar | URL |
| 6 | `SurfaceDefaults` вместо `ClickableSurfaceDefaults` (6 файлов) | API |
| 7 | Мусорный файл от debug keystore | Мусор |
| 8 | Script молча коммитил без jar | Логика |
| 9 | Повтор раунда 6 | — |
| 10 | CI не должен падать из-за jar | Архитектура |
| 11 | `project.exec` в configuration-cache (Dependabot сломал Kotlin) | Compatibility |
| 12 | `System.getenv()` в configuration-cache (6 мест) | Compatibility |
| 13 | Kotlin 1.9.24 не читает tv-material bytecode | Версии |
| 14 | Room 2.6.1 + KSP2 bug (`suspend fun` → `Unit`) | Версии |

**Итого v2.0:** 14 раундов, ~30 ошибок, все исправлены.

### Версия 3: v3.0 (Mimo, 137 файлов)

```
Файлов: 137
Архитектура: 1 Python-скрипт-генератор (3800 строк)
Компиляция: GitHub Actions
```

**Ошибки (сборка 1 — dependency resolution):**

| # | Ошибка | Причина |
|---|---|---|
| 1 | `buildConfigField` синтаксис | Python `\"` → `""` в Kotlin |
| 2 | `leakcanary:android:2.14` не найден | Неправильный артефакт (нужен `leakcanary-android`) |
| 3 | `zxing:core:4.3.0` не найден | Несуществующая версия (latest 3.5.4) |

**Ошибки (сборка 2 — Kotlin compilation):**

| # | Ошибка | Причина |
|---|---|---|
| 4 | `\s`, `\S` unsupported escape (9 ошибок) | Python → Kotlin regex escaping |
| 5 | `\n` ломает строковый литерал (4 ошибки) | Python → Kotlin string escaping |
| 6 | `"` без экранирования в `_zenithCall` (4 ошибки) | Python → Kotlin quote escaping |
| 7 | `Icons.Default.History` unresolved (12 ошибок) | Нет в `material-icons-core` |
| 8 | `TextButton` / `LinearProgressIndicator` unresolved (6 ошибок) | Неправильный import |
| 9 | `PluginViewModel` unresolved (9 ошибок) | Нет импорта в phone-экранах |
| 10 | `sharedClient` private access | Неправильный модификатор |
| 11 | TV `Tab` без `onFocus` (3 ошибки) | Разный API TV/Phone |
| 12 | `PluginRepository` unresolved | Нет импорта |
| 13 | Type inference в PhoneDetailScreen | Неявный тип лямбды |
| 14 | ScriptProvider `JsFunction` (2 ошибки) | Выдуманный API QuickJS |
| 15 | Experimental Material3 API (6 ошибок) | Нет `@OptIn` |
| 16 | `Text()` positional arg mismatch | Неправильный порядок параметров |

**Итого v3.0 (скрипт):** 16 типов ошибок, ~70 ошибок компиляции.

### Версия 4: v4.0 (миграция на реальные файлы) — ТЕКУЩАЯ

```
Файлов: 143 (109 .kt + остальные конфиги)
Архитектура: реальные .kt файлы + generator.py (180 строк)
Компиляция: GitHub Actions (пока не проверена)
```

**Ошибки экранирования:** 0 (невозможны — нет Python-строк с Kotlin-кодом)

**Остаточный риск:** только реальные кодовые ошибки (wrong imports, missing params) — которые видны в IDE и ловятся линтером.

---

## Сводная таблица

| Версия | Файлов | Подход | Ошибок | Раундов |
|---|---|---|---|---|
| v1.9 | ~50 | 10 скриптов | ? | 0 |
| v2.0 | ~90 | 1 генератор | ~30 | 14 |
| v3.0 | 137 | 1 генератор (3800 строк) | ~70 | 4 сборки |
| **v4.0** | **143** | **Реальные файлы + generator (180 строк)** | **0 escaping** | **—** |

---

## Главный урок

```
v2.0:  14 раундов × ИИ-аудит → 30 ошибок → 14 раундов фиксов
v3.0:  ИИ сказал "✅ готово" → 70 ошибок → 4 сборки фиксов
v4.0:  Реальные файлы → 0 ошибок экранирования → компиляция проверяет код
```

**Каждая версия, использующая Python-строки для хранения Kotlin-кода, имела ошибки экранирования. Версия с реальными файлами — не имеет их в принципе.**
