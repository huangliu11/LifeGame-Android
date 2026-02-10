package com.example.lifequest.data.dao

import androidx.room.*
import com.example.lifequest.data.entity.TaskEntity
import com.example.lifequest.data.entity.TaskType
import kotlinx.coroutines.flow.Flow

/**
 * 任务数据访问对象
 */
@Dao
interface TaskDao {

    /**
     * 获取所有任务
     */
    @Query("SELECT * FROM tasks ORDER BY createdAt DESC")
    fun getAllTasks(): Flow<List<TaskEntity>>

    /**
     * 根据类型获取任务
     */
    @Query("SELECT * FROM tasks WHERE type = :type ORDER BY createdAt DESC")
    fun getTasksByType(type: TaskType): Flow<List<TaskEntity>>

    /**
     * 获取未完成的任务
     */
    @Query("SELECT * FROM tasks WHERE isCompleted = 0 ORDER BY createdAt DESC")
    fun getIncompleteTasks(): Flow<List<TaskEntity>>

    /**
     * 获取已完成的任务
     */
    @Query("SELECT * FROM tasks WHERE isCompleted = 1 ORDER BY completedAt DESC")
    fun getCompletedTasks(): Flow<List<TaskEntity>>

    /**
     * 根据 ID 获取任务
     */
    @Query("SELECT * FROM tasks WHERE id = :taskId")
    suspend fun getTaskById(taskId: String): TaskEntity?

    /**
     * 插入任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    /**
     * 插入多个任务
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskEntity>)

    /**
     * 更新任务
     */
    @Update
    suspend fun updateTask(task: TaskEntity)

    /**
     * 删除任务
     */
    @Delete
    suspend fun deleteTask(task: TaskEntity)

    /**
     * 根据 ID 删除任务
     */
    @Query("DELETE FROM tasks WHERE id = :taskId")
    suspend fun deleteTaskById(taskId: String)

    /**
     * 删除所有已完成的任务
     */
    @Query("DELETE FROM tasks WHERE isCompleted = 1")
    suspend fun deleteCompletedTasks()

    /**
     * 删除所有任务
     */
    @Query("DELETE FROM tasks")
    suspend fun deleteAllTasks()

    /**
     * 获取任务总数
     */
    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun getTaskCount(): Int

    /**
     * 获取已完成任务数
     */
    @Query("SELECT COUNT(*) FROM tasks WHERE isCompleted = 1")
    suspend fun getCompletedTaskCount(): Int
}
