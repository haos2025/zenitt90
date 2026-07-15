# Zenith OTT

Android TV / Mobile IPTV приложение.

## Быстрый старт

### GitHub Actions (рекомендуется)

1. Запушить репозиторий в GitHub
2. Создать PR в `main` — CI автоматически соберёт debug APK/AAB
3. Создать тег `v*.*.*` — CI соберёт release APK/AAB

### Локальная сборка

Требуется JDK 21 + Android SDK:

```bash
./gradlew assembleDebug
```

## Архитектура

```
app/src/main/java/com/platinum/ott/
├── core/           # ServiceLocator, preferences, JS engine, plugins
├── data/           # Room (local), Retrofit (remote), repositories
├── domain/         # Models, use cases, repository interfaces
├── presentation/   # Compose screens (TV + Phone)
├── navigation/     # NavHost
├── sync/           # Sync repository
├── ui/theme/       # Theme, colors, typography
└── worker/         # WorkManager (series updates, notifications)
```

### DI: ServiceLocator (не Hilt)

Осознанное решение — см. `ZENITH_OTT_HANDOFF.md` раздел 5.

### Стек

- Kotlin 2.1.20
- Jetpack Compose + TV Material3
- ExoPlayer (Media3) — HLS/DASH
- Room 2.7.1 — кэш
- Retrofit + OkHttp — сеть
- QuickJS — JS-движок для плагинов

## Версии

См. `gradle/libs.versions.toml` — все версии зафиксированы.

## Подпись APK

- **Debug:** `debug.keystore` в репозитории (пароль `android`)
- **Release:** через GitHub Secrets (`KEYSTORE_BASE64`, `KEY_ALIAS`, `KEY_PASSWORD`, `STORE_PASSWORD`)

Инструкция: `keystore.properties.example`

## CI/CD

- `build.yml` — проверка на каждый PR/push в main
- `release.yml` — сборка релиза при теге `v*.*.*`

Gradle ставится через `gradle/actions/setup-gradle@v4` (не зависит от `gradle-wrapper.jar`).

## Генератор

`generator.py` — собирает проект из исходных файлов.

```bash
python generator.py [output_dir]
```

Не содержит Kotlin-кода как строк — все `.kt` файлы хранятся как реальные файлы.

## Документация

- `ZENITH_OTT_HANDOFF.md` — история проекта, архитектурные решения, раунды 1-14
- `ZENITH_FIX_LOG.md` — журнал исправлений, сравнение версий, защита от ошибок ИИ

## История версий

| Версия | Файлов | Подход | Ошибок |
|---|---|---|---|
| v1.9 | ~50 | 10 Python-скриптов | ? |
| v2.0 | ~90 | 1 генератор | ~30 |
| v3.0 | 137 | 1 генератор (3800 строк) | ~70 |
| **v4.0** | **143** | **Реальные файлы + generator** | **0 escaping** |

Подробности: `ZENITH_FIX_LOG.md`
