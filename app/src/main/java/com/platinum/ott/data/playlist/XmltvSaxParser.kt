package com.platinum.ott.data.playlist

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

/**
 * PROMPT_EPG.md, подзадача 3 — "channel" здесь СЫРОЙ id из XMLTV (значение
 * атрибута `channel=` у `<programme>`), НЕ ChannelEntity.id. По конвенции
 * XMLTV/M3U это и есть значение tvg-id/epg_channel_id — вызывающий код
 * (PlaylistSourceRepository) сам превращает его в "ch_tvg_<channelId>",
 * зная схему id из ChannelMatchingRepository.resolveChannelId(); этот
 * парсер сознательно не знает о ChannelEntity вообще — та же граница
 * ответственности, что у XtreamEpgProgram в XtreamEpgClient.kt.
 */
data class XmltvProgram(
    val channelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startTimeMillis: Long,
    val endTimeMillis: Long
)

// "20240101203000 +0300" — 14 цифр даты/времени + необязательный offset
// (пробел перед ним тоже необязателен — на практике встречаются оба
// варианта). Без offset ДОПУЩЕНИЕ (честно, не проверено на реальном файле
// без таймзоны): считаем UTC — сам стандарт XMLTV этого не требует, но
// большинство реальных генераторов пишут offset всегда, случай без него
// на практике редкий.
private val TIMESTAMP_REGEX = Regex("^(\\d{14})\\s*([+-]\\d{4})?$")

private fun parseXmltvTimestamp(raw: String): Long? {
    val match = TIMESTAMP_REGEX.find(raw.trim()) ?: return null
    val (datePart, offsetPart) = match.destructured
    return try {
        val sdf = SimpleDateFormat("yyyyMMddHHmmss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone(if (offsetPart.isNotBlank()) "GMT$offsetPart" else "UTC")
        sdf.parse(datePart)?.time
    } catch (_: Exception) {
        null
    }
}

/**
 * SAX, не DOM (см. PROMPT_EPG.md, п.4/подзадача 3) — XMLTV-файлы бывают на
 * десятки МБ (опыт с Xiaomi TV Stick 4K/Amlogic S905Y4, см. NEXT_STEPS.md),
 * загрузка всего документа в память как дерево DOM на таком устройстве
 * рискует уронить приложение. Обработчик фильтрует по скользящему окну
 * ПРЯМО ПРИ РАЗБОРЕ (endElement "programme") — программа, которая в окно
 * не попадает или не распарсилась, не попадает даже во временный список
 * result, не то что в БД. Итоговый список — это уже отфильтрованный
 * результат, на порядки меньше исходного файла (типичная панель — не
 * тысячи одновременно идущих окон на все каналы сразу).
 *
 * `<channel>`-блоки (с display-name/icon) СОЗНАТЕЛЬНО не разбираются —
 * сопоставление с ChannelEntity идёт по tvg-id/epg_channel_id (уже
 * решено в подзадачах 1-2), название канала из XMLTV самим Channel не
 * нужно, разбор этих блоков был бы чистой тратой времени/памяти.
 */
object XmltvSaxParser {
    fun parse(input: InputStream, windowStartMillis: Long, windowEndMillis: Long): List<XmltvProgram> {
        val result = mutableListOf<XmltvProgram>()
        val factory = SAXParserFactory.newInstance()
        disableExternalEntitiesQuietly(factory)
        val parser = factory.newSAXParser()
        parser.parse(input, XmltvHandler(windowStartMillis, windowEndMillis) { result.add(it) })
        return result
    }

    // Защита от XXE (внешние DTD/entity — чужой XML с чужой панели, не наш
    // формат). try/catch на каждую фичу отдельно и по имени: встроенный на
    // Android SAX-парсер (Expat-based, не Xerces) может не знать имена
    // фич из Xerces-мира ("disallow-doctype-decl" и т.п.) и выбросить
    // SAXNotRecognizedException — не фатально, Expat и так не тянет
    // внешние DTD по сети сам по себе (в отличие от Xerces на JVM), это
    // явное включение проверки — просто defense in depth, а не единственная линия защиты.
    private fun disableExternalEntitiesQuietly(factory: SAXParserFactory) {
        val features = listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false
        )
        for ((name, value) in features) {
            try {
                factory.setFeature(name, value)
            } catch (_: Exception) {
                // см. комментарий выше — не критично, если конкретный
                // движок эту фичу не знает.
            }
        }
    }
}

private class XmltvHandler(
    private val windowStartMillis: Long,
    private val windowEndMillis: Long,
    private val onProgramme: (XmltvProgram) -> Unit
) : DefaultHandler() {
    private var inProgramme = false
    private var channel: String? = null
    private var startMillis: Long? = null
    private var endMillis: Long? = null
    private var title: String? = null
    private var description: String? = null
    private var category: String? = null

    // Общий буфер для title/desc/category — они у <programme> идут по
    // очереди, не вложены друг в друга, так что один StringBuilder на все
    // три безопасен: currentTextTarget обнуляется на endElement каждого
    // из них до старта следующего.
    private val textBuffer = StringBuilder()
    private var currentTextTarget: StringBuilder? = null

    override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
        when (qName) {
            "programme" -> {
                inProgramme = true
                channel = attributes.getValue("channel")
                startMillis = attributes.getValue("start")?.let(::parseXmltvTimestamp)
                endMillis = attributes.getValue("stop")?.let(::parseXmltvTimestamp)
                title = null; description = null; category = null
            }
            // Берём только ПЕРВЫЙ <title>/<desc>/<category> — реальные
            // XMLTV-файлы нередко несут несколько штук на разных lang=
            // (ДОПУЩЕНИЕ, честно: без выбора по предпочитаемому языку,
            // это отдельное расширение, не архитектурное решение).
            "title" -> if (inProgramme && title == null) startCapturing()
            "desc" -> if (inProgramme && description == null) startCapturing()
            "category" -> if (inProgramme && category == null) startCapturing()
        }
    }

    private fun startCapturing() {
        textBuffer.setLength(0)
        currentTextTarget = textBuffer
    }

    override fun characters(ch: CharArray, start: Int, length: Int) {
        currentTextTarget?.append(ch, start, length)
    }

    override fun endElement(uri: String?, localName: String?, qName: String) {
        when (qName) {
            "title" -> if (inProgramme && title == null) { title = capturedTextOrNull(); currentTextTarget = null }
            "desc" -> if (inProgramme && description == null) { description = capturedTextOrNull(); currentTextTarget = null }
            "category" -> if (inProgramme && category == null) { category = capturedTextOrNull(); currentTextTarget = null }
            "programme" -> {
                inProgramme = false
                val ch = channel; val start = startMillis; val end = endMillis; val t = title
                // Фильтр окна прямо здесь — суть "потокового" разбора:
                // то, что вне скользящего окна или не распарсилось,
                // отбрасывается сразу, не попадая даже во временный
                // список результата.
                if (ch != null && start != null && end != null && t != null && end > windowStartMillis && start < windowEndMillis) {
                    onProgramme(XmltvProgram(ch, t, description, category, start, end))
                }
                channel = null; startMillis = null; endMillis = null; title = null; description = null; category = null
            }
        }
    }

    private fun capturedTextOrNull(): String? = textBuffer.toString().trim().ifBlank { null }
}
