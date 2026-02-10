package com.example.lifequest.ai

import android.util.Log

/**
 * LlamaInference - 底层 JNI 封装
 * 负责直接调用 C++ native 方法
 */
class LlamaInference {
    private val TAG = "LlamaInference"
    // ✅ 模型指针 - 指向 native 层的模型对象
    private var nativeHandle: Long = 0

    fun initialize(modelPath: String): Boolean {
        nativeHandle = nativeInit(modelPath)
        return nativeHandle != 0L
    }

    fun generate(prompt: String, maxTokens: Int = 30): String {
        return try {
            Log.d(TAG, "=== LlamaInference.generate START ===")
            Log.d(TAG, "Prompt length: ${prompt.length}")
            Log.d(TAG, "Max tokens: $maxTokens")
            Log.d(TAG, "Start time: ${System.currentTimeMillis()}")

            if (nativeHandle == 0L) {
                Log.e(TAG, "❌ Model pointer is NULL!")
                return ""
            }

            val startTime = System.currentTimeMillis()

            // 调用 native 方法
            Log.d(TAG, "Calling native method...")
            val shortPrompt = prompt.take(150) // ⭐ 限制输入长度
            val result = nativeGenerate(handle = nativeHandle, prompt = shortPrompt,maxTokens = maxTokens)

            val duration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Native call duration: ${duration}ms")
            Log.d(TAG, "Result length: ${result.length}")
            Log.d(TAG, "Result preview: ${result.take(100)}...")
            Log.d(TAG, "=== LlamaInference.generate END ===")

            result

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in generate", e)
            Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
            Log.e(TAG, "Stack trace: ${e.stackTraceToString()}")
            ""
        }
    }

    fun destroy() {
        if (nativeHandle != 0L) {
            nativeDestroy(nativeHandle)
            nativeHandle = 0
        }
    }

    fun isInitialized(): Boolean = nativeHandle != 0L

    // Native 方法声明
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeGenerate(
        handle: Long,
        prompt: String,
        maxTokens: Int
    ): String
    private external fun nativeDestroy(handle: Long)

    companion object {
        init {
            System.loadLibrary("llama-android")
        }
    }
}