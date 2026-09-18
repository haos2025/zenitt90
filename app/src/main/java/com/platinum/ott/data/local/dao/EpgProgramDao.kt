package com.platinum.ott.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.platinum.ott.data.local.entity.EpgProgramEntity

@Dao
interface EpgProgramDao {
    // REPLACE, не IGNORE — при повторном фетче того же канала актуальная
    // версия программы (например, изменившееся описание/время) должна
    // перезаписать старую строку с тем же составным id, см. комментарий
    // в EpgProgramEntity.kt.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(programs: List<EpgProgramEntity>)

    // Используется UI сеткой программ (подзадача 5) — все программы канала,
    // которые хоть частично попадают в запрошенный диапазон (не только
    // начинающиеся внутри него, иначе программа, начавшаяся до fromMillis,
    // но всё ещё идущая, пропала бы из выдачи).
    @Query(
        "SELECT * FROM epg_programs WHERE channelId = :channelId " +
            "AND endTimeMillis > :fromMillis AND startTimeMillis < :toMillis " +
            "ORDER BY startTimeMillis ASC"
    )
    suspend fun getForChannelInRange(channelId: String, fromMillis: Long, toMillis: Long): List<EpgProgramEntity>

    // "Сейчас идёт" для канала — например, для строки текущей программы
    // в списке каналов до того, как будет готова полная сетка (подзадача 5).
    @Query(
        "SELECT * FROM epg_programs WHERE channelId = :channelId " +
            "AND startTimeMillis <= :nowMillis AND endTimeMillis > :nowMillis LIMIT 1"
    )
    suspend fun getCurrentForChannel(channelId: String, nowMillis: Long): EpgProgramEntity?

    // Полная замена расписания канала одним свежим фетчем: старые слоты,
    // которых больше нет в новом XMLTV/Xtream-ответе (сетка передач могла
    // измениться), REPLACE по upsertAll не удалит сам по себе — только
    // явный delete-затем-insert в одной транзакции (используется парсерами
    // из подзадачи 3) гарантирует, что расписание канала не содержит и
    // старых, и новых версий одновременно.
    @Transaction
    suspend fun replaceForChannel(channelId: String, programs: List<EpgProgramEntity>) {
        deleteForChannel(channelId)
        upsertAll(programs)
    }

    @Query("DELETE FROM epg_programs WHERE channelId = :channelId")
    suspend fun deleteForChannel(channelId: String)

    // Фоновая чистка (EpgCleanupWorker) — прошлый край скользящего окна:
    // программы, которые уже полностью закончились раньше cutoffMillis.
    @Query("DELETE FROM epg_programs WHERE endTimeMillis < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
