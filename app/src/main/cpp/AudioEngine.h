#pragma once
#include <oboe/Oboe.h>
#include "PianoSynthesizer.h"

/**
 * Oboe audio engine — AAudio exclusive low-latency stream.
 * Inherits both DataCallback and ErrorCallback so the stream auto-reopens
 * on device disconnection (e.g. headphone unplug).
 */
class AudioEngine :
        public oboe::AudioStreamDataCallback,
        public oboe::AudioStreamErrorCallback {
public:
    bool openStream();
    void closeStream();
    void noteOn(int midiNote, int velocity);
    void noteOff(int midiNote);

    // AudioStreamDataCallback
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream* stream,
            void* audioData,
            int32_t numFrames) override;

    // AudioStreamErrorCallback — auto-reopens stream after disconnect
    void onErrorAfterClose(oboe::AudioStream* stream, oboe::Result error) override;

private:
    oboe::ManagedStream stream_;
    PianoSynthesizer    synth_;
};
