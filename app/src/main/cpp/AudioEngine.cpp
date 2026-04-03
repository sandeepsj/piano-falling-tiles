#include "AudioEngine.h"
#include <android/log.h>
#include <cstring>

#define LOG_TAG "AUDIO"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

bool AudioEngine::openStream() {
    if (stream_ && stream_->getState() == oboe::StreamState::Started) {
        LOGI("Stream already running — skipping reopen");
        return true;
    }

    oboe::AudioStreamBuilder builder;
    builder.setDirection(oboe::Direction::Output);
    builder.setPerformanceMode(oboe::PerformanceMode::LowLatency);
    builder.setSharingMode(oboe::SharingMode::Exclusive);
    builder.setFormat(oboe::AudioFormat::Float);
    builder.setChannelCount(oboe::ChannelCount::Stereo);
    builder.setSampleRate(48000);
    builder.setSampleRateConversionQuality(oboe::SampleRateConversionQuality::Medium);
    builder.setDataCallback(this);
    builder.setErrorCallback(this);

    oboe::Result result = builder.openManagedStream(stream_);
    if (result != oboe::Result::OK) {
        LOGE("openManagedStream failed: %s", oboe::convertToText(result));
        return false;
    }

    synth_.setSampleRate(static_cast<float>(stream_->getSampleRate()));

    result = stream_->start();
    if (result != oboe::Result::OK) {
        LOGE("stream->start() failed: %s", oboe::convertToText(result));
        return false;
    }

    int bufFrames = stream_->getBufferSizeInFrames();
    int sr        = stream_->getSampleRate();
    float latMs   = static_cast<float>(bufFrames) / static_cast<float>(sr) * 1000.f;
    LOGI("Stream ready — api=%s sr=%d bufFrames=%d latencyMs=~%.1f",
         oboe::convertToText(stream_->getAudioApi()),
         sr, bufFrames, latMs);
    return true;
}

void AudioEngine::closeStream() {
    if (stream_) stream_->stop();
}

void AudioEngine::noteOn(int midiNote, int velocity) {
    synth_.noteOn(midiNote, velocity);
}

void AudioEngine::noteOff(int midiNote) {
    synth_.noteOff(midiNote);
}

oboe::DataCallbackResult AudioEngine::onAudioReady(
        oboe::AudioStream* /*stream*/,
        void* audioData,
        int32_t numFrames) {
    float* out = static_cast<float*>(audioData);
    memset(out, 0, sizeof(float) * numFrames * 2);
    synth_.mixInto(out, numFrames);
    return oboe::DataCallbackResult::Continue;
}

void AudioEngine::onErrorAfterClose(oboe::AudioStream* /*stream*/, oboe::Result error) {
    LOGE("Stream error: %s — reopening", oboe::convertToText(error));
    openStream();
}
