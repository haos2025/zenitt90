# Zenith OTT — История чата (2026-07-15)

## Контекст

Пользователь: Shadow (Windows 10, GitHub Desktop)
ИИ: Mimo (xiaomi/mimo-v2.5-pro)
Задача: исправить ошибки сборки Zenith OTT

---

## Этап 1: Анализ ошибок buildConfigField

**Проблема:** `build.gradle.kts` не компилируется — `buildConfigField` с неправильным синтаксисом.

**Причина:** Python `\"` в triple-quote → `""` на выходе вместо `\"`.

**Фикс:** Изменено экранирование в строках 118, 120 скрипта.

---

## Этап 2: Несуществующие зависимости

**Проблема:** `leakcanary:android:2.14` и `zxing:core:4.3.0` не найдены.

**Фикс:**
- `leakcanary` артефакт: `android` → `leakcanary-android`
- `zxing` версия: разделена на `zxingCore = "3.5.4"` и `zxingEmbedded = "4.3.0"`

---

## Этап 3: Regex escaping

**Проблема:** `\s`, `\S` — unsupported escape sequences в Kotlin.

**Фикс:** `\\s` → `\\\\s` в Python (produces `\\s` in Kotlin → regex `\s`).

---

## Этап 4: String/quote escaping

**Проблема:** `\n` ломает строковые литералы, `"` без экранирования.

**Фикс:** Правильное экранирование `\\n`, `\\"`.

---

## Этап 5: Unresolved references

**Проблема:** `History`, `PluginViewModel`, `TextButton`, `LinearProgressIndicator` не найдены.

**Фикс:**
- `Icons.Default.History` → `Icons.Default.AccessTime`
- Добавлены импорты для `TextButton`, `LinearProgressIndicator`, `PluginViewModel`

---

## Этап 6: QuickJS API

**Проблема:** `JsFunction` не существует в QuickJS 0.9.2.

**Фикс:** Переписан `evaluateScript` на char-коды (`34.toChar()`, `92.toChar()`).

---

## Этап 7: Experimental API + SetupScreen

**Проблема:** Нет `@OptIn`, `Text()` positional arg mismatch.

**Фикс:** Добавлен `@OptIn(ExperimentalMaterial3Api::class)`, `style = TextStyle(...)`.

---

## Этап 8: Миграция на реальные файлы

**Решение:** Вместо Python-скрипта (3800 строк) → реальные `.kt` файлы + `generator.py` (187 строк).

**Результат:** 0 ошибок экранирования (невозможны).

---

## Этап 9: Восстановление потерянных файлов

**Проблема:** При миграции с v2 на v3 потеряны компоненты.

**Возвращено:**
- `COMPATIBILITY.md`, `MIGRATION_NOTES.md`, `README_SIGNING.md`
- `PlayerController.kt`, `QualityMenuOverlay.kt`, `CatalogRow.kt`

---

## Итого

| Показатель | Значение |
|---|---|
| Ошибок найдено | ~70 |
| Ошибок исправлено | ~70 |
| Раундов работы | 4 сборки |
| Финальный размер | 151 файл |
| Архитектура | Реальные файлы + generator |

---

## Следующие шаги

1. Push в GitHub через GitHub Desktop
2. Дождаться зелёной сборки в Actions
3. Если красная — читать лог, фиксить
4. Повторять пока не соберётся
