package com.example.lifequest.data

import androidx.room.TypeConverter
import com.example.lifequest.data.entity.TaskType

/**
 * Room 数据库类型转换器
 */
class Converters {

    /**
     * TaskType 转 String
     */
    @TypeConverter
    fun fromTaskType(value: TaskType): String {
        return value.name
    }

    /**
     * String 转 TaskType
     */
    @TypeConverter
    fun toTaskType(value: String): TaskType {
        return try {
            TaskType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            TaskType.SIDE
        }
    }
}
