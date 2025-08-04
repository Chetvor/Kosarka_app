package com.example.kosarka30

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.Context.RECEIVER_NOT_EXPORTED
import android.service.notification.NotificationListenerService
import android.util.Log

class NotificationCleanerService : NotificationListenerService() {

    private val clearReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            clearAllNotifications()
            Log.d("NotificationCleaner", "Получен broadcast: CLEAR_NOTIFICATIONS, уведомления очищены")
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.example.kosarka30.CLEAR_NOTIFICATIONS")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            // Для Android 8.0 (API 26) и выше
            registerReceiver(clearReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            // Для старых версий Android
            @Suppress("DEPRECATION")
            registerReceiver(clearReceiver, filter)
        }
    }


    override fun onDestroy() {
        // Очень важно: снимать регистрацию ресивера!
        unregisterReceiver(clearReceiver)
        super.onDestroy()
    }

    // Метод для удаления всех уведомлений
    fun clearAllNotifications() {
        cancelAllNotifications()
    }
}
