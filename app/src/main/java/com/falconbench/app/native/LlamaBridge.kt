package com.falconbench.app.native

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * JNI façade for llama.cpp with 32-bit-friendly load path.
 * SAF URIs are copied into app cache so mmap works on armeabi-v7a.
 */
object LlamaBridge {
    private const val TAG = "LlamaBridge"

    init {
        System.loadLibrary("falconbench")
        nativeInit()
    }

    external fun nativeInit()
    external fun nativeShutdown()
    external fun nativeSetPriority(nice: Int): Int
    external fun nativeSetAffinity(mask: Long): Int
    external fun nativeNproc(): Int
    external fun nativeLoadPath(
        path: String,
        nThreads: Int,
        nCtx: Int,
        useMmap: Boolean,
        useMlock: Boolean
    ): Boolean
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean
    external fun nativeAbort()
    external fun nativeGenerate(
        prompt: String,
        maxTokens: Int,
        temp: Float,
        topK: Int,
        topP: Float
    ): String
    external fun nativeBench(prompt: String, nPredict: Int): String

    data class LoadConfig(
        val nThreads: Int = Runtime.getRuntime().availableProcessors().coerceIn(1, 8),
        val nCtx: Int = 2048,
        val useMmap: Boolean = true,
        val useMlock: Boolean = false,
        val nice: Int = 0,           // -20..19
        val affinityMask: Long = 0L  // 0 = leave OS default
    )

    fun loadFromUri(context: Context, uri: Uri, cfg: LoadConfig): Result<String> = runCatching {
        val dest = File(context.cacheDir, "model_active.gguf")
        context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                FileOutputStream(dest).use { output ->
                    input.copyTo(output)
                }
            }
        } ?: error("Cannot open URI")

        if (cfg.affinityMask != 0L) nativeSetAffinity(cfg.affinityMask)
        if (cfg.nice != 0) nativeSetPriority(cfg.nice)

        val ok = nativeLoadPath(
            dest.absolutePath,
            cfg.nThreads,
            cfg.nCtx,
            cfg.useMmap,
            cfg.useMlock
        )
        if (!ok) error("nativeLoadPath failed")
        dest.absolutePath
    }.onFailure { Log.e(TAG, "loadFromUri", it) }

    fun loadFromFile(path: String, cfg: LoadConfig): Result<Unit> = runCatching {
        if (cfg.affinityMask != 0L) nativeSetAffinity(cfg.affinityMask)
        if (cfg.nice != 0) nativeSetPriority(cfg.nice)
        val ok = nativeLoadPath(path, cfg.nThreads, cfg.nCtx, cfg.useMmap, cfg.useMlock)
        if (!ok) error("nativeLoadPath failed")
    }
}
