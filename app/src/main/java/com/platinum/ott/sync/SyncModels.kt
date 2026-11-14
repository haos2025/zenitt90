package com.platinum.ott.sync

/** Итог одной категории (избранное / история) за один сеанс синхронизации. */
data class CategoryReport(
    val sent: Int = 0,      // отправлено на сервер (кроме удалений — они в deletedSent)
    val received: Int = 0,  // получено с сервера
    val added: Int = 0,     // из полученного: новых у нас
    val updated: Int = 0,   // из полученного: обновили существующее
    val deleted: Int = 0,   // из полученного: удалили по метке удаления
    val skipped: Int = 0,   // из полученного: у нас версия новее или то же самое
    val errors: Int = 0,    // не удалось применить/отправить
    val deletedSent: Int = 0 // отправлено меток удаления
)

/** Отчёт о синхронизации (показывается в «Мои устройства», хранятся последние 20). */
data class SyncReport(
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    /** "manual" | "auto" | "pairing" */
    val trigger: String = "manual",
    val ok: Boolean = true,
    val error: String? = null,
    val favorites: CategoryReport = CategoryReport(),
    val history: CategoryReport = CategoryReport(),
    /** Ответ сервера на отправку: принял новых / обновил / проигнорировал (устарели) / отклонил. */
    val serverAccepted: Int = 0,
    val serverUpdated: Int = 0,
    val serverIgnored: Int = 0,
    val serverRejected: Int = 0,
    /** Совпала ли контрольная сумма наших данных с серверной после синхронизации (null — не проверялось). */
    val checksumMatches: Boolean? = null,
    /** Предупреждения (например, ошибки формата, которые вернул сервер). */
    val notes: List<String> = emptyList()
)

data class DeviceInfo(
    val id: String,
    val name: String,
    val type: String,
    val createdAt: Long,
    val lastSeenAt: Long,
    val lastSyncAt: Long,
    val isCurrent: Boolean,
    /** Что устройство подтвердило после своей последней синхронизации (null — ещё не подтверждало). */
    val ackAt: Long?,
    val ackApplied: Map<String, Int>,
    val ackMatchesServer: Boolean?
)

data class RedeemResult(val inviterName: String, val merged: Map<String, Int>)
