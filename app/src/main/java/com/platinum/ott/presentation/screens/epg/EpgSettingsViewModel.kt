package com.platinum.ott.presentation.screens.epg

import androidx.lifecycle.ViewModel
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.repository.EpgStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * «Настройки → Телепрограмма (EPG)»: состояние и запуск загрузки программы.
 * Само обновление живёт в PlaylistSourceRepository (продолжается при уходе с экрана);
 * здесь только показ состояния и кнопка.
 */
@HiltViewModel
class EpgSettingsViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    val status: StateFlow<EpgStatus> = sessionGraph.playlistSourceRepository.epgStatus

    fun refresh() = sessionGraph.playlistSourceRepository.startEpgRefresh()
}

/** Короткое описание состояния для шапки экрана (общее для ТВ и телефона). */
internal fun epgHeadline(s: EpgStatus): String = when {
    s.running -> "Идёт загрузка…"
    s.ok == null -> "Ещё не запускалось"
    s.ok == true -> "Обновлено ${epgTime(s.finishedAt)}"
    else -> "Не удалось обновить (${epgTime(s.finishedAt)})"
}

internal fun epgSummary(s: EpgStatus): String? =
    if (s.running || s.ok == null) null
    else "Каналов с программой: ${s.channelsWithEpg} из ${s.totalChannels} • программ в базе: ${s.programs}"

/** Подсказка, что делать, если программы нет ни у одного канала или загрузка не удалась. */
internal fun epgAdvice(s: EpgStatus): String? =
    if (!s.running && s.ok != null && (s.ok == false || s.channelsWithEpg == 0))
        "Программа сопоставляется с каналами по tvg-id или по названию. Если в журнале «в плейлисте нет адреса программы (url-tvg)», " +
            "у источника нет своей программы — тогда берутся запасные адреса. Если файл получен, но «сопоставлено по id 0, по названию 0», " +
            "названия или tvg-id каналов не совпадают с теми, что в файле программы. Пришлите этот журнал — по нему видно, что именно не сошлось."
    else null

private fun epgTime(ms: Long): String =
    if (ms <= 0L) "—" else SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()).format(Date(ms))
