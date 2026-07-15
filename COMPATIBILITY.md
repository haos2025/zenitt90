# Матрица совместимости версий (проверено веб-поиском, июль 2026)

| Компонент | Версия в проекте | Проверено против |
|---|---|---|
| Android Gradle Plugin | 8.6.0 | Минимальная версия AGP для compileSdk 35 — именно 8.6.0 (developer.android.com) |
| Gradle Wrapper | 8.7 | Минимальная версия Gradle для AGP 8.6 — именно 8.7 |
| compileSdk / targetSdk | 35 | Максимальный уровень API, поддерживаемый AGP 8.6 |
| Kotlin | 1.9.24 | — |
| Compose Compiler extension | 1.5.14 | Официально таргетирован на Kotlin 1.9.24 (androidx release notes: "This compiler release is targeting Kotlin 1.9.24") |
| KSP | 1.9.24-1.0.20 | Формат версии KSP = `<версия Kotlin>-<версия KSP>` — префикс совпадает с Kotlin 1.9.24 |
| tv-foundation | 1.0.0 (stable) | Официальная страница androidx: `implementation("androidx.tv:tv-foundation:1.0.0")` |
| tv-material | 1.1.0 (stable) | Официальная страница androidx: `implementation("androidx.tv:tv-material:1.1.0")` (1.0.0 — первый stable-релиз, 1.1.0 — текущий) |

Все версии образуют документально подтверждённую совместимую связку — это не предположение,
а результат прямой сверки с официальными release notes Android Developers на момент сборки
проекта (июль 2026). Единственное, что физически нельзя проверить без реальной среды сборки —
это компиляцию конкретного кода проекта (см. MIGRATION_NOTES.md, раздел "нужно проверить вручную").
