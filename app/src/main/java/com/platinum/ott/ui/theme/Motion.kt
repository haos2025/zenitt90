package com.platinum.ott.ui.theme

import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearOutSlowInEasing

// Подзадача 2 PROMPT_DESIGN_SYSTEM.md. До этого файла в presentation/
// анимации встречались всего в 4 местах, и только в плеере:
//   - PlayerController.kt:302-304 (TV) и
//     PhonePlayerController.kt:421-423 (телефон) — рост прогресс-бара
//     4dp→10dp при перемотке, оба места буквально дублируют
//     tween(durationMillis = 150, easing = LinearOutSlowInEasing)
//   - 5× AnimatedVisibility(enter = fadeIn(), exit = fadeOut()) без явного
//     animationSpec (капсула плеера/тосты сика/буфер зага на TV и
//     телефоне) — то есть неявно используют дефолт Compose (300ms,
//     FastOutSlowInEasing) россыпью, а не общий токен
// Ниже — то же самое, но именованными константами, чтобы дальнейшие
// новые переходы ссылались на них, а не заводили свои магические числа.
//
// SkeletonLoader.kt (шиммер, tween(1100, LinearEasing) в бесконечном
// повторе) сюда сознательно НЕ включён — как и предупреждал сам промт,
// это не типовой переход, а самостоятельный эффект с другим смыслом
// (бесконечный луп, не короткий/средний переход по состоянию), сведение
// его к этим токенам ничего не даёт и не было сделано.

/** 150ms — короткие точечные переходы (рост/скрытие мелких элементов
 *  вроде толщины прогресс-бара при перемотке). Сейчас соответствует
 *  двум местам выше (TV + телефон) — при переносе на них (отдельная
 *  задача, не эта сессия) оба должны ссылаться на эту константу. */
const val ZenithDurationShort = 150

/** 300ms — переходы уровня экрана/оверлея (появление-исчезновение
 *  капсулы плеера, тостов и т.п.). Совпадает с дефолтной длительностью
 *  fadeIn()/fadeOut() в Compose — существующие 5 AnimatedVisibility
 *  используют её неявно; эта константа делает то же значение явным для
 *  новых мест, а не фиксирует новое поведение. */
const val ZenithDurationMedium = 300

/** Единая easing-кривая для переходов выше. LinearOutSlowInEasing уже
 *  используется в обоих местах durationShort — принята как стандарт,
 *  а не FastOutSlowInEasing (дефолт fadeIn/fadeOut для durationMedium),
 *  чтобы у всей системы анимаций проекта была одна кривая, а не две
 *  разные по типу перехода. */
val ZenithEasingStandard: Easing = LinearOutSlowInEasing
