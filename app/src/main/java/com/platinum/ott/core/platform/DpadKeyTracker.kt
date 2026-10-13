package com.platinum.ott.core.platform

/**
 * Время последнего нажатия стрелки пульта (вверх/вниз/влево/вправо), uptimeMillis.
 * Пишется из MainActivity.dispatchKeyEvent. Нужен боковой панели ТВ (NavSidebar),
 * чтобы отличить «фокус пришёл в панель, потому что пользователь нажал стрелку»
 * от «система сама поставила начальный фокус на первый элемент экрана» —
 * во втором случае панель не раскрывается и передаёт фокус контенту.
 */
object DpadKeyTracker {
    @Volatile var lastDirectionalKeyMs: Long = 0L
}
