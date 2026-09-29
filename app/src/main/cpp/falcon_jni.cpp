#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <mutex>
#include <atomic>
#include <chrono>
#include <cstring>
#include <cmath>
#include <algorithm>
#include <cstdlib>
#include "llama.h"

#define LOG_TAG "FalconBench"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {
int falcon_set_thread_priority(int nice);
int falcon_set_cpu_affinity(uint64_t mask);
int falcon_nproc();
}

namespace {
std::mutex g_mu;
llama_model * g_model = nullptr;
llama_context * g_ctx = nullptr;
std::atomic<bool> g_abort{false};

void free_all_locked() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_free_model(g_model); g_model = nullptr; }
}

std::string j2s(JNIEnv * env, jstring js) {
    if (!js) return {};
    const char * c = env->GetStringUTFChars(js, nullptr);
    std::string s = c ? c : "";
    if (c) env->ReleaseStringUTFChars(js, c);
    return s;
}
}

extern "C" JNIEXPORT void JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeInit(JNIEnv *, jclass) {
    llama_backend_init(false);
    LOGI("nproc=%d", falcon_nproc());
}

extern "C" JNIEXPORT void JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeShutdown(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    free_all_locked();
    llama_backend_free();
}

extern "C" JNIEXPORT jint JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeSetPriority(JNIEnv *, jclass, jint nice) {
    return falcon_set_thread_priority(nice);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeSetAffinity(JNIEnv *, jclass, jlong mask) {
    return falcon_set_cpu_affinity((uint64_t)mask);
}

extern "C" JNIEXPORT jint JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeNproc(JNIEnv *, jclass) {
    return falcon_nproc();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeLoadPath(
        JNIEnv * env, jclass, jstring jpath, jint n_threads, jint n_ctx,
        jboolean use_mmap, jboolean use_mlock) {
    std::string path = j2s(env, jpath);
    if (path.empty()) return JNI_FALSE;
    std::lock_guard<std::mutex> lk(g_mu);
    free_all_locked();

    auto mp = llama_model_default_params();
    mp.use_mmap = use_mmap;
    mp.use_mlock = use_mlock;
    mp.n_gpu_layers = 0;

    g_model = llama_load_model_from_file(path.c_str(), mp);
    if (!g_model) { LOGE("load_model failed"); return JNI_FALSE; }

    auto cp = llama_context_default_params();
    cp.n_ctx = n_ctx > 0 ? (uint32_t)n_ctx : 2048;
    cp.n_threads = n_threads > 0 ? n_threads : falcon_nproc();
    cp.n_threads_batch = cp.n_threads;

    g_ctx = llama_new_context_with_model(g_model, cp);
    if (!g_ctx) {
        LOGE("new_context failed");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }
    LOGI("loaded");
    return JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeUnload(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    free_all_locked();
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeIsLoaded(JNIEnv *, jclass) {
    std::lock_guard<std::mutex> lk(g_mu);
    return (g_model && g_ctx) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeAbort(JNIEnv *, jclass) {
    g_abort.store(true);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeGenerate(
        JNIEnv * env, jclass, jstring jprompt, jint max_tokens, jfloat temp, jint top_k, jfloat) {
    std::string prompt = j2s(env, jprompt);
    g_abort.store(false);
    std::lock_guard<std::mutex> lk(g_mu);
    if (!g_model || !g_ctx) return env->NewStringUTF("[error: model not loaded]");

    std::vector<llama_token> tokens(prompt.size() + 16);
    int n = llama_tokenize(g_ctx, prompt.c_str(), tokens.data(), (int)tokens.size(), true);
    if (n < 0) {
        tokens.resize((size_t)(-n));
        n = llama_tokenize(g_ctx, prompt.c_str(), tokens.data(), (int)tokens.size(), true);
    }
    if (n <= 0) return env->NewStringUTF("[error: tokenize failed]");
    tokens.resize((size_t)n);

    llama_kv_cache_clear(g_ctx);

    for (int i = 0; i < n; i += 32) {
        int n_eval = std::min(32, n - i);
        if (llama_decode(g_ctx, llama_batch_get_one(&tokens[(size_t)i], n_eval, i, 0)) != 0)
            return env->NewStringUTF("[error: decode prompt]");
    }

    std::string out;
    int n_gen = max_tokens > 0 ? max_tokens : 128;
    int n_vocab = llama_n_vocab(g_model);

    for (int i = 0; i < n_gen && !g_abort.load(); ++i) {
        float * logits = llama_get_logits_ith(g_ctx, -1);
        if (!logits) break;
        llama_token id = 0;
        if (temp <= 0.01f) {
            int best = 0; float bv = logits[0];
            for (int t = 1; t < n_vocab; ++t) if (logits[t] > bv) { bv = logits[t]; best = t; }
            id = best;
        } else {
            int k = top_k > 0 ? top_k : 40;
            if (k > n_vocab) k = n_vocab;
            std::vector<std::pair<float,int>> v;
            for (int t = 0; t < n_vocab; ++t) v.emplace_back(logits[t] / temp, t);
            std::partial_sort(v.begin(), v.begin() + k, v.end(),
                [](auto&a, auto&b){ return a.first > b.first; });
            float sum = 0.f;
            for (int t = 0; t < k; ++t) {
                v[(size_t)t].first = expf(v[(size_t)t].first - v[0].first);
                sum += v[(size_t)t].first;
            }
            float r = (float)rand() / (float)RAND_MAX * sum, acc = 0.f;
            id = v[0].second;
            for (int t = 0; t < k; ++t) {
                acc += v[(size_t)t].first;
                if (acc >= r) { id = v[(size_t)t].second; break; }
            }
        }
        if (id == llama_token_eos(g_model)) break;
        char buf[256];
        int np = llama_token_to_piece(g_ctx, id, buf, (int)sizeof(buf));
        if (np < 0) np = -np;
        if (np > 0 && np < (int)sizeof(buf)) out.append(buf, (size_t)np);
        if (llama_decode(g_ctx, llama_batch_get_one(&id, 1, n + i, 0)) != 0) break;
    }
    return env->NewStringUTF(out.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_falconbench_app_native_LlamaBridge_nativeBench(
        JNIEnv * env, jclass, jstring jprompt, jint n_predict) {
    auto t0 = std::chrono::steady_clock::now();
    jstring result = Java_com_falconbench_app_native_LlamaBridge_nativeGenerate(
        env, nullptr, jprompt, n_predict, 0.f, 0, 1.f);
    auto t1 = std::chrono::steady_clock::now();
    double sec = std::chrono::duration<double>(t1 - t0).count();
    const char * body = env->GetStringUTFChars(result, nullptr);
    size_t chars = body ? strlen(body) : 0;
    char summary[256];
    snprintf(summary, sizeof(summary), "time=%.2fs approx_tps=%.2f out_chars=%zu",
             sec, sec > 0 ? n_predict / sec : 0.0, chars);
    std::string full = std::string("--- metrics ---\n") + summary + "\n--- output ---\n" + (body ? body : "");
    if (body) env->ReleaseStringUTFChars(result, body);
    env->DeleteLocalRef(result);
    return env->NewStringUTF(full.c_str());
}
