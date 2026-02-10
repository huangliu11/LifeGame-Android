package com.example.lifequest.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifequest.ai.LocalModelHandler
import com.example.lifequest.ai.ModelFileManager
import com.example.lifequest.ai.TaskParser
import com.example.lifequest.ai.UserIntent
import com.example.lifequest.data.entity.TaskEntity
import com.example.lifequest.data.entity.TaskType
import com.example.lifequest.data.entity.RewardItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*

/**
 * 用户统计数据
 */
data class UserStats(
    val level: Int = 1,
    val exp: Int = 0,
    val coins: Int = 0,
    val totalTasksCompleted: Int = 0,
    val streak: Int = 0 // 连续完成天数
)

/**
 * 聊天消息
 */
data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
    val type: MessageType = MessageType.TEXT
)

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,           // 普通文本
    TASK_CREATED,   // 任务创建通知
    TASK_COMPLETED, // 任务完成通知
    LEVEL_UP,       // 升级通知
    SYSTEM          // 系统消息
}

/**
 * AI 模型状态
 */
enum class ModelState {
    UNINITIALIZED,  // 未初始化
    CHECKING,       // 检查中
    LOADING,        // 加载中
    READY,          // 就绪
    ERROR,          // 错误
    NOT_FOUND       // 未找到模型
}

/**
 * 主 ViewModel
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "MainViewModel"
        private const val EXP_PER_LEVEL = 100
        private const val MAX_CHAT_HISTORY = 100 // 限制聊天历史数量
    }

    // AI 模型处理器
    private var modelHandler: LocalModelHandler? = null
    private var taskMessageParser: TaskParser? = null

    // 模型状态
    private val _modelState = MutableStateFlow(ModelState.UNINITIALIZED)
    val modelState: StateFlow<ModelState> = _modelState.asStateFlow()

    // 用户数据
    private val _userStats = MutableStateFlow(UserStats())
    val userStats: StateFlow<UserStats> = _userStats.asStateFlow()

    // 任务列表
    private val _tasks = MutableStateFlow<List<TaskEntity>>(emptyList())
    val tasks: StateFlow<List<TaskEntity>> = _tasks.asStateFlow()

    // 聊天消息
    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    // 奖励列表
    private val _rewards = MutableStateFlow<List<RewardItem>>(emptyList())
    val rewards: StateFlow<List<RewardItem>> = _rewards.asStateFlow()

    // 加载状态
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 错误消息
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadInitialData()
        initializeAIModel()
    }

    /**
     * 加载初始数据
     */
    private fun loadInitialData() {
        _rewards.value = listOf(
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "看一集电视剧",
                description = "完成任务后的放松时光",
                coinCost = 50
            ),
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "买一杯奶茶",
                description = "奖励自己一杯喜欢的饮品",
                coinCost = 100
            ),
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "玩一小时游戏",
                description = "尽情享受游戏时光",
                coinCost = 150
            ),
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "买一本喜欢的书",
                description = "充实自己的精神世界",
                coinCost = 200
            ),
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "周末出游",
                description = "计划一次短途旅行",
                coinCost = 500
            ),
            RewardItem(
                id = UUID.randomUUID().toString(),
                name = "购买心仪商品",
                description = "奖励自己一件想要的东西",
                coinCost = 1000
            )
        )
    }

    /**
     * 初始化 AI 模型
     */
    private fun initializeAIModel() {
        viewModelScope.launch {
            try {
                _modelState.value = ModelState.CHECKING
                Log.d(TAG, "Checking model availability...")

                // 检查模型是否存在
                val modelExists = withContext(Dispatchers.IO) {
                    ModelFileManager.isModelExists(getApplication())
                }

                if (!modelExists) {
                    _modelState.value = ModelState.NOT_FOUND
                    Log.w(TAG, "Model file not found")
                    addSystemMessage(
                        "⚠️ AI 模型未安装\n" +
                                "当前使用简化模式，功能受限。" +
                        "请前往设置页面安装模型以使用完整功能。"
                    )
                    return@launch
                }

                // 加载模型
                _modelState.value = ModelState.LOADING
                Log.d(TAG, "Loading AI model...")

                modelHandler = LocalModelHandler(getApplication())
                val success = withContext(Dispatchers.IO) {
                    modelHandler?.initialize() ?: false
                }

                if (success) {
                    // 关键：初始化 taskMessageParser
                    Log.d(TAG, "Initializing TaskParser...")
                    taskMessageParser = TaskParser(modelHandler!!)
                    Log.d(TAG, "TaskParser initialized: ${taskMessageParser != null}")

                    _modelState.value = ModelState.READY
                    Log.d(TAG, "AI model initialized successfully")
                    addSystemMessage(
                        "✅ AI 模型已就绪" +
                        "你好！我是 LifeQuest AI 助手 🤖\n" +
                    "我可以帮你：" +
                    "• 智能创建和管理任务" +
                    "• 提供个性化建议" +
                    "• 规划时间安排" +
                    "• 激励和鼓励你\n" +
                    "告诉我你想做什么吧！"
                    )
                } else {
                    _modelState.value = ModelState.ERROR
                    Log.e(TAG, "Failed to initialize AI model")
                    addSystemMessage(
                        "❌ AI 模型加载失败" +
                        "将使用简化模式。"
                    )
                }
            } catch (e: Exception) {
                _modelState.value = ModelState.ERROR
                Log.e(TAG, "Error initializing AI model", e)
                _errorMessage.value = "模型初始化失败: ${e.message}"
                addSystemMessage(
                    "❌ 模型加载出错" +
                    "错误: ${e.message}\n" +
                            "将使用简化模式。"
                )
            }
        }
    }

    /**
     * 发送聊天消息
     */
    fun sendChatMessage(message: String) {
        if (message.isBlank()) return

        viewModelScope.launch {
            try {
                // 添加用户消息
                addUserMessage(message)
                _isLoading.value = true
                _errorMessage.value = null

                // 根据模型状态选择处理方式
                when (_modelState.value) {
                    ModelState.READY -> handleMessageWithAI(message)
                    else -> handleMessageWithoutAI(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling message", e)
                _errorMessage.value = "处理消息失败: ${e.message}"
                addAssistantMessage(
                    "抱歉，处理消息时出现错误：${e.message}\n" +
                            "请稍后重试。"
                )
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * 使用 AI 模型处理消息
     */
    private suspend fun handleMessageWithAI(message: String) {
        withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "Processing message with AI: $message")

                if (taskMessageParser == null) {
                    Log.e(TAG, "taskMessageParser is NULL!")
                    withContext(Dispatchers.Main) { handleMessageWithoutAI(message) }
                    return@withContext
                }

                // ✅ 第一步：判断用户意图
                val intent = taskMessageParser?.detectUserIntent(message)
                Log.d(TAG, "Detected intent: $intent")

                when (intent) {
                    UserIntent.CREATE_TASK -> {
                        // 尝试解析并创建任务
                        val taskInfo = taskMessageParser?.parseTaskFromMessage(message)
                        if (taskInfo != null && taskInfo.title.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                createTaskFromAI(taskInfo)
                            }

                            // 生成确认消息
                            val confirmPrompt = """用户创建了任务：${taskInfo.title}
请用20字内确认并鼓励。""".trimIndent()

                            val response = withTimeoutOrNull(15000) {
                                taskMessageParser?.generateResponse(confirmPrompt, maxTokens = 50)
                            }

                            withContext(Dispatchers.Main) {
                                addAssistantMessage(
                                    response ?: "✅ 任务「${taskInfo.title}」已创建！加油！💪"
                                )
                            }
                        } else {
                            // 解析失败，给出提示
                            withContext(Dispatchers.Main) {
                                addAssistantMessage("我没理解清楚，请告诉我具体要做什么任务？")
                            }
                        }
                    }

                    UserIntent.QUESTION -> {
                        // ✅ 第二步：回答咨询问题
                        val systemPrompt = buildSystemPrompt()
                        val fullPrompt = """$systemPrompt

用户问：$message
回复（30字内）：""".trimIndent()

                        val response = withTimeoutOrNull(20000) {
                            taskMessageParser?.generateResponse(fullPrompt, maxTokens = 80)
                        }

                        withContext(Dispatchers.Main) {
                            if (response.isNullOrBlank()) {
                                // AI 失败，降级到规则回复
                                handleMessageWithoutAI(message)
                            } else {
                                addAssistantMessage(response)
                            }
                        }
                    }

                    else -> {
                        withContext(Dispatchers.Main) {
                            addAssistantMessage("我可以帮你创建任务或回答问题，请告诉我你需要什么？")
                        }
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error in AI handling", e)
                withContext(Dispatchers.Main) {
                    handleMessageWithoutAI(message)
                }
            }
        }
    }


    /**
     * 构建系统提示
     */
    private fun buildSystemPrompt(): String {
        val stats = _userStats.value
        val taskCount = _tasks.value.size
        val completedCount = _tasks.value.count { it.isCompleted }

        return """
            你是 LifeQuest 的 AI 助手，一个帮助用户管理任务和提升效率的智能助手。
     
            你的职责：
            1. 帮助用户创建和管理任务
            2. 提供积极的鼓励和建议
            3. 回答用户关于任务管理的问题
            4. 保持友好、简洁的对话风格
            
            回复要求：
            - 简洁明了，不超过50字
            - 使用友好、鼓励的语气
            - 适当使用 emoji 增加趣味性
            - 中文回复
        """.trimIndent()
    }

    /**
     * 不使用 AI 的简单处理
     */
    private fun handleMessageWithoutAI(message: String) {
        val lowerMessage = message.lowercase()

        val response = when {
            lowerMessage.contains("主线") || lowerMessage.contains("重要") -> {
                createSimpleTask(message, TaskType.MAIN)
                "✅ 已创建主线任务！\n\n" +
                        "主线任务是重要且紧急的事项，完成后可获得：" +
                "💰 100 金币 + ⭐ 50 经验值" +
                "加油完成它吧！💪"
            }

            lowerMessage.contains("支线") || lowerMessage.contains("学习") -> {
                createSimpleTask(message, TaskType.SIDE)
                "✅ 已创建支线任务！" +
                "支线任务帮助你提升技能，完成后可获得：" +
                "💰 50 金币 + ⭐ 25 经验值" +
                "慢慢来，不要着急！📚"
            }

            lowerMessage.contains("每日") || lowerMessage.contains("日常") || lowerMessage.contains("习惯") -> {
                createSimpleTask(message, TaskType.DAILY)
                "✅ 已创建每日任务！" +
                "坚持每天完成可以养成好习惯，完成后可获得：\n" +
                        "💰 20 金币 + ⭐ 10 经验值" +
                "持之以恒最重要！✨"
            }

            lowerMessage.contains("帮助") || lowerMessage.contains("怎么用") || lowerMessage.contains("使用") -> {
                getHelpMessage()
            }

            lowerMessage.contains("统计") || lowerMessage.contains("数据") -> {
                getStatsMessage()
            }

            lowerMessage.contains("删除") || lowerMessage.contains("取消") -> {
                "要删除任务，请在任务列表中点击删除按钮即可。\n" +
                "如果需要帮助，随时告诉我！😊"
            }

            else -> {
                createSimpleTask(message, TaskType.SIDE)
                "✅ 已为你创建任务！" +
                "你可以在任务列表中查看和管理它。" +
                "继续加油！🎯"
            }
        }

        addAssistantMessage(response)
    }

    /**
     * 获取帮助信息
     */
    private fun getHelpMessage(): String {
        val modelStatus = when (_modelState.value) {
            ModelState.READY -> "✅ 已启用（完整功能）"
            ModelState.NOT_FOUND -> "⚠️ 未安装（简化模式）"
            ModelState.ERROR -> "❌ 加载失败（简化模式）"
            else -> "🔄 ${_modelState.value}"
        }

        return """
            📖 LifeQuest 使用指南
            
            🎯 创建任务
            告诉我你想做什么，例如：
            • "创建主线任务：完成毕业论文"
            • "每日任务：晨跑30分钟"
            • "学习 Kotlin 编程"
            
            📊 任务类型
            • 主线任务：重要紧急（100💰 + 50⭐）
            • 支线任务：技能提升（50💰 + 25⭐）
            • 每日任务：习惯养成（20💰 + 10⭐）
            
            ✅ 完成任务
            在任务列表中勾选完成，获得奖励
            
            🎁 兑换奖励
            用金币兑换你喜欢的奖励
            
            🤖 AI 状态
            $modelStatus
            
            💡 提示：输入"统计"查看你的数据
        """.trimIndent()
    }

    /**
     * 获取统计信息
     */
    private fun getStatsMessage(): String {
        val stats = _userStats.value
        val totalTasks = _tasks.value.size
        val completedTasks = _tasks.value.count { it.isCompleted }
        val completionRate = if (totalTasks > 0) {
            (completedTasks * 100 / totalTasks)
        } else 0

        return """
            📊 你的数据统计
            
            👤 等级信息
            • 当前等级：Lv.${stats.level}
            • 经验值：${stats.exp}/${EXP_PER_LEVEL}
            • 进度：${stats.exp * 100 / EXP_PER_LEVEL}%
            
            💰 财富状况
            • 金币余额：${stats.coins}
            • 总完成任务：${stats.totalTasksCompleted}
            
            📝 任务情况
            • 总任务数：$totalTasks
            • 已完成：$completedTasks
            • 完成率：$completionRate%
            • 进行中：${totalTasks - completedTasks}
            
            ${if (stats.streak > 0) "🔥 连续完成：${stats.streak} 天" else ""}
            
            继续保持，你做得很棒！💪
        """.trimIndent()
    }

    /**
     * 从 AI 解析的信息创建任务
     */
    private fun createTaskFromAI(taskInfo: com.example.lifequest.ai.TaskInfo) {
        val taskType = when (taskInfo.type.uppercase()) {
            "MAIN" -> TaskType.MAIN
            "DAILY" -> TaskType.DAILY
            else -> TaskType.SIDE
        }

        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = taskInfo.title,
            description = taskInfo.description.ifEmpty { "AI 创建的任务" },
            type = taskType,
            coinReward = when (taskType) {
                TaskType.MAIN -> 100
                TaskType.SIDE -> 50
                TaskType.DAILY -> 20
            },
            expReward = when (taskType) {
                TaskType.MAIN -> 50
                TaskType.SIDE -> 25
                TaskType.DAILY -> 10
            },
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        _tasks.value = _tasks.value + task
        Log.d(TAG, "Task created from AI: ${task.title}")
    }

    /**
     * 简单创建任务
     */
    private fun createSimpleTask(message: String, type: TaskType) {
        val title = extractTaskTitle(message)

        if (title.isBlank() || title.length < 2) {
            Log.w(TAG, "Invalid task title extracted from: $message")
            return
        }

        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = "通过聊天创建的任务",
            type = type,
            coinReward = when (type) {
                TaskType.MAIN -> 100
                TaskType.SIDE -> 50
                TaskType.DAILY -> 20
            },
            expReward = when (type) {
                TaskType.MAIN -> 50
                TaskType.SIDE -> 25
                TaskType.DAILY -> 10
            },
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        _tasks.value = _tasks.value + task
        Log.d(TAG, "Simple task created: ${task.title}")
    }

    /**
     * 提取任务标题
     */
    private fun extractTaskTitle(message: String): String {
        var title = message
            .replace(Regex("创建|任务|主线|支线|每日|日常"), "")
            .replace(Regex("[：:]"), ":")
            .trim()

        if (title.contains(":")) {
            title = title.substringAfter(":").trim()
        }

        return title.take(50)
    }

    /**
     * 手动添加任务
     */
    fun addTask(title: String, type: TaskType) {
        if (title.isBlank()) return

        val task = TaskEntity(
            id = UUID.randomUUID().toString(),
            title = title,
            description = "手动创建的任务",
            type = type,
            coinReward = when (type) {
                TaskType.MAIN -> 100
                TaskType.SIDE -> 50
                TaskType.DAILY -> 20
            },
            expReward = when (type) {
                TaskType.MAIN -> 50
                TaskType.SIDE -> 25
                TaskType.DAILY -> 10
            },
            isCompleted = false,
            createdAt = System.currentTimeMillis()
        )

        _tasks.value = _tasks.value + task
        Log.d(TAG, "Manual task added: ${task.title}")
    }

    /**
     * 完成任务
     */
    fun completeTask(task: TaskEntity) {
        if (task.isCompleted) return

        viewModelScope.launch {
            // 更新任务状态
            val updatedTask = task.copy(isCompleted = true)
            _tasks.value = _tasks.value.map {
                if (it.id == task.id) updatedTask else it
            }

            // 更新用户统计
            val currentStats = _userStats.value
            val newExp = currentStats.exp + task.expReward
            val levelUps = newExp / EXP_PER_LEVEL
            val remainingExp = newExp % EXP_PER_LEVEL

            _userStats.value = currentStats.copy(
                coins = currentStats.coins + task.coinReward,
                exp = remainingExp,
                level = currentStats.level + levelUps,
                totalTasksCompleted = currentStats.totalTasksCompleted + 1
            )

            // 添加完成消息
            val message = if (levelUps > 0) {
                ChatMessage(
                    text = "🎉 恭喜！你完成了任务「${task.title}」\n\n" +
                            "获得奖励：\n" +
                            "💰 ${task.coinReward} 金币" +
                    "⭐ ${task.expReward} 经验值" +
                    "✨ 升级了！当前等级：Lv.${currentStats.level + levelUps}\n" +
                "太棒了！继续保持！🎊",
                isUser = false,
                type = MessageType.LEVEL_UP
                )
            } else {
                ChatMessage(
                    text = "✅ 完成了任务「${task.title}」" +
                    "获得奖励：" +
                    "💰 ${task.coinReward} 金币" +
                    "⭐ ${task.expReward} 经验值\n" +
                "干得漂亮！💪",
                isUser = false,
                type = MessageType.TASK_COMPLETED
                )
            }

            addMessage(message)
            Log.d(TAG, "Task completed: ${task.title}, Level ups: $levelUps")
        }
    }

    /**
     * 删除任务
     */
    fun deleteTask(task: TaskEntity) {
        _tasks.value = _tasks.value.filter { it.id != task.id }
        Log.d(TAG, "Task deleted: ${task.title}")
    }

    /**
     * 购买奖励
     */
    fun purchaseReward(reward: RewardItem) {
        if (reward.isPurchased) return

        viewModelScope.launch {
            val currentStats = _userStats.value

            if (currentStats.coins >= reward.coinCost) {
                // 扣除金币
                _userStats.value = currentStats.copy(
                    coins = currentStats.coins - reward.coinCost
                )

                // 标记为已购买
                _rewards.value = _rewards.value.map {
                    if (it.id == reward.id) it.copy(isPurchased = true) else it
                }

                // 添加购买消息
                addMessage(
                    ChatMessage(
                        text = "🎁 成功兑换奖励「${reward.name}」！" +
                        "花费：💰 ${reward.coinCost} 金币" +
                        "剩余：💰 ${currentStats.coins - reward.coinCost} 金币" +
                        "好好享受吧！😊",
                        isUser = false,
                        type = MessageType.SYSTEM
                    )
                )

                Log.d(TAG, "Reward purchased: ${reward.name}")
            } else {
                _errorMessage.value = "金币不足，还需要 ${reward.coinCost - currentStats.coins} 金币"
            }
        }
    }

    /**
     * 添加用户消息
     */
    private fun addUserMessage(text: String) {
        addMessage(ChatMessage(text = text, isUser = true))
    }

    /**
     * 添加助手消息
     */
    private fun addAssistantMessage(text: String) {
        addMessage(ChatMessage(text = text, isUser = false))
    }

    /**
     * 添加系统消息
     */
    private fun addSystemMessage(text: String) {
        addMessage(ChatMessage(text = text, isUser = false, type = MessageType.SYSTEM))
    }

    /**
     * 添加消息（限制历史数量）
     */
    private fun addMessage(message: ChatMessage) {
        _chatMessages.value = (_chatMessages.value + message).takeLast(MAX_CHAT_HISTORY)
    }

    /**
     * 清除错误消息
     */
    fun clearError() {
        _errorMessage.value = null
    }

    /**
     * 重新初始化模型
     */
    fun reinitializeModel() {
        modelHandler?.release()
        modelHandler = null
        _modelState.value = ModelState.UNINITIALIZED
        initializeAIModel()
    }

    /**
     * 清理资源
     */
    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared, releasing resources")
        modelHandler?.release()
        modelHandler = null
    }
}
