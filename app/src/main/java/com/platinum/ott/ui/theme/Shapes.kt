package com.platinum.ott.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Shapes as TvShapes

// Подзадача 1 PROMPT_DESIGN_SYSTEM.md. До этого файла по проекту было
// 11 разных сырых значений RoundedCornerShape(Ndp) (3/4/6/8/9/10/12/16/
// 24/28dp + 50%/CircleShape), ни MaterialTheme, ни TvMaterialTheme не
// получали общий Shapes — каждое место в коде решало форму само по
// себе. Ниже — осознанно сведённая шкала из 4 значений, не
// один-в-один слепок старых чисел.
//
// ZenithShapeSmall  = 8dp  — карточки, чипы, поля ввода, мелкие поверхности
// ZenithShapeMedium = 12dp — диалоги, панели, крупные контейнеры
// ZenithShapeLarge  = 24dp — капсула плеера и другие "выделенные" поверхности
// ZenithShapePill   = 50%  — полностью скруглённые элементы (индикаторы,
//                            круглые кнопки, capsule-края)
//
// Карта старое → новое (решение принято здесь, применяется точечно в
// подзадаче 4 — этот файл её только фиксирует, чтобы не расходиться на
// разных сессиях):
//   3dp  (EpgGridScreen.kt, 6dp-точка live-индикатора)     → Pill
//        (сама фигура и так уже полностью круглая: 3dp = половина
//        6dp-бокса; RoundedCornerShape(3.dp) там по сути CircleShape,
//        стоит заменить прямо на ZenithShapePill/CircleShape)
//   4dp, 6dp, 8dp                                          → Small
//   9dp, 10dp  (кнопки-капсулы плеера: PlayerController.kt,
//               PlaybackMenuOverlay.kt, PhonePlayerController.kt)  → Small
//   12dp                                                    → Medium
//   16dp  (PlayerScreen.kt, контейнеры вокруг капсулы)      → Medium
//   24dp, 28dp (внешняя капсула плеера и её border)          → Large
//   50% / CircleShape (6 мест, уже круглые)                  → Pill
//
// Как и предупреждал сам промт про shimmer в SkeletonLoader.kt для
// анимаций — здесь аналогичных исключений нет, все 11 значений выше
// имеют осмысленное место в этой шкале.

val ZenithShapeSmall = RoundedCornerShape(8.dp)
val ZenithShapeMedium = RoundedCornerShape(12.dp)
val ZenithShapeLarge = RoundedCornerShape(24.dp)
val ZenithShapePill = RoundedCornerShape(50)

/**
 * Набор форм для androidx.compose.material3 (используется ZenithTheme —
 * телефон). extraSmall/small совпадают намеренно: у проекта нет
 * отдельного варианта "ещё мельче, чем Small" — сведение к 4 значениям,
 * а не к полным 5 слотам M3.
 */
val ZenithShapes = Shapes(
    extraSmall = ZenithShapeSmall,
    small = ZenithShapeSmall,
    medium = ZenithShapeMedium,
    large = ZenithShapeLarge,
    extraLarge = ZenithShapePill,
)

/**
 * То же самое для androidx.tv.material3 (используется ZenithTvTheme —
 * TV). Тип androidx.tv.material3.Shapes подтверждён по актуальной
 * документации (доступен с tv-material 1.0.0, в проекте — 1.1.0):
 * та же 5-слотовая система extraSmall/small/medium/large/extraLarge.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
val ZenithTvShapes = TvShapes(
    extraSmall = ZenithShapeSmall,
    small = ZenithShapeSmall,
    medium = ZenithShapeMedium,
    large = ZenithShapeLarge,
    extraLarge = ZenithShapePill,
)
