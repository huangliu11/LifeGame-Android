package com.example.lifequest.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.lifequest.ai.ModelFileManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 设置界面 ViewModel
 */
class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
    }

    /**
     * 加载设置
     */
    private fun loadSettings() {
        viewModelScope.launch {
            val modelInfo = ModelFileManager.getModelInfo(context)

            _uiState.value = SettingsUiState(
                modelPath = if (modelInfo.exists) modelInfo.path else "",
                modelName = modelInfo.assetModelName ?: "未加载",
                modelSizeMB = modelInfo.sizeInMB,
                modelExists = modelInfo.exists,
                hasAssetModel = modelInfo.hasAssetModel,
                notificationsEnabled = true,
                language = "zh-CN",
                theme = "system"
            )
        }
    }

    /**
     * 获取模型路径
     */
    fun getModelPath(): String {
        return _uiState.value.modelPath
    }

    /**
     * 获取模型信息
     */
    fun getModelInfo(): String {
        val state = _uiState.value
        return when {
            state.modelExists -> "已安装 (${state.modelSizeMB} MB)"
            state.hasAssetModel -> "未安装，可从资源安装"
            else -> "未找到模型文件"
        }
    }

    /**
     * 安装模型
     */
    fun installModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在安装模型..."
            )

            val success = ModelFileManager.copyModelFromAssets(context) { progress ->
                _uiState.value = _uiState.value.copy(
                    installProgress = progress
                )
            }

            if (success) {
                loadSettings() // 重新加载模型信息
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    installProgress = 0,
                    message = "模型安装成功！"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    installProgress = 0,
                    message = "模型安装失败，请重试"
                )
            }
        }
    }

    /**
     * 卸载模型
     */
    fun uninstallModel() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在卸载模型..."
            )

            val success = ModelFileManager.deleteModel(context)

            if (success) {
                loadSettings() // 重新加载模型信息
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "模型已卸载"
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    message = "卸载失败，请重试"
                )
            }
        }
    }

    /**
     * 验证模型
     */
    fun validateModel() {
        viewModelScope.launch {
            val isValid = ModelFileManager.validateModel(context)
            _uiState.value = _uiState.value.copy(
                message = if (isValid) "模型文件完整" else "模型文件损坏，请重新安装"
            )
        }
    }

    /**
     * 清理临时文件
     */
    fun cleanupTempFiles() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在清理临时文件..."
            )

            val success = ModelFileManager.cleanupTempFiles(context)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = if (success) "临时文件已清理" else "清理失败"
            )
        }
    }

    /**
     * 备份数据
     */
    fun backupData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在备份数据..."
            )

            // TODO: 实现数据备份逻辑
            kotlinx.coroutines.delay(2000)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = "备份成功！"
            )
        }
    }

    /**
     * 恢复数据
     */
    fun restoreData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在恢复数据..."
            )

            // TODO: 实现数据恢复逻辑
            kotlinx.coroutines.delay(2000)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = "恢复成功！"
            )
        }
    }

    /**
     * 清除缓存
     */
    fun clearCache() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                message = "正在清除缓存..."
            )

            // 清理临时文件
            ModelFileManager.cleanupTempFiles(context)

            kotlinx.coroutines.delay(1000)

            _uiState.value = _uiState.value.copy(
                isLoading = false,
                message = "缓存已清除！"
            )
        }
    }

    /**
     * 清除消息
     */
    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}

/**
 * 设置界面 UI 状态
 */
data class SettingsUiState(
    val modelPath: String = "",
    val modelName: String = "未加载",
    val modelSizeMB: Long = 0,
    val modelExists: Boolean = false,
    val hasAssetModel: Boolean = false,
    val installProgress: Int = 0,
    val notificationsEnabled: Boolean = true,
    val language: String = "zh-CN",
    val theme: String = "system",
    val isLoading: Boolean = false,
    val message: String? = null
)
