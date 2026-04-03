cmake_minimum_required(VERSION 3.22.1)
project("piano_audio")

# Find Oboe package
find_package(oboe REQUIRED CONFIG)

add_library(
    piano_audio
    SHARED
    jni_bridge.cpp
    AudioEngine.cpp
    PianoSynthesizer.cpp
)

target_link_libraries(
    piano_audio
    oboe::oboe
    android
    log
)

target_compile_options(piano_audio PRIVATE
    -O3
    -march=armv8-a
    -fno-rtti
    -fno-exceptions
)
