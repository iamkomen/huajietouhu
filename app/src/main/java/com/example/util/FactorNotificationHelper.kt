package com.example.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.R

object FactorNotificationHelper {

    private const val CHANNEL_ID = "factor_score_alerts_channel"
    private const val CHANNEL_NAME = "多因子量化异动提醒"
    private const val CHANNEL_DESC = "自选股五因子评分异动、多头突破及破位预警通知"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun sendFactorAlertNotification(
        context: Context,
        notificationId: Int,
        stockName: String,
        stockCode: String,
        oldScore: Int,
        newScore: Int,
        delta: Int,
        oldRating: String,
        newRating: String,
        detailMessage: String
    ) {
        createNotificationChannel(context)

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val deltaSign = if (delta >= 0) "+$delta" else "$delta"
        val title = "【因子异动】$stockName ($stockCode) 评分变动 $deltaSign 分"
        val summaryText = if (oldRating.isNotEmpty() && oldRating != newRating) {
            "评级变动: $oldRating ➔ $newRating · 当前评分: ${newScore}分"
        } else {
            "当前综合评分: ${newScore}分 (前值: ${oldScore}分)"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_more)
            .setContentTitle(title)
            .setContentText(summaryText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$summaryText\n\n$detailMessage")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        try {
            val manager = NotificationManagerCompat.from(context)
            if (manager.areNotificationsEnabled()) {
                manager.notify(notificationId, builder.build())
            }
        } catch (e: SecurityException) {
            // Handled when permission is not granted on Android 13+
        }
    }
}
