package com.example.lifequest.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lifequest.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()

    // 显示消息的 Snackbar
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // AI 助手设置
                SettingsSection(title = "AI 助手") {
                    // 模型状态
                    SettingsItem(
                        icon = Icons.Default.SmartToy,
                        title = "AI 模型",
                        subtitle = viewModel.getModelInfo(),
                        onClick = { /* 显示模型详情 */ }
                    )

                    // 安装/卸载模型
                    if (uiState.modelExists) {
                        SettingsItem(
                            icon = Icons.Default.Delete,
                            title = "卸载模型",
                            subtitle = "释放 ${uiState.modelSizeMB} MB 空间",
                            onClick = { viewModel.uninstallModel() },
                            isDestructive = true
                        )

                        SettingsItem(
                            icon = Icons.Default.CheckCircle,
                            title = "验证模型",
                            subtitle = "检查模型文件完整性",
                            onClick = { viewModel.validateModel() }
                        )
                    } else if (uiState.hasAssetModel) {
                        SettingsItem(
                            icon = Icons.Default.Download,
                            title = "安装模型",
                            subtitle = "从应用资源安装 AI 模型",
                            onClick = { viewModel.installModel() }
                        )
                    }

                    SettingsItem(
                        icon = Icons.Default.Speed,
                        title = "响应速度",
                        subtitle = "平衡模式",
                        onClick = { /* TODO: 打开速度设置 */ }
                    )
                }

                // 通用设置
                SettingsSection(title = "通用") {
                    SettingsItem(
                        icon = Icons.Default.Notifications,
                        title = "通知",
                        subtitle = if (uiState.notificationsEnabled) "已开启" else "已关闭",
                        onClick = { /* TODO: 打开通知设置 */ }
                    )

                    SettingsItem(
                        icon = Icons.Default.Language,
                        title = "语言",
                        subtitle = "简体中文",
                        onClick = { /* TODO: 打开语言设置 */ }
                    )

                    SettingsItem(
                        icon = Icons.Default.Palette,
                        title = "主题",
                        subtitle = when (uiState.theme) {
                            "light" -> "浅色"
                            "dark" -> "深色"
                            else -> "跟随系统"
                        },
                        onClick = { /* TODO: 打开主题设置 */ }
                    )
                }

                // 数据管理
                SettingsSection(title = "数据管理") {
                    SettingsItem(
                        icon = Icons.Default.CloudUpload,
                        title = "备份数据",
                        subtitle = "将数据备份到云端",
                        onClick = { viewModel.backupData() }
                    )

                    SettingsItem(
                        icon = Icons.Default.CloudDownload,
                        title = "恢复数据",
                        subtitle = "从云端恢复数据",
                        onClick = { viewModel.restoreData() }
                    )

                    SettingsItem(
                        icon = Icons.Default.CleaningServices,
                        title = "清理临时文件",
                        subtitle = "清理 AI 模型临时文件",
                        onClick = { viewModel.cleanupTempFiles() }
                    )

                    SettingsItem(
                        icon = Icons.Default.Delete,
                        title = "清除缓存",
                        subtitle = "释放存储空间",
                        onClick = { viewModel.clearCache() },
                        isDestructive = true
                    )
                }

                // 关于
                SettingsSection(title = "关于") {
                    SettingsItem(
                        icon = Icons.Default.Info,
                        title = "版本",
                        subtitle = "1.0.0",
                        onClick = { /* TODO: 显示版本信息 */ }
                    )

                    SettingsItem(
                        icon = Icons.Default.Description,
                        title = "隐私政策",
                        subtitle = "查看隐私政策",
                        onClick = { /* TODO: 打开隐私政策 */ }
                    )

                    SettingsItem(
                        icon = Icons.Default.Gavel,
                        title = "用户协议",
                        subtitle = "查看用户协议",
                        onClick = { /* TODO: 打开用户协议 */ }
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
            }

            // 加载指示器
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            CircularProgressIndicator()

                            if (uiState.installProgress > 0) {
                                Text(
                                    text = "安装进度: ${uiState.installProgress}%",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                LinearProgressIndicator(
                                    progress = uiState.installProgress / 100f,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                Text(
                                    text = uiState.message ?: "处理中...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    isDestructive: Boolean = false
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isDestructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isDestructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
