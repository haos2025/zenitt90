package com.platinum.ott.data.repository

import com.platinum.ott.data.local.dao.EpgProgramDao
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.data.playlist.XtreamEpgProgram
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * PROMPT_EPG.md, подзадача 1 — тонкая обёртка над EpgProgramDao, тот же
 * принцип, что и ChannelRepository/ChannelMatchingRepository рядом:
 * репозиторий сам не парсит XMLTV/Xtream (это подзадачи 2-3, будут
 * записывать сюда через replaceProgramsForChannel()) — здесь только
 * хранение/чтение уже готовых программ и фоновая чистка окна.
 */
class EpgProgramRepository(
    private val epgProgramDao: EpgProgramDao
) {
    suspend fun getForChannelInRange(channelId: String, fromMillis: Long, toMillis: Long): List<EpgProgramEntity> =
        withContext(Dispatchers.IO) { epgProgramDao.getForChannelInRange(channelId, fromMillis, toMillis) }

    suspend fun getCurrentForChannel(channelId: String, nowMillis: Long = System.currentTimeMillis()): EpgProgramEntity? =
        withContext(Dispatchers.IO) { epgProgramDao.getCurrentForChannel(channelId, nowMillis) }

    suspend fun replaceProgramsForChannel(channelId: String, programs: List<EpgProgramEntity>) =
        withContext(Dispatchers.IO) { epgProgramDao.replaceForChannel(channelId, programs) }

    // PROMPT_EPG.md, подзадача 2 — точка входа для XtreamEpgClient
    // (get_short_epg/get_epg): в отличие от replaceProgramsForChannel(),
    // НЕ удаляет остальные программы канала перед записью — short_epg
    // отдаёт только ближайшие N программ, а не расписание целиком, полная
    // замена стёрла бы уже накопленные из XMLTV (подзадача 3) слоты вне
    // этого маленького окна. Маппинг DTO→Entity — здесь, а не в вызывающем
    // коде: составной id ("channelId:startTimeMillis") — деталь схемы
    // EpgProgramEntity, вызывающему коду (PlaylistSourceRepository) знать
    // о ней незачем.
    suspend fun upsertFromXtream(channelId: String, programs: List<XtreamEpgProgram>) {
        if (programs.isEmpty()) return
        val entities = programs.map { p ->
            EpgProgramEntity(
                id = "$channelId:${p.startTimeMillis}",
                channelId = channelId,
                title = p.title,
                description = p.description,
                startTimeMillis = p.startTimeMillis,
                endTimeMillis = p.endTimeMillis
            )
        }
        withContext(Dispatchers.IO) { epgProgramDao.upsertAll(entities) }
    }

    // Прошлый край скользящего окна (−2ч, см. PROMPT_EPG.md) — вызывается
    // EpgCleanupWorker по расписанию, не при каждом чтении: чтение сетки
    // (getForChannelInRange) и так не покажет то, что вне окна, лишняя
    // чистка на каждый запрос была бы избыточной работой с БД.
    suspend fun cleanupOutsideWindow() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(2)
        epgProgramDao.deleteOlderThan(cutoff)
    }
}
