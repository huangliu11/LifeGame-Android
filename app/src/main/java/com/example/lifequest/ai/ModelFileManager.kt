package com.example.lifequest.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/**
 * AI 模型文件管理器
 * 负责模型文件的检查、复制、删除等操作
 */
object ModelFileManager {

    private const val TAG = "ModelFileManager"

    // 模型文件配置
    private const val ASSET_MODEL_DIR = "models"           // assets 中的目录
    private const val MODEL_FILE_NAME = "model.gguf"       // 模型文件名
    private const val INTERNAL_MODEL_DIR = "ai_models"     // 内部存储目录

    // 缓冲区大小
    private const val BUFFER_SIZE = 8192

    /**
     * 检查 assets 中是否有模型文件
     */
    fun hasAssetModel(context: Context): Boolean {
        return try {
            val assetFiles = context.assets.list(ASSET_MODEL_DIR) ?: emptyArray()
            val hasModel = assetFiles.any { it.endsWith(".gguf") }
            Log.d(TAG, "Asset model check: $hasModel, files: ${assetFiles.joinToString()}")
            hasModel
        } catch (e: Exception) {
            Log.e(TAG, "Error checking asset model", e)
            false
        }
    }

    /**
     * 检查模型是否已安装到内部存储
     */
    fun isModelExists(context: Context): Boolean {
        val modelFile = getModelFile(context)
        val exists = modelFile.exists() && modelFile.length() > 0
        Log.d(TAG, "Model exists check: $exists, path: ${modelFile.absolutePath}")
        return exists
    }

    /**
     * 获取模型文件对象
     */
    fun getModelFile(context: Context): File {
        val modelDir = File(context.filesDir, INTERNAL_MODEL_DIR)
        return File(modelDir, MODEL_FILE_NAME)
    }

    /**
     * 获取模型目录
     */
    fun getModelDir(context: Context): File {
        return File(context.filesDir, INTERNAL_MODEL_DIR)
    }

    /**
     * 获取模型文件大小（MB）
     */
    fun getModelSize(context: Context): Long {
        val modelFile = getModelFile(context)
        return if (modelFile.exists()) {
            modelFile.length() / (1024 * 1024) // 转换为 MB
        } else {
            0L
        }
    }

    /**
     * 获取 assets 中的模型文件名
     */
    fun getAssetModelFileName(context: Context): String? {
        return try {
            val assetFiles = context.assets.list(ASSET_MODEL_DIR) ?: emptyArray()
            assetFiles.firstOrNull { it.endsWith(".gguf") }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting asset model file name", e)
            null
        }
    }

    /**
     * 从 assets 复制模型到内部存储
     * @param progressCallback 进度回调 (0-100)
     */
    suspend fun copyModelFromAssets(
        context: Context,
        progressCallback: ((Int) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        var inputStream: InputStream? = null
        var outputStream: FileOutputStream? = null

        try {
            Log.d(TAG, "Starting model copy from assets...")

            // 获取 assets 中的模型文件名
            val assetFileName = getAssetModelFileName(context)
            if (assetFileName == null) {
                Log.e(TAG, "No .gguf file found in assets/$ASSET_MODEL_DIR")
                return@withContext false
            }

            val assetPath = "$ASSET_MODEL_DIR/$assetFileName"
            Log.d(TAG, "Asset model path: $assetPath")

            // 打开 asset 文件
            inputStream = context.assets.open(assetPath)
            val totalSize = inputStream.available().toLong()
            Log.d(TAG, "Model size: ${totalSize / (1024 * 1024)} MB")

            // 创建目标目录
            val modelDir = getModelDir(context)
            if (!modelDir.exists()) {
                modelDir.mkdirs()
                Log.d(TAG, "Created model directory: ${modelDir.absolutePath}")
            }

            // 创建目标文件
            val targetFile = getModelFile(context)
            if (targetFile.exists()) {
                Log.d(TAG, "Deleting existing model file")
                targetFile.delete()
            }

            outputStream = FileOutputStream(targetFile)

            // 复制文件并报告进度
            val buffer = ByteArray(BUFFER_SIZE)
            var bytesCopied = 0L
            var bytesRead: Int
            var lastProgress = 0

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                outputStream.write(buffer, 0, bytesRead)
                bytesCopied += bytesRead

                // 计算并报告进度
                val progress = ((bytesCopied * 100) / totalSize).toInt()
                if (progress != lastProgress && progress % 5 == 0) {
                    progressCallback?.invoke(progress)
                    lastProgress = progress
                    Log.d(TAG, "Copy progress: $progress%")
                }
            }

            outputStream.flush()
            progressCallback?.invoke(100)

            Log.d(TAG, "Model copied successfully to: ${targetFile.absolutePath}")
            Log.d(TAG, "Final file size: ${targetFile.length() / (1024 * 1024)} MB")

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error copying model from assets", e)
            false
        } finally {
            try {
                inputStream?.close()
                outputStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing streams", e)
            }
        }
    }

    /**
     * 删除已安装的模型
     */
    suspend fun deleteModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = getModelFile(context)
            if (modelFile.exists()) {
                val deleted = modelFile.delete()
                Log.d(TAG, "Model deleted: $deleted")
                return@withContext deleted
            }
            Log.d(TAG, "Model file does not exist, nothing to delete")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting model", e)
            false
        }
    }

    /**
     * 获取模型信息
     */
    fun getModelInfo(context: Context): ModelInfo {
        val modelFile = getModelFile(context)
        val exists = modelFile.exists()

        return ModelInfo(
            exists = exists,
            path = modelFile.absolutePath,
            sizeInMB = if (exists) modelFile.length() / (1024 * 1024) else 0L,
            lastModified = if (exists) modelFile.lastModified() else 0L,
            hasAssetModel = hasAssetModel(context),
            assetModelName = getAssetModelFileName(context)
        )
    }

    /**
     * 验证模型文件完整性
     */
    fun validateModel(context: Context): Boolean {
        val modelFile = getModelFile(context)

        if (!modelFile.exists()) {
            Log.w(TAG, "Model file does not exist")
            return false
        }

        val fileSize = modelFile.length()
        if (fileSize < 1024 * 1024) { // 小于 1MB 可能是损坏的
            Log.w(TAG, "Model file too small: $fileSize bytes")
            return false
        }

        // 检查文件是否可读
        if (!modelFile.canRead()) {
            Log.w(TAG, "Model file is not readable")
            return false
        }

        Log.d(TAG, "Model validation passed")
        return true
    }

    /**
     * 清理临时文件
     */
    suspend fun cleanupTempFiles(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelDir = getModelDir(context)
            if (modelDir.exists()) {
                modelDir.listFiles()?.forEach { file ->
                    if (file.name.endsWith(".tmp") || file.name.endsWith(".temp")) {
                        file.delete()
                        Log.d(TAG, "Deleted temp file: ${file.name}")
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up temp files", e)
            false
        }
    }
}

/**
 * 模型信息数据类
 */
data class ModelInfo(
    val exists: Boolean,
    val path: String,
    val sizeInMB: Long,
    val lastModified: Long,
    val hasAssetModel: Boolean,
    val assetModelName: String?
)
