package com.example.lifequest.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 任务实体类
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey  // ✅ 移除 autoGenerate，因为我们使用 String UUID
    val id: String,  // ✅ String 类型，不能用 autoGenerate
    val title: String,
    val description: String = "",
    val type: TaskType = TaskType.SIDE,
    val coinReward: Int = 50,
    val expReward: Int = 25,
    val isCompleted: Boolean = false,
    val priority: Int = 0,
    val dueDate: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null
)

/**
 * 任务类型枚举
 */
enum class TaskType {
    MAIN,   // 主线任务
    SIDE,   // 支线任务
    DAILY   // 每日任务
}
