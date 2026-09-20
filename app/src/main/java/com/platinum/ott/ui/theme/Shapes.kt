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
//
// РЕАЛЬНЫЙ БАГ (найден по репорту с телефона, после того как это уже
// какое-то время было в проде): extraLarge ниже был = ZenithShapePill
// (50%) — это сломало ВСЕ AlertDialog и ModalBottomSheet на телефоне
// (PhonePlayerController.kt: "Ещё настройки", диалог субтитров и т.д.),
// хотя ни один из них не задаёт свой shape явно. Material3's
// AlertDialog/ModalBottomSheet по умолчанию берут форму из
// MaterialTheme.shapes.extraLarge (AlertDialogDefaults.shape/
// BottomSheetDefaults.ExpandedShape) — а 50%-скругление на ШИРОКОМ на
// весь экран диалоге превращается в гигантский почти круглый нарост,
// обрезающий текст ("отитры" вместо "Субтитры", "Закрыт" вместо
// "Закрыть") — ровно то, что видно на скриншотах. TV этой проблемы не
// коснулось не потому, что там код лучше, а потому что TV-диалоги
// (PlaybackMenuOverlay.kt) — свои Surface с явным shape, никогда не
// читают TvMaterialTheme.shapes.extraLarge неявно.
//
// extraLarge переопределён ниже на ZenithShapeLarge (24dp, близко к
// недефолтному значению самого Material3, ~28dp) — обычный диалог
// теперь снова выглядит как диалог. Явные обращения к ZenithShapePill
// напрямую (прогресс-бары капсулы плеера и т.п.) эту правку не
// затрагивают вообще — они ссылаются на константу напрямую, а не через
// Shapes.extraLarge.

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
    // Было ZenithShapePill (50%) — реальный баг, см. комментарий выше:
    // ломало AlertDialog/ModalBottomSheet на телефоне (оба берут форму
    // из этого слота по умолчанию, если не задают свою явно).
    extraLarge = ZenithShapeLarge,
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
    // Тот же фикс, что и в ZenithShapes выше — TV сейчас этим багом не
    // задето (свои Surface с явным shape), но оставлять здесь Pill в
    // этом слоте — заведомо неверно на будущее, тот же риск для любого
    // TV-компонента, который когда-нибудь неявно возьмёт extraLarge.
    extraLarge = ZenithShapeLarge,
)
