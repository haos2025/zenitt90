// PROMPT_SUBTITLES.md, подзадача 5 — тонкий JNI-мост к whisper.cpp.
//
// Основано на официальном примере
// github.com/ggml-org/whisper.cpp/examples/whisper.android.java/app/src/main/jni/whisper/jni.c
// (верифицировано чтением исходников whisper.cpp v1.9.4 в этой же
// сессии), с двумя отличиями:
// 1. Добавлен параметр language в fullTranscribe — в оригинале захардкожен
//    "en" (params.language = "en"), нам нужен русский по умолчанию, язык
//    передаётся с Kotlin-стороны (LocalWhisperTranscriber.kt).
// 2. Один .so-таргет без ABI-специализаций (vfpv4/v8fp16) — сознательное
//    упрощение ради снижения риска в первой версии, см. комментарий в
//    WhisperLib.kt.
//
// НЕ СКОМПИЛИРОВАНО в этой сессии (нет NDK toolchain в окружении) —
// сигнатуры JNI-функций подобраны так, чтобы точно совпадать с
// external fun в WhisperLib.kt (Java_ + полный package + класс + метод,
// подчёркивания в package экранированы как _1 не требуются, т.к. в пути
// пакета com_platinum_ott_core_subtitles_whisper_WhisperLib нет символов
// подчёркивания кроме разделителей точек — стандартное правило JNI
// name-mangling).

#include <jni.h>
#include "whisper.h"

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_initContext(
        JNIEnv *env, jobject /*thiz*/, jstring model_path_str) {
    const char *model_path_chars = env->GetStringUTFChars(model_path_str, nullptr);
    struct whisper_context_params cparams = whisper_context_default_params();
    // По умолчанию use_gpu=true — безвредно, т.к. GPU-бэкенды (CUDA/Metal/
    // Vulkan) не собираются вообще (CMakeLists.txt их не включает, только
    // CPU), но выключаем явно, а не полагаемся на то, что несобранный
    // бэкенд молча проигнорируется — проверено реальной сборкой+запуском
    // на хосте в этой сессии (cparams.use_gpu действительно читается как
    // true по умолчанию).
    cparams.use_gpu = false;
    struct whisper_context *context = whisper_init_from_file_with_params(model_path_chars, cparams);
    env->ReleaseStringUTFChars(model_path_str, model_path_chars);
    return reinterpret_cast<jlong>(context);
}

JNIEXPORT void JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_freeContext(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong context_ptr) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    whisper_free(context);
}

JNIEXPORT void JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_fullTranscribe(
        JNIEnv *env, jobject /*thiz*/, jlong context_ptr, jint num_threads,
        jfloatArray audio_data, jstring language_str) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    jfloat *audio_data_arr = env->GetFloatArrayElements(audio_data, nullptr);
    const jsize audio_data_length = env->GetArrayLength(audio_data);
    const char *language_chars = env->GetStringUTFChars(language_str, nullptr);

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.language = language_chars;
    params.n_threads = num_threads;
    params.offset_ms = 0;
    // Аудио уже нарезано по речи Silero VAD (подзадача 4) до вызова
    // Whisper — здесь всегда один относительно короткий кусок речи, а не
    // весь 5-минутный чанк, поэтому single_segment=false (Whisper сам
    // разобьёт кусок на несколько сегментов с таймкодами, если внутри
    // несколько фраз) и no_context=true (каждый вызов независим, нет
    // смысла тащить текстовый контекст между несвязанными VAD-сегментами).
    params.no_context = true;
    params.single_segment = false;

    whisper_reset_timings(context);
    whisper_full(context, params, audio_data_arr, audio_data_length);

    env->ReleaseStringUTFChars(language_str, language_chars);
    env->ReleaseFloatArrayElements(audio_data, audio_data_arr, JNI_ABORT);
}

JNIEXPORT jint JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_getTextSegmentCount(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong context_ptr) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    return whisper_full_n_segments(context);
}

JNIEXPORT jstring JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_getTextSegment(
        JNIEnv *env, jobject /*thiz*/, jlong context_ptr, jint index) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    const char *text = whisper_full_get_segment_text(context, index);
    return env->NewStringUTF(text);
}

// Внимание (см. WhisperContext.kt): whisper.cpp возвращает t0/t1 в
// ЦЕНТИСЕКУНДАХ (1/100 с = 10 мс), не в миллисекундах — известная
// особенность API (ggml-org/whisper.cpp issue #3370), конвертация в мс
// сделана на Kotlin-стороне, не здесь.
JNIEXPORT jlong JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_getTextSegmentT0(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong context_ptr, jint index) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    return static_cast<jlong>(whisper_full_get_segment_t0(context, index));
}

JNIEXPORT jlong JNICALL
Java_com_platinum_ott_core_subtitles_whisper_WhisperLib_getTextSegmentT1(
        JNIEnv * /*env*/, jobject /*thiz*/, jlong context_ptr, jint index) {
    auto *context = reinterpret_cast<struct whisper_context *>(context_ptr);
    return static_cast<jlong>(whisper_full_get_segment_t1(context, index));
}

} // extern "C"
