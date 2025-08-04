package com.example.kosarka30

import android.os.Bundle
import android.view.WindowManager
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.*
import androidx.compose.runtime.rememberCoroutineScope
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Alignment
import androidx.navigation.NavHostController
import androidx.navigation.compose.*
import com.example.kosarka30.ui.theme.Kosarka30Theme

// Импорты для корутин:
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Kosarka30Theme {
                MainApp()
            }
        }
    }
}

@Composable
fun MainApp() {
    val navController = rememberNavController()
    var keepScreenOn by remember { mutableStateOf(false) }

    // Не даём гаснуть экрану, если включено в настройках
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    Scaffold(
        bottomBar = {
            BottomNavigationBar(navController)
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "settings",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("messages") { MessagesScreen() }
            composable("apps") { AppsScreen() }
            composable("settings") {
                SettingsScreen(
                    keepScreenOn = keepScreenOn,
                    onKeepScreenOnChange = { keepScreenOn = it }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(
        BottomNavItem("messages", "Сообщения", Icons.Default.Notifications),
        BottomNavItem("apps", "Приложения", Icons.Default.Apps),
        BottomNavItem("settings", "Настройки", Icons.Default.Settings),
    )
    NavigationBar {
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = navBackStackEntry?.destination?.route
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        // Оставляет только одну копию экрана в стеке
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}

data class BottomNavItem(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun MessagesScreen() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Здесь будут сообщения (пуши)")
    }
}

@Composable
fun AppsScreen() {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Здесь будет список приложений")
    }
}

@Composable
fun SettingsScreen(
    keepScreenOn: Boolean,
    onKeepScreenOnChange: (Boolean) -> Unit,
) {
    var interval by remember { mutableStateOf("9") }
    val context = LocalContext.current

    // Переменные для автоочистки
    var isTimerRunning by remember { mutableStateOf(false) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Не давать экрану гаснуть", modifier = Modifier.weight(1f))
            Switch(
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange
            )
        }

        Spacer(Modifier.height(16.dp))

        // Поле и кнопка в одной строке
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it.filter { ch -> ch.isDigit() } },
                label = { Text("мин") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            )
            Button(
                onClick = {
                    if (isTimerRunning) {
                        // Остановить автоочистку
                        timerJob?.cancel()
                        timerJob = null
                        isTimerRunning = false
                    } else {
                        // Запустить автоочистку
                        val delayMillis = (interval.toLongOrNull() ?: 3L) * 60_000L
                        isTimerRunning = true
                        timerJob = coroutineScope.launch {
                            while (isActive) {
                                sendClearNotificationsBroadcast(context)
                                delay(delayMillis)
                            }
                        }
                    }
                },
                enabled = interval.isNotEmpty(),
                modifier = Modifier.weight(2f)
            ) {
                Text(
                    if (isTimerRunning) "Остановить автоочистку"
                    else "Запустить очистку"
                )
            }
        }

        // Кнопка для открытия настроек разрешения
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Дать доступ к уведомлениям")
        }
        Spacer(Modifier.height(24.dp))
    }
}

// Важно: функция должна быть ВНЕ composable!
fun sendClearNotificationsBroadcast(context: Context) {
    val intent = Intent("com.example.kosarka30.CLEAR_NOTIFICATIONS")
    context.sendBroadcast(intent)
}
