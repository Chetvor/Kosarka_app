package com.example.kosarka30

import android.os.Bundle
import android.view.WindowManager
import android.content.Intent
import android.provider.Settings
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.telephony.TelephonyManager

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.navigation.NavHostController
import androidx.navigation.compose.*

import com.example.kosarka30.ui.theme.Kosarka30Theme

import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.URL

import android.util.Log
import android.widget.Toast

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import android.app.NotificationChannel
import android.app.NotificationManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import android.net.Uri

import android.media.MediaPlayer
import android.app.AlertDialog
import android.content.pm.PackageManager



data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


class MainActivity : ComponentActivity() {

    companion object {
        private const val REQUEST_PERMISSIONS_CODE = 1001
        var alarmPlayer: MediaPlayer? = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNecessaryPermissions() // Запрашиваем все нужные разрешения

        setContent {
            Kosarka30Theme {
                MainApp()
            }
        }
    }

    // Функция для проверки и запроса всех нужных разрешений
    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()

        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_NETWORK_STATE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_NETWORK_STATE)
        }
        if (checkSelfPermission(android.Manifest.permission.ACCESS_WIFI_STATE)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.ACCESS_WIFI_STATE)
        }
        if (checkSelfPermission(android.Manifest.permission.INTERNET)
            != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissions.add(android.Manifest.permission.INTERNET)
        }

        if (permissions.isNotEmpty()) {
            requestPermissions(permissions.toTypedArray(), REQUEST_PERMISSIONS_CODE)
        }
    }

    // Обработка результата запроса разрешений
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_CODE) {
            if (grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                // Все разрешения выданы, можно продолжать работу
            } else {
                // Какое-то разрешение не выдано — покажи уведомление, если нужно
            }
        }
    }
}


// Data class без лишних полей!
data class ConnectionStatus(
    val networkName: String = "",
    val connectionType: String = "",
    val speed: String = "",
    val signal: String = "",
    val isConnected: Boolean = false
)

// Получение статуса сети
fun getConnectionStatus(context: Context): ConnectionStatus {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    val network = connectivityManager.activeNetwork
    val caps = connectivityManager.getNetworkCapabilities(network)

    if (caps == null) {
        return ConnectionStatus(isConnected = false)
    }

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> {
            val wifiInfo = wifiManager.connectionInfo
            val ssid = wifiInfo?.ssid?.replace("\"", "") ?: "Wi-Fi"
            val linkSpeed = wifiInfo?.linkSpeed ?: 0 // Мбит/с
            val rssi = wifiInfo?.rssi ?: -100        // dBm
            val percent = ((rssi + 100) * 2).coerceIn(0, 100) // в процентах
            ConnectionStatus(
                networkName = ssid,
                connectionType = "Wi-Fi",
                speed = if (linkSpeed > 0) "$linkSpeed Mbps |" else "",
                signal = "$percent%",
                isConnected = true
            )
        }
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> {
            val operator = telephonyManager.networkOperatorName
            val networkType = when (telephonyManager.networkType) {
                TelephonyManager.NETWORK_TYPE_LTE -> "LTE"
                TelephonyManager.NETWORK_TYPE_NR -> "5G"
                TelephonyManager.NETWORK_TYPE_HSPAP,
                TelephonyManager.NETWORK_TYPE_HSPA,
                TelephonyManager.NETWORK_TYPE_UMTS -> "3G"
                TelephonyManager.NETWORK_TYPE_EDGE,
                TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                else -> "Моб. интернет"
            }
            ConnectionStatus(
                networkName = operator.ifEmpty { "Мобильная сеть" },
                connectionType = networkType,
                speed = "",
                signal = "",
                isConnected = true
            )
        }
        else -> ConnectionStatus(isConnected = false)
    }
}

// Получение внешнего IP, города и кода страны
suspend fun fetchExternalIpInfo(): Quadruple<String, String, String, String> = withContext(Dispatchers.IO) {
    try {
        val apiUrl = URL("https://ipwho.is/")
        val json = apiUrl.readText()
        val obj = JSONObject(json)
        val ip = obj.optString("ip")
        val city = obj.optString("city")
        val country = obj.optString("country")          // Ukraine, Turkey, etc.
        val countryCode = obj.optString("country_code") // UA, TR, etc.
        Quadruple(ip, city, country, countryCode)
    } catch (e: Exception) {
        Log.e("EXTERNAL_IP_DEBUG", "Exception: ${e.message}")
        Quadruple("", "", "", "")
    }
}



// Утилита для флага по коду страны
fun countryCodeToFlagEmoji(code: String): String {
    val cc = code.uppercase()
    if (cc.length != 2) return ""
    val first = Character.codePointAt(cc, 0) - 0x41 + 0x1F1E6
    val second = Character.codePointAt(cc, 1) - 0x41 + 0x1F1E6
    return String(Character.toChars(first)) + String(Character.toChars(second))
}


// Статус-бар с поддержкой всех новых данных!
@Composable
fun ConnectionStatusBar(
    status: ConnectionStatus,
    externalIp: String,
    city: String,
    countryCode: String,
    countryName: String = "",
    isRefreshing: Boolean = false,
    onRefreshClick: () -> Unit = {}
) {
    Surface(
        tonalElevation = 0.dp,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
        ) {
            // Первая строка — сеть + refresh
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Circle,
                    contentDescription = if (status.isConnected) "Online" else "Offline",
                    tint = if (status.isConnected) Color(0xFF2ECC40) else Color.Red,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                if (status.isConnected) {
                    if (status.connectionType == "Wi-Fi") {
                        Icon(
                            imageVector = Icons.Default.Wifi,
                            contentDescription = "Wi-Fi",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.DataUsage,
                            contentDescription = "Mobile data",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(status.networkName, color = MaterialTheme.colorScheme.onBackground)
                    if (status.speed.isNotEmpty()) {
                        Text(" | ${status.speed}", color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (status.signal.isNotEmpty()) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.SignalWifi4Bar,
                            contentDescription = "Signal",
                            tint = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(" ${status.signal}", color = MaterialTheme.colorScheme.onBackground)
                    }
                } else {
                    Text(
                        "Нет подключения к интернету",
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.weight(1f)) // Кнопка справа

                Box(
                    modifier = Modifier.size(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.Crossfade(targetState = isRefreshing, label = "refresh_anim") { refreshing ->
                        if (refreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(onClick = onRefreshClick, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Обновить",
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }
                }
}
            // Вторая строка — внешний IP, город, страна, флаг
            if (status.isConnected && (externalIp.isNotEmpty() || city.isNotEmpty() || countryName.isNotEmpty() || countryCode.isNotEmpty())) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (externalIp.isNotEmpty()) {
                        Text(externalIp, color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (city.isNotEmpty()) {
                        Text(" | $city", color = MaterialTheme.colorScheme.onBackground)
                    }
                    if (countryName.isNotEmpty()) {
                        Text(" | $countryName", color = MaterialTheme.colorScheme.onBackground)
                    }
                    val flag = countryCodeToFlagEmoji(countryCode.uppercase())
                    if (flag.isNotEmpty()) {
                        Text(" $flag", fontSize = 20.sp)
                    }
                }
            }
            Divider(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }
    }
}

// Главная функция с навигацией и статусбаром

// Структура для внешних данных
data class ExternalData(
    val ip: String = "",
    val city: String = "",
    val country: String = "",
    val code: String = ""
)

@Composable
fun MainApp() {
    val navController = rememberNavController()
    var keepScreenOn by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Гладкое состояние сети
    var connectionStatus by remember { mutableStateOf(getConnectionStatus(context)) }
    var isExternalOnline by remember { mutableStateOf(true) }
    var lastExternalOnline by remember { mutableStateOf(isExternalOnline) } // привязываем к состоянию при старте
    var externalData by remember { mutableStateOf(ExternalData()) }
    var isRefreshing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // "Гладкое" обновление локального статуса (раз в 2 сек, только если что-то реально поменялось)
    LaunchedEffect(Unit) {
        while (true) {
            val newStatus = getConnectionStatus(context)
            // Сравниваем объекты (data class умеет сравнивать поля по equals)
            if (newStatus != connectionStatus) {
                connectionStatus = newStatus
            }
            delay(2000)
        }
    }

    // Регулярная автоматическая проверка внешнего интернета (раз в 5 сек, только если что-то реально поменялось)
    LaunchedEffect(connectionStatus.isConnected) {
        while (connectionStatus.isConnected) {
            isRefreshing = true
            try {
                withTimeoutOrNull(3000) {
                    val (ip, city, country, code) = fetchExternalIpInfo()
                    val newData = ExternalData(ip, city, country, code)
                    if (ip.isNotEmpty()) {
                        if (newData != externalData) {
                            externalData = newData
                        }
                        isExternalOnline = true
                    } else {
                        isExternalOnline = false
                    }
                }
            } catch (_: Exception) {
                isExternalOnline = false
            } finally {
                isRefreshing = false
            }
            delay(5000)
        }
        // Если локальное соединение пропало — сбрасываем всё разово!
        isExternalOnline = false
        if (externalData != ExternalData()) {
            externalData = ExternalData()
        }
    }

    // Ручное обновление (по кнопке)
    fun manualRefresh() {
        coroutineScope.launch {
            isRefreshing = true
            try {
                val (ip, city, country, code) = fetchExternalIpInfo()
                val newData = ExternalData(ip, city, country, code)
                if (ip.isNotEmpty()) {
                    if (newData != externalData) {
                        externalData = newData
                    }
                    isExternalOnline = true
                } else {
                    isExternalOnline = false
                }
            } catch (_: Exception) {
                isExternalOnline = false
            } finally {
                isRefreshing = false
            }
        }
    }

    // Алерт по исчезновению внешнего интернета — только по переходу online → offline


    LaunchedEffect(isExternalOnline, connectionStatus.isConnected) {
        // Показываем алерт при переходе online→offline, и обязательно если впервые поймали отсутствие внешки после старта
        if (
            (lastExternalOnline && !isExternalOnline && connectionStatus.isConnected)
            || (!isExternalOnline && lastExternalOnline == isExternalOnline) // если оба false и ещё не алертили
        ) {
            showAlertNotification(
                context,
                connectionStatus,
                "Внешний интернет ОТСУТСТВУЕТ! Проверьте соединение роутера с провайдером."
            )
        }
        lastExternalOnline = isExternalOnline
    }

    // Не даём гаснуть экрану, если включено в настройках
    val activity = LocalContext.current as? ComponentActivity
    LaunchedEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Для хранения предыдущего локального статуса
    var lastConnectionKey by remember { mutableStateOf("") }
    LaunchedEffect(connectionStatus.isConnected, connectionStatus.networkName, connectionStatus.connectionType) {
        val currentKey = "${connectionStatus.isConnected}|${connectionStatus.networkName}|${connectionStatus.connectionType}"
        if (lastConnectionKey.isNotEmpty() && lastConnectionKey != currentKey) {
            showAlertNotification(
                context,
                connectionStatus,
                if (connectionStatus.isConnected)
                    "Подключено к: ${connectionStatus.networkName} (${connectionStatus.connectionType})"
                else
                    "Отключено от локальной сети!"
            )
        }
        lastConnectionKey = currentKey
    }

    Scaffold(
        topBar = {
            ConnectionStatusBar(
                status = connectionStatus,
                externalIp = externalData.ip,
                city = externalData.city,
                countryCode = externalData.code,
                countryName = externalData.country,
                isRefreshing = isRefreshing,
                onRefreshClick = { manualRefresh() }
            )
        },
        bottomBar = { BottomNavigationBar(navController) }
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


// Главное меню
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

    var isTimerRunning by remember { mutableStateOf(false) }
    var timerJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    Column(
        Modifier
            .fillMaxSize()
            .padding(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Экран", modifier = Modifier.weight(1f))
            Switch(
                checked = keepScreenOn,
                onCheckedChange = onKeepScreenOnChange
            )
        }

        Spacer(Modifier.height(8.dp))

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
                        timerJob?.cancel()
                        timerJob = null
                        isTimerRunning = false
                    } else {
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
                    if (isTimerRunning) "Остановить очистку"
                    else "Запустить очистку"
                )
            }
        }

        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Доступ к уведомлениям")
        }
        Spacer(Modifier.height(8.dp))
    }
}


fun sendClearNotificationsBroadcast(context: Context) {
    val intent = Intent("com.example.kosarka30.CLEAR_NOTIFICATIONS")
    context.sendBroadcast(intent)
}

fun showAlertNotification(context: Context, status: ConnectionStatus, message: String) {
    val channelId = "network_alert_channel_v4"
    val notificationId = 777

    // 1. Запускаем звук тревоги (не зависит от режима)
    stopAlarm()
    val afd = context.resources.openRawResourceFd(R.raw.modem)
    MainActivity.alarmPlayer = MediaPlayer().apply {
        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
        afd.close()
        setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
        isLooping = true
        prepare()
        start()
    }

    // 2. Оповещение (как раньше)
    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val channel = NotificationChannel(
            channelId, "Network Alerts", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts for network connection changes"
            enableVibration(true)
            setSound(null, null)
        }
        notificationManager.createNotificationChannel(channel)
    }

    val builder = NotificationCompat.Builder(context, channelId)
        .setSmallIcon(android.R.drawable.stat_notify_error)
        .setContentTitle("Сетевое подключение изменено")
        .setContentText(message)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
    NotificationManagerCompat.from(context).notify(notificationId, builder.build())

    // 3. Показываем диалог поверх всего (именно с activity!)
    val activity = context as? ComponentActivity
    activity?.runOnUiThread {
        AlertDialog.Builder(activity) // <-- исправили тут
            .setTitle("Внимание!")
            .setMessage(message)
            .setCancelable(false)
            .setPositiveButton("ОК") { dialog, _ ->
                stopAlarm()
                dialog.dismiss()
            }
            .show()
    }
}

// Функция для остановки звука
fun stopAlarm() {
    MainActivity.alarmPlayer?.let {
        if (it.isPlaying) it.stop()
        it.release()
        MainActivity.alarmPlayer = null
    }
}
