package com.platinum.ott.presentation.screens.epg

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.platinum.ott.core.SessionGraph
import com.platinum.ott.data.local.entity.EpgProgramEntity
import com.platinum.ott.data.repository.ChannelRepository
import com.platinum.ott.data.repository.ChannelUiItem
import com.platinum.ott.data.repository.EpgProgramRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Слот в строке канала внутри текущего временного окна — либо реальная
 * программа, либо "дыра" (нет данных EPG на этот промежуток, например
 * XMLTV-источник ещё не подтянулся или не покрывает канал). weightMinutes —
 * это вес для Modifier.weight() в UI: и слоты канала, и линейка часов
 * сверху делят ОДНУ и ту же длину окна пропорционально минутам, поэтому
 * колонки у всех строк и у линейки времени совпадают без единого
 * абсолютного пикселя.
 *
 * Это же и есть сознательный ответ на "синхронная горизонтальная прокрутка"
 * из PROMPT_EPG.md: не непрерывный scroll, а окно ФИКСИРОВАННОЙ длины
 * (см. EpgGridViewModel.WINDOW_SPAN_MS), которое целиком помещается по
 * ширине экрана и сдвигается шагом (кнопки/D-pad), а не тянется пальцем —
 * тот же принцип, что уже применялся в проекте раньше (см. отказ от
 * drag-and-drop для приоритета источников в SourcesScreen.kt — "на TV
 * пультом ненадёжно"), только для непрерывного горизонтального скролла он
 * ещё более явно неприменим, чем для перетаскивания элементов списка.
 * Если в реальном использовании это окажется неудобным — обсудить отдельно,
 * это не единственный возможный вариант, просто наиболее совместимый с
 * пультом ТВ из имеющихся.
 */
sealed interface EpgSlot {
    val weightMinutes: Float
    data class ProgramSlot(
        val program: EpgProgramEntity,
        val isLive: Boolean,
        // PROMPT_EPG.md, подзадача 5 — программа ПОЛНОСТЬЮ в прошлом
        // (endTimeMillis <= now, см. buildSlots()), у канала есть хотя бы
        // один стрим с catchupDays > 0 (ChannelUiItem.catchupDaysAvailable),
        // и сама программа не старше catchupDaysAvailable дней назад. Не
        // проверяет доступность архива у КОНКРЕТНОГО стрима на этот
        // channel-wide максимум — какой именно стрим реально сможет
        // отдать архив на это время, решает GetPlayableUrlUseCase.
        // executeChannelCatchup() в момент запроса, здесь только "стоит ли
        // вообще предлагать нажать".
        val isCatchupAvailable: Boolean,
        override val weightMinutes: Float
    ) : EpgSlot
    data class GapSlot(override val weightMinutes: Float) : EpgSlot
}

data class EpgChannelRow(val channel: ChannelUiItem, val slots: List<EpgSlot>)

sealed interface EpgGridUiState {
    data object Loading : EpgGridUiState
    // Отдельно от Success(rows = emptyList()) — сообщение пользователю
    // разное ("подпишитесь на каналы" vs, в будущем, "EPG ещё не пришёл"),
    // тот же принцип разделения состояний, что и ChannelsUiState рядом.
    data object NoChannels : EpgGridUiState
    data class Success(val rows: List<EpgChannelRow>, val windowStart: Long, val windowEnd: Long) : EpgGridUiState
}

@HiltViewModel
class EpgGridViewModel @Inject constructor(
    private val sessionGraph: SessionGraph
) : ViewModel() {
    private val channelRepository: ChannelRepository get() = sessionGraph.channelRepository
    private val epgProgramRepository: EpgProgramRepository get() = sessionGraph.epgProgramRepository

    private val _uiState = MutableStateFlow<EpgGridUiState>(EpgGridUiState.Loading)
    val uiState: StateFlow<EpgGridUiState> = _uiState.asStateFlow()

    private var windowStart = initialWindowStart()

    init { load() }

    fun load() { viewModelScope.launch { reload() } }

    /** Кнопки "◀"/"▶" — шаг WINDOW_STEP_MS, не непрерывный скролл (см. EpgSlot). */
    fun shiftWindow(forward: Boolean) {
        windowStart += if (forward) WINDOW_STEP_MS else -WINDOW_STEP_MS
        viewModelScope.launch { reload() }
    }

    fun jumpToNow() {
        windowStart = initialWindowStart()
        viewModelScope.launch { reload() }
    }

    private suspend fun reload() {
        val channels = channelRepository.getSubscribed()
        if (channels.isEmpty()) {
            _uiState.value = EpgGridUiState.NoChannels
            return
        }
        val start = windowStart
        val end = start + WINDOW_SPAN_MS
        val now = System.currentTimeMillis()
        val rows = channels.map { channel ->
            val programs = epgProgramRepository.getForChannelInRange(channel.id, start, end)
            EpgChannelRow(channel, buildSlots(programs, start, end, now, channel.catchupDaysAvailable))
        }
        _uiState.value = EpgGridUiState.Success(rows, start, end)
    }

    companion object {
        val WINDOW_SPAN_MS: Long = TimeUnit.HOURS.toMillis(3)
        val WINDOW_STEP_MS: Long = TimeUnit.MINUTES.toMillis(30)
        val RULER_SEGMENT_MS: Long = TimeUnit.MINUTES.toMillis(30)

        // Окно начинается на полшага (30 мин) раньше "сейчас" — так
        // текущая программа почти никогда не оказывается в первой же
        // колонке впритык к левому краю, а не потому что 30 минут чем-то
        // особенно удобны сами по себе.
        private fun initialWindowStart(): Long {
            val now = System.currentTimeMillis()
            val floored = now - (now % WINDOW_STEP_MS)
            return floored - WINDOW_STEP_MS
        }
    }
}

/**
 * Программы из EpgProgramDao.getForChannelInRange() уже отсортированы по
 * startTimeMillis и не перекрываются по построению (составной id
 * "channelId:startTimeMillis" + replaceForChannel() — см. EpgProgramDao.kt/
 * PlaylistSourceRepository.refreshEpgFromXmltv()), поэтому здесь только
 * закрываются ДЫРЫ между ними/до первой/после последней — GapSlot.
 */
fun buildSlots(programs: List<EpgProgramEntity>, windowStart: Long, windowEnd: Long, nowMillis: Long, catchupDaysAvailable: Int): List<EpgSlot> {
    val catchupCutoffMillis = nowMillis - TimeUnit.DAYS.toMillis(catchupDaysAvailable.toLong())
    val slots = mutableListOf<EpgSlot>()
    var cursor = windowStart
    for (program in programs) {
        val clampedStart = program.startTimeMillis.coerceAtLeast(windowStart)
        val clampedEnd = program.endTimeMillis.coerceAtMost(windowEnd)
        if (clampedStart > cursor) slots += EpgSlot.GapSlot(minutesBetween(cursor, clampedStart))
        val isLive = program.startTimeMillis <= nowMillis && program.endTimeMillis > nowMillis
        val isCatchupAvailable = !isLive && catchupDaysAvailable > 0 &&
            program.endTimeMillis <= nowMillis && program.startTimeMillis >= catchupCutoffMillis
        slots += EpgSlot.ProgramSlot(program, isLive, isCatchupAvailable, minutesBetween(clampedStart, clampedEnd))
        cursor = clampedEnd
    }
    if (cursor < windowEnd) slots += EpgSlot.GapSlot(minutesBetween(cursor, windowEnd))
    return slots
}

// coerceAtLeast(0.1f) — Modifier.weight() падает на 0/отрицательном весе,
// защита от вырожденного слота при странных/битых временных метках.
private fun minutesBetween(fromMillis: Long, toMillis: Long): Float =
    ((toMillis - fromMillis).coerceAtLeast(0L) / 60000f).coerceAtLeast(0.1f)

fun formatHm(millis: Long): String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(millis)
