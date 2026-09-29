// CPU affinity / priority helpers for Android 10+ 32-bit userspace
#include <cerrno>
#include <cstring>
#include <pthread.h>
#include <sched.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <android/log.h>

#define LOG_TAG "FalconBenchCPU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

int falcon_set_thread_priority(int nice) {
    // nice: -20 (highest) .. 19 (lowest). App needs no special CAP on own threads.
    int r = setpriority(PRIO_PROCESS, 0, nice);
    if (r != 0) {
        LOGE("setpriority(%d) failed: %s", nice, strerror(errno));
        return -errno;
    }
    LOGI("setpriority -> %d", nice);
    return 0;
}

int falcon_set_cpu_affinity(uint64_t mask) {
    // mask bit i = CPU i. Example: 0xF = cores 0-3
    cpu_set_t set;
    CPU_ZERO(&set);
    for (int i = 0; i < 64; ++i) {
        if (mask & (1ULL << i)) {
            CPU_SET(i, &set);
        }
    }
    // Prefer pthread affinity (works on Android NDK)
    int r = sched_setaffinity(0, sizeof(set), &set);
    if (r != 0) {
        LOGE("sched_setaffinity mask=0x%llx failed: %s",
             (unsigned long long)mask, strerror(errno));
        return -errno;
    }
    LOGI("affinity mask=0x%llx", (unsigned long long)mask);
    return 0;
}

int falcon_nproc() {
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    return n > 0 ? (int)n : 1;
}

} // extern "C"
