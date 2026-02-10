package com.example.lifequest.ai

import android.util.Log

class TaskParser(private val modelHandler: LocalModelHandler) {

    companion object {
        private const val TAG = "TaskParser"
    }

    /**
     * 从用户消息中解析任务
     */
    suspend fun parseTaskFromMessage(message: String): TaskInfo? {
        return try {
            Log.d(TAG, "Parsing task from: $message")

            if (!modelHandler.isReady()) {
                Log.e(TAG, "Model not ready")
                return null
            }

            // ✅ 混合策略：AI 提取标题 + 规则判断类型
            extractTaskInfoHybrid(message)

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing task", e)
            null
        }
    }

    /**
     * 生成 AI 回复
     */
    suspend fun generateResponse(message: String, maxTokens: Int = 200): String? {
        return try {
            if (!modelHandler.isReady()) {
                Log.e(TAG, "Model not ready")
                return null
            }

            Log.d(TAG, "Generating response...")
            val startTime = System.currentTimeMillis()
            val response = modelHandler.generate(message, maxTokens = maxTokens)
            val duration = System.currentTimeMillis() - startTime

            Log.d(TAG, "Generation: ${duration}ms, length: ${response?.length ?: 0}")

            if (duration > 10000) {
                Log.w(TAG, "⚠️ Generation took more than 10 seconds!")
            }

            response

        } catch (e: Exception) {
            Log.e(TAG, "Error generating response", e)
            null
        }
    }

    /**
     * ✅ 混合策略：AI 提取标题 + 规则判断类型
     */
    private suspend fun extractTaskInfoHybrid(message: String): TaskInfo? {
        Log.d(TAG, "=== Hybrid extraction: AI for title, rules for type ===")

        // 1. 先用规则检查是否包含任务关键词
        if (!containsTaskKeywords(message)) {
            Log.d(TAG, "No task keywords found")
            return null
        }

        // 2. 用 AI 提取简洁的标题
        val aiTitle = extractTitleWithAI(message)

        // 3. 如果 AI 失败，降级到规则提取
        val title = if (aiTitle.isNullOrBlank() || aiTitle.length < 2) {
            Log.w(TAG, "AI title extraction failed, using rules")
            extractTitleWithRules(message)
        } else {
            aiTitle
        }

        if (title.isBlank() || title.length < 2) {
            Log.d(TAG, "Title too short or empty: '$title'")
            return null
        }

        // 4. 用规则判断类型
        val type = determineTaskType(message)

        // 5. 固定描述
        val description = "通过聊天提取的任务"

        Log.d(TAG, "✅ Task extracted successfully")
        Log.d(TAG, "   Title: $title")
        Log.d(TAG, "   Type: $type")
        Log.d(TAG, "   Description: $description")

        return TaskInfo(
            title = title,
            description = description,
            type = type
        )
    }

    /**
     * ✅ 用 AI 提取简洁的标题
     */
    private suspend fun extractTitleWithAI(message: String): String? {
        try {
            Log.d(TAG, "=== Extracting title with AI ===")

            // 构建极简提示词
            val prompt = buildTitleExtractionPrompt(message)
            Log.d(TAG, "Prompt: $prompt")

            // 调用 AI（限制 token 数量）
            val response = modelHandler.generate(prompt, maxTokens = 30)

            if (response.isNullOrEmpty()) {
                Log.e(TAG, "AI returned empty response")
                return null
            }

            Log.d(TAG, "AI response: $response")

            // 解析 AI 的响应
            val title = parseAITitleResponse(response)

            if (title != null) {
                Log.d(TAG, "✅ AI extracted title: $title")
            } else {
                Log.d(TAG, "❌ Failed to parse AI response")
            }

            return title

        } catch (e: Exception) {
            Log.e(TAG, "Error in extractTitleWithAI", e)
            return null
        }
    }

    /**
     * ✅ 构建标题提取的极简提示词
     */
    private fun buildTitleExtractionPrompt(message: String): String {
        return """
从用户消息中提取任务标题。

用户说：帮我建立主线任务，我希望在3月前找到新工作
标题：3月前找到新工作

用户说：创建每日任务：每天跑步30分钟
标题：每天跑步30分钟

用户说：我想学习Python编程
标题：学习Python编程

用户说：$message
标题：
""".trimIndent()
    }

    /**
     * ✅ 解析 AI 返回的标题
     */
    private fun parseAITitleResponse(response: String): String? {
        try {
            // 清理响应
            var title = response.trim()

            // 移除可能的前缀
            val prefixes = listOf(
                "输出：", "输出:", "标题：", "标题:",
                "任务标题：", "任务标题:", "任务：", "任务:", ":"
            )
            for (prefix in prefixes) {
                if (title.startsWith(prefix)) {
                    title = title.substring(prefix.length).trim()
                }
            }

            // 只取第一行
            title = title.lines().firstOrNull()?.trim() ?: ""

            // 移除引号
            title = title.removeSurrounding("\"").removeSurrounding("'")

            // 移除多余的标点
            title = title.trimEnd('。', '！', '？', '.', '!', '?')

            // 验证长度
            if (title.length < 2 || title.length > 50) {
                Log.w(TAG, "Title length invalid: ${title.length}")
                return null
            }

            // 验证不是无意义的回复
            val invalidTitles = listOf(
                "无", "无任务", "没有", "不知道", "不清楚",
                "无法提取", "提取失败", "错误"
            )
            if (invalidTitles.any { title.contains(it) }) {
                Log.w(TAG, "Title is invalid: $title")
                return null
            }

            Log.d(TAG, "Parsed AI title: $title")
            return title

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing AI title response", e)
            return null
        }
    }

    /**
     * ✅ 用规则提取标题（降级方案）
     */
    private fun extractTitleWithRules(message: String): String {
        var title = message

        Log.d(TAG, "Extracting title with rules...")

        // 移除任务类型前缀
        val taskTypePrefixes = listOf(
            "主线任务", "支线任务", "每日任务", "日常任务", "任务"
        )
        for (prefix in taskTypePrefixes) {
            title = title.replace(prefix, " ")
        }

        // 移除动作前缀
        val actionPrefixes = listOf(
            "帮我", "请", "麻烦", "能不能", "可以",
            "建立一个", "创建一个", "添加一个", "新建一个",
            "建立", "创建", "添加", "新建"
        )
        for (prefix in actionPrefixes) {
            title = title.replace(prefix, " ")
        }

        // 处理意图表达
        val intentPhrases = listOf(
            "我希望", "我想要", "我想", "我要", "我打算"
        )
        for (phrase in intentPhrases) {
            if (title.contains(phrase)) {
                title = title.substringAfter(phrase).trim()
                break
            }
        }

        // 处理时间表达
        if (title.startsWith("在") && title.contains("前")) {
            title = title.substring(1).trim()
        }

        // 移除标点符号
        title = title
            .replace("，", " ")
            .replace("。", " ")
            .replace("！", " ")
            .replace("？", " ")
            .replace("：", " ")
            .replace(":", " ")
            .trim()

        // 清理多余空格
        title = title.replace(Regex("\\s+"), " ").trim()

        // 限制长度
        if (title.length > 30) {
            title = title.take(30)
        }

        Log.d(TAG, "Rule-based title: $title")
        return title.ifBlank { "新任务" }
    }

    /**
     * ✅ 检查是否包含任务关键词
     */
    private fun containsTaskKeywords(message: String): Boolean {
        val taskKeywords = listOf(
            "任务", "创建", "添加", "建立", "新建", "做", "完成", "开始",
            "学习", "练习", "跑步", "阅读", "写", "准备", "复习",
            "计划", "目标", "希望", "想要", "想", "要", "打算"
        )

        val lowerMessage = message.lowercase()
        val hasKeyword = taskKeywords.any { lowerMessage.contains(it) }

        Log.d(TAG, "Contains task keywords: $hasKeyword")
        return hasKeyword
    }

    /**
     * ✅ 用规则判断任务类型
     */
    private fun determineTaskType(message: String): String {
        val lowerMessage = message.lowercase()

        val type = when {
            // 主线任务关键词
            lowerMessage.contains("主线") ||
                    lowerMessage.contains("重要") ||
                    lowerMessage.contains("紧急") ||
                    lowerMessage.contains("必须") ||
                    lowerMessage.contains("deadline") ||
                    lowerMessage.contains("截止") ||
                    lowerMessage.contains("工作") ||
                    lowerMessage.contains("项目") ||
                    lowerMessage.contains("报告") ||
                    lowerMessage.contains("面试") ||
                    lowerMessage.contains("求职") -> "MAIN"

            // 每日任务关键词
            lowerMessage.contains("每日") ||
                    lowerMessage.contains("每天") ||
                    lowerMessage.contains("日常") ||
                    lowerMessage.contains("习惯") ||
                    lowerMessage.contains("坚持") ||
                    lowerMessage.contains("跑步") ||
                    lowerMessage.contains("运动") ||
                    lowerMessage.contains("锻炼") -> "DAILY"

            // 支线任务（默认）
            else -> "SIDE"
        }

        Log.d(TAG, "Determined type: $type")
        return type
    }

    /**
     * ✅ 判断用户意图
     */
    suspend fun detectUserIntent(message: String): UserIntent {
        // 1. 规则优先判断
        val lowerMessage = message.lowercase()

        // 明确的任务创建关键词
        val taskKeywords = listOf("创建", "建立", "添加", "新建", "帮我", "任务")
        val hasTaskKeyword = taskKeywords.any { lowerMessage.contains(it) }

        // 咨询类关键词
        val questionKeywords = listOf("怎么", "如何", "什么", "为什么", "能不能", "可以", "?", "？")
        val hasQuestionKeyword = questionKeywords.any { lowerMessage.contains(it) }

        return when {
            // 明确的任务创建意图
            hasTaskKeyword && !hasQuestionKeyword -> UserIntent.CREATE_TASK

            // 明确的咨询意图
            hasQuestionKeyword -> UserIntent.QUESTION

            // 模糊情况，用 AI 判断
            else -> detectIntentWithAI(message)
        }
    }

    /**
     * ✅ 用 AI 判断意图（仅在模糊情况下使用）
     */
    private suspend fun detectIntentWithAI(message: String): UserIntent {
        try {
            val prompt = """判断用户意图，只回答"任务"或"咨询"。

用户说：创建主线任务学习Python
意图：任务

用户说：怎么养成早起习惯
意图：咨询

用户说：我想学习Python
意图：任务

用户说：$message
意图：""".trimIndent()

            val response = modelHandler.generate(prompt, maxTokens = 5)?.trim()?.lowercase()

            return when {
                response?.contains("任务") == true -> UserIntent.CREATE_TASK
                response?.contains("咨询") == true -> UserIntent.QUESTION
                else -> UserIntent.QUESTION // 默认当作咨询
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting intent", e)
            return UserIntent.QUESTION
        }
    }

    /**
     * 释放资源
     */
    fun release() {
        // 不需要释放 modelHandler
    }
}
