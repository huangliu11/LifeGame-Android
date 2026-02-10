package com.example.lifequest

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.lifequest.ai.ModelFileManager
import com.example.lifequest.ui.screens.ChatScreen
import com.example.lifequest.ui.screens.RewardScreen
import com.example.lifequest.ui.screens.SettingsScreen
import com.example.lifequest.ui.screens.TaskListScreen
import com.example.lifequest.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 检查并初始化模型
        initializeModel()

        setContent {
            LifeQuestTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }

    /**
     * 初始化模型
     */
    private fun initializeModel() {
        lifecycleScope.launch {
            try {
                val hasAsset = ModelFileManager.hasAssetModel(this@MainActivity)
                val isInstalled = ModelFileManager.isModelExists(this@MainActivity)

                when {
                    isInstalled -> {
                        val size = ModelFileManager.getModelSize(this@MainActivity)
                        showToast("✅ AI 模型已就绪 (${size}MB)")
                    }
                    hasAsset -> {
                        showToast("📦 检测到模型文件，正在准备安装...")
                        // 可选：自动安装模型
                        // autoInstallModel()
                    }
                    else -> {
                        showToast("⚠️ 未找到模型文件\n请将 .gguf 文件放入 assets/models/ 目录")
                    }
                }
            } catch (e: Exception) {
                showToast("❌ 模型检查失败: ${e.message}")
            }
        }
    }

    /**
     * 自动安装模型（可选）
     */
    private suspend fun autoInstallModel() {
        val success = ModelFileManager.copyModelFromAssets(this) { progress ->
            // 可以在这里更新进度通知
            if (progress % 20 == 0) {
                runOnUiThread {
                    showToast("安装进度: $progress%")
                }
            }
        }

        if (success) {
            showToast("✅ 模型安装成功！")
        } else {
            showToast("❌ 模型安装失败，请手动在设置中安装")
        }
    }

    /**
     * 显示 Toast 消息
     */
    private fun showToast(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        }
    }
}

/**
 * 应用主题
 */
@Composable
fun LifeQuestTheme(content: @Composable () -> Unit) {
    // 动态颜色方案（可选）
    val colorScheme = lightColorScheme(
        primary = MaterialTheme.colorScheme.primary,
        secondary = MaterialTheme.colorScheme.secondary,
        tertiary = MaterialTheme.colorScheme.tertiary,
        background = MaterialTheme.colorScheme.background,
        surface = MaterialTheme.colorScheme.surface,
    )

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        content = content
    )
}

/**
 * 主屏幕
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // 导航项配置
    val navigationItems = remember {
        listOf(
            NavigationItem("tasks", "任务", Icons.Filled.List),
            NavigationItem("chat", "聊天", Icons.Filled.Chat),
            NavigationItem("rewards", "奖励", Icons.Filled.Star),
            NavigationItem("settings", "设置", Icons.Filled.Settings)
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = getScreenTitle(currentRoute),
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    UserStatsDisplay(viewModel)
                }
            )
        },
        bottomBar = {
            BottomNavigationBar(
                navController = navController,
                items = navigationItems,
                currentRoute = currentRoute
            )
        }
    ) { innerPadding ->
        NavigationHost(
            navController = navController,
            viewModel = viewModel,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

/**
 * 导航项数据类
 */
data class NavigationItem(
    val route: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

/**
 * 用户状态显示
 */
@Composable
private fun UserStatsDisplay(viewModel: MainViewModel) {
    val userStats by viewModel.userStats.collectAsState()

    Row(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 金币显示
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "💰",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${userStats.coins}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        // 等级显示
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.tertiaryContainer,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "⭐",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "Lv.${userStats.level}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * 底部导航栏
 */
@Composable
private fun BottomNavigationBar(
    navController: NavHostController,
    items: List<NavigationItem>,
    currentRoute: String?
) {
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 3.dp
    ) {
        items.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.label
                    )
                },
                label = {
                    Text(
                        text = item.label,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                selected = currentRoute == item.route,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            // 避免重复导航
                            popUpTo(navController.graph.startDestinationId) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    }
}

/**
 * 导航主机
 */
@Composable
private fun NavigationHost(
    navController: NavHostController,
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = "tasks",
        modifier = modifier,
        enterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        exitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Left,
                animationSpec = tween(300)
            )
        },
        popEnterTransition = {
            slideIntoContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        },
        popExitTransition = {
            slideOutOfContainer(
                towards = AnimatedContentTransitionScope.SlideDirection.Right,
                animationSpec = tween(300)
            )
        }
    ) {
        composable("tasks") {
            TaskListScreen(viewModel)
        }
        composable("chat") {
            ChatScreen(viewModel)
        }
        composable("rewards") {
            RewardScreen(viewModel)
        }
        composable("settings") {
            SettingsScreen(onNavigateBack = {
                navController.popBackStack()  // ✅ 返回上一页
            })
        }
    }
}

/**
 * 获取屏幕标题
 */
private fun getScreenTitle(route: String?): String {
    return when (route) {
        "tasks" -> "📝 任务列表"
        "chat" -> "💬 AI 助手"
        "rewards" -> "🎁 奖励商店"
        "settings" -> "⚙️ 设置"
        else -> "LifeQuest"
    }
}
