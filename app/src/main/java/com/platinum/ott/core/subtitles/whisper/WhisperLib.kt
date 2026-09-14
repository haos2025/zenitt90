package com.platinum.ott.core.subtitles.whisper

// PROMPT_SUBTITLES.md, подзадача 5. Имена/сигнатуры методов должны БУКВАЛЬНО
// совпадать с jni_bridge.cpp (Java_<package>_<class>_<method>, package с
// точками замененными на подчёркивания) — при переносе этого файла в
// другой пакет обязательно поправить и C++-сторону.
//
// Одно упрощение относительно официального примера
// (whisper.android.java/WhisperLib.java): там для armeabi-v7a/arm64-v8a
// грузятся ABI-специализированные .so (vfpv4/v8fp16 — читают /proc/cpuinfo
// на предмет поддержки конкретных инструкций) — здесь один универсальный
// таргет "zenith_whisper" (см. CMakeLists.txt) ради меньшего риска в первой
// версии; специализации можно добавить позже, если профилирование на
// реальном устройстве покажет в этом смысл.
internal object WhisperLib {
    init {
        System.loadLibrary("zenith_whisper")
    }

    external fun initContext(modelPath: String): Long
    external fun freeContext(contextPtr: Long)
    external fun fullTranscribe(contextPtr: Long, numThreads: Int, audioData: FloatArray, language: String)
    external fun getTextSegmentCount(contextPtr: Long): Int
    external fun getTextSegment(contextPtr: Long, index: Int): String
    external fun getTextSegmentT0(contextPtr: Long, index: Int): Long
    external fun getTextSegmentT1(contextPtr: Long, index: Int): Long
}
