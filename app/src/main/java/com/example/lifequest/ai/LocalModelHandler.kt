package com.example.lifequest.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * LocalModelHandler - 高级模型管理器
 * 使用 LlamaInference 作为底层，提供更高级的功能
 */
class LocalModelHandler(private val context: Context) {

    companion object {
        private const val TAG = "LocalModelHandler"
        private const val DEFAULT_MAX_TOKENS = 512
        private const val DEFAULT_TEMPERATURE = 0.7f
    }

    // ✅ 使用 LlamaInference 作为底层
    private var llamaInference: LlamaInference? = null
    private var isInitialized = false
    private var modelPath: String? = null
    private val useMockMode = false // 使用模拟模式，测试阶段先不开启

    /**
     * 初始化模型
     */
    suspend fun initialize(modelFilePath: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Log.d(TAG, "Model already initialized")
                return@withContext true
            }

            val path = modelFilePath ?: getDefaultModelPath()

            if (path == null) {
                Log.e(TAG, "Model path is null")
                return@withContext false
            }

            val modelFile = File(path)
            if (!modelFile.exists()) {
                Log.e(TAG, "Model file not found: $path")
                return@withContext false
            }

            Log.d(TAG, "Initializing model from: $path")

            if (useMockMode) {
                Log.d(TAG, "Using mock mode")
                isInitialized = true
                modelPath = path
                return@withContext true
            }

            // ✅ 使用 LlamaInference 初始化
            llamaInference = LlamaInference()
            val success = llamaInference?.initialize(path) ?: false

            if (success) {
                isInitialized = true
                modelPath = path
                Log.d(TAG, "Model initialized successfully")
                true
            } else {
                Log.e(TAG, "Failed to initialize model")
                llamaInference = null
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing model", e)
            false
        }
    }

    /**
     * 生成回复
     */
    suspend fun generate(
        prompt: String,
        maxTokens: Int = 100,
        temperature: Float = DEFAULT_TEMPERATURE,
        systemPrompt: String = ""
    ): String = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "=== LocalModelHandler.generate START ===")
            Log.d(TAG, "Mode: ${if (useMockMode) "MOCK" else "REAL MODEL"}")
            Log.d(TAG, "Max tokens: $maxTokens")
            Log.d(TAG, "Prompt length: ${prompt.length}")
            Log.d(TAG, "System prompt length: ${systemPrompt.length}")
            Log.d(TAG, "Start time: ${System.currentTimeMillis()}")

            if (!isInitialized) {
                Log.e(TAG, "Model not initialized")
                return@withContext ""
            }
            val startTime = System.currentTimeMillis()

//            Log.d(TAG, "Generating response for: $prompt")

            val fullPrompt = if (systemPrompt.isNotEmpty()) {
                """
                <|system|>
                $systemPrompt
                <|end|>
                <|user|>
                $prompt
                <|end|>
                <|assistant|>
                """.trimIndent()
            } else {
                prompt
            }

            Log.d(TAG, "Full prompt length: ${fullPrompt.length}")
            Log.d(TAG, "Full prompt preview: $fullPrompt")

            val response = if (useMockMode) {
                Log.d(TAG, "Using MOCK mode")
                generateMockResponse(prompt)
            } else {
                Log.d(TAG, "Using REAL MODEL")

                // 构建完整 prompt
                val fullPrompt = if (systemPrompt.isNotEmpty()) {
                    """
                    <|system|>
                    $systemPrompt
                    <|end|>
                    <|user|>
                    $prompt
                    <|end|>
                    <|assistant|>
                    """.trimIndent()
                } else {
                    prompt
                }

                Log.d(TAG, "Full prompt length: ${fullPrompt.length}")
                Log.d(TAG, "Full prompt preview: $fullPrompt")

                try {
                    Log.d(TAG, "Calling llamaInference.generate()...")
                    val inferenceStart = System.currentTimeMillis()

                    val result = llamaInference?.generate(fullPrompt, maxTokens)

                    val inferenceDuration = System.currentTimeMillis() - inferenceStart
                    Log.d(TAG, "Native inference took: ${inferenceDuration}ms")

                    if (result.isNullOrEmpty()) {
                        Log.w(TAG, "⚠️ Native inference returned empty, using mock")
                        generateMockResponse(prompt)
                    } else {
                        Log.d(TAG, "✅ Native inference success")
                        result
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Native inference error", e)
                    Log.e(TAG, "Error type: ${e.javaClass.simpleName}")
                    Log.e(TAG, "Error message: ${e.message}")
                    generateMockResponse(prompt)
                }
            }

            val totalDuration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Total generation time: ${totalDuration}ms (${totalDuration / 1000.0}s)")
            Log.d(TAG, "Response length: ${response.length}")
            Log.d(TAG, "Response preview: $response")
            Log.d(TAG, "Tokens per second: ${if (totalDuration > 0) maxTokens * 1000.0 / totalDuration else 0}")
            Log.d(TAG, "=== LocalModelHandler.generate END ===")

            // ✅ 性能分析
            when {
                totalDuration < 1000 -> Log.d(TAG, "✅ Performance: EXCELLENT")
                totalDuration < 5000 -> Log.d(TAG, "✅ Performance: GOOD")
                totalDuration < 15000 -> Log.w(TAG, "⚠️ Performance: ACCEPTABLE")
                else -> Log.e(TAG, "❌ Performance: POOR (${totalDuration / 1000}s)")
            }

            response
        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            "抱歉，生成回复时出现错误：${e.message}"
        }
    }

    /**
     * 生成模拟回复
     */
    private fun generateMockResponse(prompt: String): String {
        val lowerPrompt = prompt.lowercase()
        return when {
            lowerPrompt.contains("帮助") || lowerPrompt.contains("怎么用") -> {
                """
                我可以帮你：
                ✨ 创建任务 - 告诉我你想做什么，我会帮你创建合适的任务。
                📋 任务建议 - 我可以根据你的情况提供任务规划建议。
                💪 鼓励支持 - 在你需要的时候给予鼓励和支持。
                
                试试对我说：
                • "创建主线任务：完成项目报告"
                • "帮我制定学习计划"
                • "我想养成早起的习惯"
                """.trimIndent()
            }
            lowerPrompt.contains("计划") || lowerPrompt.contains("规划") -> {
                """
                制定计划是个好主意！
                建议你：
                1. 先确定主要目标
                2. 分解成小任务
                3. 设定合理的时间
                4. 每天完成一点
                
                告诉我你的具体目标，我可以帮你创建任务！
                """.trimIndent()
            }
            lowerPrompt.contains("习惯") || lowerPrompt.contains("坚持") -> {
                """
                养成好习惯需要时间和坚持！
                小建议：
                • 从小目标开始
                • 每天固定时间做
                • 记录你的进度
                • 给自己奖励
                
                我可以帮你创建每日任务来追踪习惯养成！
                """.trimIndent()
            }
            lowerPrompt.contains("谢谢") || lowerPrompt.contains("感谢") -> {
                "不客气！很高兴能帮到你！\n如果还需要什么帮助，随时告诉我！"
            }
            lowerPrompt.contains("你好") || lowerPrompt.contains("hi") || lowerPrompt.contains("hello") -> {
                "你好！我是 LifeQuest AI 助手\n我可以帮你管理任务、制定计划。告诉我你想做什么吧！"
            }
            else -> {
                "收到！我会帮你处理的。\n如果需要创建任务，请告诉我具体内容！"
            }
        }
    }

    /**
     * 检查是否准备就绪
     */
    fun isReady(): Boolean = isInitialized

    /**
     * 检查模型是否已初始化
     */
    fun isModelReady(): Boolean = isInitialized

    /**
     * 获取模型路径
     */
    fun getModelPath(): String? = modelPath

    /**
     * 是否使用模拟模式
     */
    fun isMockMode(): Boolean = useMockMode

    /**
     * 释放资源
     */
    fun release() {
        try {
            Log.d(TAG, "Releasing model resources...")

            // ✅ 使用 LlamaInference 释放
            llamaInference?.destroy()
            llamaInference = null

            isInitialized = false
            modelPath = null
            Log.d(TAG, "Model resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing model", e)
        }
    }

    /**
     * 获取默认模型路径
     */
    private fun getDefaultModelPath(): String? {
        val modelFile = File(context.filesDir, "ai_models/model.gguf")
        return if (modelFile.exists()) modelFile.absolutePath else null
    }
}