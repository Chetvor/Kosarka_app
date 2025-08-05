package com.example.kosarka30

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
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
        // Для Android 13+ (API 33) обязательно указывать флаг
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(clearReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
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
