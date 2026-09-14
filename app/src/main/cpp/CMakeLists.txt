# PROMPT_SUBTITLES.md, подзадача 5 — локальный Whisper (whisper.cpp).
#
# Вместо того чтобы вендорить исходники whisper.cpp (C/C++, десятки МБ)
# прямо в репозиторий Zenith OTT, тянем их при конфигурации CMake через
# FetchContent с зафиксированным тегом релиза — GitHub Actions раннеры
# имеют полный доступ в интернет. Официальный пример
# github.com/ggml-org/whisper.cpp/examples/whisper.android.java решает
# это иначе (там Android-проект физически лежит ВНУТРИ репозитория
# whisper.cpp, относительными путями до ../../../src и ../../../ggml) —
# у нас так не получится, это два разных репозитория (Zenith OTT и
# zenith-backend — тоже отдельный, для контраста: тот прокси-бэкенд,
# этот C++-библиотека).
#
# whisper.cpp v1.9.4 (сентябрь 2026) — актуальный релизный тег на момент
# написания. Его собственный CMakeLists.txt уже сам создаёт таргет
# `whisper` (динамическая/статическая — по BUILD_SHARED_LIBS) со всеми
# зависимостями (ggml, Threads) и правильно определяет
# GGML_NATIVE_DEFAULT=OFF при кросс-компиляции (Android NDK — это и есть
# кросс-компиляция, ggml/CMakeLists.txt проверяет CMAKE_CROSSCOMPILING
# сам) — доп. флаги под ARM (-march=...) не нужны, whisper.cpp сам не
# пытается использовать инструкции хоста.
#
# НЕ ПРОВЕРЕНО реальной сборкой (нет доступа к Android NDK/CMake
# toolchain в этой сессии) — первая реальная проверка только на GitHub
# Actions, тот же принцип, что и в COMPATIBILITY.md.

cmake_minimum_required(VERSION 3.22)
project(zenith_whisper)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include(FetchContent)

set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)

FetchContent_Declare(
    whispercpp
    GIT_REPOSITORY https://github.com/ggml-org/whisper.cpp.git
    GIT_TAG v1.9.4
)
FetchContent_MakeAvailable(whispercpp)

# Наш собственный тонкий JNI-мост (не копия jni.c из официального
# примера — упрощён под нужды этого проекта: один .so на все ABI без
# отдельных vfpv4/fp16-специализаций, и добавлен параметр language,
# которого нет в официальном примере — там захардкожен "en", нам нужен
# русский по умолчанию, см. WhisperLib.kt).
add_library(zenith_whisper SHARED jni_bridge.cpp)

find_library(log-lib log)
target_link_libraries(zenith_whisper PRIVATE whisper ${log-lib})
