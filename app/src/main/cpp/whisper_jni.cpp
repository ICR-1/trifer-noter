// Jembatan JNI tipis ke whisper.cpp untuk fitur "Rapikan".
// Kelas Kotlin: com.nousfercorp.triferNoter.asr.WhisperEngine
#include <jni.h>
#include <android/log.h>

#include <atomic>
#include <string>
#include <vector>

#include "whisper.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "TriferWhisper", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "TriferWhisper", __VA_ARGS__)

namespace {
std::atomic<bool> g_abort{false};

bool abort_cb(void *) { return g_abort.load(); }

std::string to_std(JNIEnv *env, jstring s) {
    if (s == nullptr) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string out(c ? c : "");
    if (c) env->ReleaseStringUTFChars(s, c);
    return out;
}
}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_nousfercorp_triferNoter_asr_WhisperEngine_nativeInit(JNIEnv *env, jobject, jstring modelPath) {
    const std::string path = to_std(env, modelPath);
    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;
    whisper_context *ctx = whisper_init_from_file_with_params(path.c_str(), cparams);
    if (ctx == nullptr) LOGE("gagal memuat model: %s", path.c_str());
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_nousfercorp_triferNoter_asr_WhisperEngine_nativeFree(JNIEnv *, jobject, jlong ptr) {
    if (ptr != 0) whisper_free(reinterpret_cast<whisper_context *>(ptr));
}

JNIEXPORT void JNICALL
Java_com_nousfercorp_triferNoter_asr_WhisperEngine_nativeAbort(JNIEnv *, jobject, jboolean abort) {
    g_abort.store(abort == JNI_TRUE);
}

/**
 * Transkripsi satu potongan audio (PCM float 16 kHz, idealnya <= 30 detik).
 * Mengembalikan larik string berformat "t0Ms|t1Ms|noSpeechProb|teks", atau null bila gagal/dibatalkan.
 */
JNIEXPORT jobjectArray JNICALL
Java_com_nousfercorp_triferNoter_asr_WhisperEngine_nativeTranscribe(
        JNIEnv *env, jobject, jlong ptr, jfloatArray samples, jint nSamples, jint threads,
        jint audioCtx, jstring prompt) {
    auto *ctx = reinterpret_cast<whisper_context *>(ptr);
    if (ctx == nullptr) return nullptr;

    const std::string promptStr = to_std(env, prompt);

    whisper_full_params p = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    p.n_threads = threads;
    p.language = "id";
    p.detect_language = false;
    p.translate = false;
    p.no_context = true;
    p.single_segment = false;
    p.print_special = false;
    p.print_progress = false;
    p.print_realtime = false;
    p.print_timestamps = false;
    p.audio_ctx = audioCtx;  // 0 = penuh (30 dtk); lebih kecil = lebih cepat untuk potongan pendek
    p.suppress_blank = true;
    p.suppress_nst = true;
    p.greedy.best_of = 1;
    // Whisper melompati seluruh jendela bila menduga "tidak ada ucapan", dan sering keliru pada audio nyata
    // (terukur: ±21 dtk ucapan dilewati). Penilaian ada di sisi Kotlin berdasarkan energi audio.
    p.no_speech_thold = 1.0f;
    p.logprob_thold = -100.0f;
    if (!promptStr.empty()) p.initial_prompt = promptStr.c_str();
    p.abort_callback = abort_cb;
    p.abort_callback_user_data = nullptr;

    jfloat *data = env->GetFloatArrayElements(samples, nullptr);
    const int rc = whisper_full(ctx, p, data, nSamples);
    env->ReleaseFloatArrayElements(samples, data, JNI_ABORT);
    if (rc != 0) {
        if (!g_abort.load()) LOGE("whisper_full gagal: %d", rc);
        return nullptr;
    }

    const int n = whisper_full_n_segments(ctx);
    jclass strCls = env->FindClass("java/lang/String");
    jobjectArray out = env->NewObjectArray(n, strCls, nullptr);
    for (int i = 0; i < n; i++) {
        const int64_t t0 = whisper_full_get_segment_t0(ctx, i) * 10;  // satuan 10 ms -> ms
        const int64_t t1 = whisper_full_get_segment_t1(ctx, i) * 10;
        const float nsp = whisper_full_get_segment_no_speech_prob(ctx, i);
        const char *text = whisper_full_get_segment_text(ctx, i);
        std::string line = std::to_string(t0) + "|" + std::to_string(t1) + "|" + std::to_string(nsp) + "|" +
                           (text ? text : "");
        jstring js = env->NewStringUTF(line.c_str());
        env->SetObjectArrayElement(out, i, js);
        env->DeleteLocalRef(js);
    }
    return out;
}

}  // extern "C"
