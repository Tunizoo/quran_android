package com.quran.mobile.feature.wird.reminder

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.wird.R
import com.quran.mobile.feature.wird.WirdActivity
import com.quran.mobile.feature.wird.model.Achievement
import com.quran.mobile.feature.wird.model.WirdSummary
import com.quran.mobile.feature.wird.ui.AchievementStrings
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

@SingleIn(AppScope::class)
class WirdNotifier @Inject constructor(
  @ApplicationContext private val context: Context
) {

  fun canNotify(): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
      ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
      PackageManager.PERMISSION_GRANTED
    ) {
      return false
    }
    return NotificationManagerCompat.from(context).areNotificationsEnabled()
  }

  fun showReminder(summary: WirdSummary) {
    val text = when {
      summary.currentStreak >= 2 ->
        context.getString(R.string.wird_notification_streak_risk, summary.currentStreak, summary.remainingToday)
      summary.todayPages > 0 ->
        context.getString(R.string.wird_notification_remaining, summary.remainingToday)
      else -> context.getString(R.string.wird_notification_start)
    }
    notify(
      channelId = REMINDER_CHANNEL,
      channelName = context.getString(R.string.wird_notification_channel_reminder),
      notificationId = NOTIFICATION_ID_REMINDER,
      title = context.getString(R.string.wird_notification_title),
      text = text,
      intent = readerIntent()
    )
  }

  fun showAchievements(achievements: List<Achievement>) {
    if (achievements.isEmpty()) return
    val names = achievements.joinToString(separator = " · ") { context.getString(AchievementStrings.title(it)) }
    notify(
      channelId = ACHIEVEMENT_CHANNEL,
      channelName = context.getString(R.string.wird_notification_channel_achievements),
      notificationId = NOTIFICATION_ID_ACHIEVEMENT,
      title = context.getString(R.string.wird_achievement_notification_title),
      text = names,
      intent = Intent(context, WirdActivity::class.java)
    )
  }

  fun showKhatmaCompleted(khatmaNumber: Int) {
    notify(
      channelId = ACHIEVEMENT_CHANNEL,
      channelName = context.getString(R.string.wird_notification_channel_achievements),
      notificationId = NOTIFICATION_ID_KHATMA,
      title = context.getString(R.string.wird_khatma_completed_title),
      text = context.getString(R.string.wird_khatma_completed_text, khatmaNumber),
      intent = Intent(context, WirdActivity::class.java)
    )
  }

  private fun readerIntent(): Intent {
    return context.packageManager.getLaunchIntentForPackage(context.packageName)
      ?: Intent(context, WirdActivity::class.java)
  }

  private fun notify(
    channelId: String,
    channelName: String,
    notificationId: Int,
    title: String,
    text: String,
    intent: Intent
  ) {
    if (!canNotify()) return

    val manager = NotificationManagerCompat.from(context)
    manager.createNotificationChannel(
      NotificationChannelCompat.Builder(channelId, NotificationManagerCompat.IMPORTANCE_DEFAULT)
        .setName(channelName)
        .build()
    )

    val pendingIntent = PendingIntent.getActivity(
      context,
      notificationId,
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    val notification = NotificationCompat.Builder(context, channelId)
      .setSmallIcon(R.drawable.ic_wird_notification)
      .setContentTitle(title)
      .setContentText(text)
      .setStyle(NotificationCompat.BigTextStyle().bigText(text))
      .setContentIntent(pendingIntent)
      .setAutoCancel(true)
      .setPriority(NotificationCompat.PRIORITY_DEFAULT)
      .build()

    try {
      manager.notify(notificationId, notification)
    } catch (e: SecurityException) {
      // notification permission was revoked between the check and the call - ignore
    }
  }

  companion object {
    const val REMINDER_CHANNEL = "quran_wird_reminder"
    const val ACHIEVEMENT_CHANNEL = "quran_wird_achievements"
    const val NOTIFICATION_ID_REMINDER = 7001
    const val NOTIFICATION_ID_ACHIEVEMENT = 7002
    const val NOTIFICATION_ID_KHATMA = 7003
  }
}
