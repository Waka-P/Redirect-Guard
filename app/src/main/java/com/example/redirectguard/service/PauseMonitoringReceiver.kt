package com.example.redirectguard.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.redirectguard.data.SettingsRepository

/**
 * 通知の「監視を一時停止」アクション用。自動復帰ループでUI操作が困難になった場合の
 * 緊急脱出手段として、通知シェードから直接監視をOFFにできるようにする。
 */
class PauseMonitoringReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        SettingsRepository(context).monitoringEnabled = false
        context.getSystemService(NotificationManager::class.java).cancel(1)
    }
}
