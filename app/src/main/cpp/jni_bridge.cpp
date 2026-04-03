#include <jni.h>
#include <android/log.h>
#include "AudioEngine.h"

static AudioEngine gAudioEngine;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_pianotiles_audio_AudioManager_nativeInit(JNIEnv* /*env*/, jobject /*obj*/) {
    return gAudioEngine.openStream() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_pianotiles_audio_AudioManager_nativeNoteOn(
    JNIEnv* /*env*/, jobject /*obj*/, jint midiNote, jint velocity) {
    gAudioEngine.noteOn(midiNote, velocity);
}

JNIEXPORT void JNICALL
Java_com_pianotiles_audio_AudioManager_nativeNoteOff(
    JNIEnv* /*env*/, jobject /*obj*/, jint midiNote) {
    gAudioEngine.noteOff(midiNote);
}

// Stub for sample loading — implemented in Layer 3
JNIEXPORT jboolean JNICALL
Java_com_pianotiles_audio_AudioManager_nativeLoadSample(
    JNIEnv* /*env*/, jobject /*obj*/, jint /*midiNote*/,
    jbyteArray /*oggData*/, jint /*length*/) {
    return JNI_TRUE;
}

} // extern "C"
