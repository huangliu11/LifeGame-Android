package com.example.lifequest.repository

import com.example.lifequest.data.dao.TaskDao
import com.example.lifequest.data.entity.TaskEntity
import com.example.lifequest.data.entity.TaskType
import kotlinx.coroutines.flow.Flow

/**
 * 任务数据仓库
 */
class TaskRepository(private val taskDao: TaskDao) {

    /**
     * 获取所有任务
     */
    fun getAllTasks(): Flow<List<TaskEntity>> {
        return taskDao.getAllTasks()
    }

    /**
     * 获取活跃任务（未完成的任务）
     */
    fun getActiveTasks(): Flow<List<TaskEntity>> {
        return taskDao.getIncompleteTasks()
    }

    /**
     * 获取未完成的任务
     */
    fun getIncompleteTasks(): Flow<List<TaskEntity>> {
        return taskDao.getIncompleteTasks()
    }

    /**
     * 获取已完成的任务
     */
    fun getCompletedTasks(): Flow<List<TaskEntity>> {
        return taskDao.getCompletedTasks()
    }

    /**
     * 根据类型获取任务
     */
    fun getTasksByType(type: TaskType): Flow<List<TaskEntity>> {
        return taskDao.getTasksByType(type)
    }

    /**
     * 根据 ID 获取任务
     */
    suspend fun getTaskById(taskId: String): TaskEntity? {
        return taskDao.getTaskById(taskId)
    }

    /**
     * 插入任务
     */
    suspend fun insertTask(task: TaskEntity) {
        taskDao.insertTask(task)
    }

    /**
     * 插入多个任务
     */
    suspend fun insertTasks(tasks: List<TaskEntity>) {
        taskDao.insertTasks(tasks)
    }

    /**
     * 更新任务
     */
    suspend fun updateTask(task: TaskEntity) {
        taskDao.updateTask(task)
    }

    /**
     * 完成任务
     */
    suspend fun completeTask(taskId: String) {
        val task = taskDao.getTaskById(taskId)
        task?.let {
            val completedTask = it.copy(
                isCompleted = true,
                completedAt = System.currentTimeMillis()
            )
            taskDao.updateTask(completedTask)
        }
    }

    /**
     * 删除任务
     */
    suspend fun deleteTask(task: TaskEntity) {
        taskDao.deleteTask(task)
    }

    /**
     * 根据 ID 删除任务
     */
    suspend fun deleteTaskById(taskId: String) {
        taskDao.deleteTaskById(taskId)
    }

    /**
     * 删除所有已完成的任务
     */
    suspend fun deleteCompletedTasks() {
        taskDao.deleteCompletedTasks()
    }

    /**
     * 删除所有任务
     */
    suspend fun deleteAllTasks() {
        taskDao.deleteAllTasks()
    }

    /**
     * 获取任务总数
     */
    suspend fun getTaskCount(): Int {
        return taskDao.getTaskCount()
    }

    /**
     * 获取已完成任务数
     */
    suspend fun getCompletedTaskCount(): Int {
        return taskDao.getCompletedTaskCount()
    }

    /**
     * 获取任务完成率
     */
    suspend fun getTaskCompletionRate(): Float {
        val total = getTaskCount()
        if (total == 0) return 0f
        val completed = getCompletedTaskCount()
        return completed.toFloat() / total.toFloat()
    }
}
