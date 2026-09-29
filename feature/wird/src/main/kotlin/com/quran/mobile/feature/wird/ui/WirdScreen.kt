package com.quran.mobile.feature.wird.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.wird.R
import com.quran.mobile.feature.wird.domain.WirdClock
import com.quran.mobile.feature.wird.model.Achievement
import com.quran.mobile.feature.wird.model.DailyCount
import com.quran.mobile.feature.wird.model.ReminderMode
import com.quran.mobile.feature.wird.model.UnlockedAchievement
import com.quran.mobile.feature.wird.model.WirdSettings
import com.quran.mobile.feature.wird.model.WirdSummary
import com.quran.mobile.feature.wird.state.WirdEvent
import com.quran.mobile.feature.wird.state.WirdState
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Composable
fun WirdScreen(
  state: WirdState,
  onBackPressed: () -> Unit,
  modifier: Modifier = Modifier
) {
  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.wird_title)) },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              imageVector = QuranIcons.ArrowBack,
              contentDescription = stringResource(R.string.wird_back),
              modifier = Modifier.autoMirror()
            )
          }
        },
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
          .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
      )
    }
  ) { innerPadding ->
    val summary = state.summary
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .navigationBarsPadding(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
      if (summary != null) {
        item { TodayCard(summary) }
        item { StreakCard(summary) }
        item { KhatmaCard(summary) }
        item { RecentDaysCard(summary) }
      }
      item { GoalCard(state.settings, state.eventSink) }
      item { ReminderCard(state.settings, state.eventSink) }
      if (summary != null) {
        item {
          Text(
            text = stringResource(R.string.wird_achievements_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
          )
        }
        val unlockedById = summary.unlockedAchievements.associateBy { it.achievement }
        items(Achievement.entries, key = { it.id }) { achievement ->
          AchievementRow(achievement, unlockedById[achievement])
        }
      }
    }
  }
}

@Composable
private fun SectionCard(content: @Composable () -> Unit) {
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(modifier = Modifier.padding(16.dp)) { content() }
  }
}

@Composable
private fun TodayCard(summary: WirdSummary) {
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      ProgressRing(
        progress = if (summary.goal <= 0) 0f else (summary.todayPages.toFloat() / summary.goal).coerceIn(0f, 1f),
        modifier = Modifier.size(112.dp)
      ) {
        Text(
          text = "${summary.todayPages}",
          style = MaterialTheme.typography.headlineMedium,
          fontWeight = FontWeight.Bold
        )
      }
      Spacer(Modifier.width(16.dp))
      Column {
        Text(
          text = stringResource(R.string.wird_today_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = stringResource(R.string.wird_today_progress, summary.todayPages, summary.goal),
          style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        val hint = when {
          summary.isTodayGoalMet -> stringResource(R.string.wird_goal_completed)
          summary.totalPagesRead == 0 -> stringResource(R.string.wird_empty_hint)
          else -> pluralStringResource(R.plurals.wird_remaining_pages, summary.remainingToday, summary.remainingToday)
        }
        Text(
          text = hint,
          style = MaterialTheme.typography.bodySmall,
          color = if (summary.isTodayGoalMet) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
  }
}

@Composable
private fun ProgressRing(
  progress: Float,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit
) {
  val trackColor = MaterialTheme.colorScheme.surfaceVariant
  val progressColor = MaterialTheme.colorScheme.primary
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Canvas(modifier = Modifier.fillMaxSize()) {
      val stroke = 12.dp.toPx()
      val inset = stroke / 2
      val arcSize = Size(size.width - stroke, size.height - stroke)
      drawArc(
        color = trackColor,
        startAngle = -90f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(inset, inset),
        size = arcSize,
        style = Stroke(width = stroke, cap = StrokeCap.Round)
      )
      if (progress > 0f) {
        drawArc(
          color = progressColor,
          startAngle = -90f,
          sweepAngle = 360f * progress,
          useCenter = false,
          topLeft = Offset(inset, inset),
          size = arcSize,
          style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
      }
    }
    content()
  }
}

@Composable
private fun StreakCard(summary: WirdSummary) {
  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(text = "🔥", style = MaterialTheme.typography.headlineMedium)
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.wird_streak_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = pluralStringResource(R.plurals.wird_streak_days, summary.currentStreak, summary.currentStreak),
          style = MaterialTheme.typography.bodyMedium
        )
        if (summary.currentStreak > 0 && !summary.isTodayGoalMet) {
          Text(
            text = stringResource(R.string.wird_streak_at_risk),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
          )
        }
      }
      Text(
        text = stringResource(R.string.wird_best_streak, summary.bestStreak),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}

@Composable
private fun KhatmaCard(summary: WirdSummary) {
  SectionCard {
    Text(
      text = stringResource(R.string.wird_khatma_title, summary.khatmaNumber),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    LinearProgressIndicator(
      progress = { summary.khatmaProgress },
      modifier = Modifier
        .fillMaxWidth()
        .height(10.dp)
        .clip(RoundedCornerShape(5.dp))
    )
    Spacer(Modifier.height(6.dp))
    Text(
      text = stringResource(
        R.string.wird_khatma_progress,
        summary.khatmaPagesRead,
        summary.mushafPageCount,
        (summary.khatmaProgress * 100).toInt()
      ),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    if (summary.completedKhatmas.isNotEmpty()) {
      Text(
        text = pluralStringResource(
          R.plurals.wird_khatma_completed_count,
          summary.completedKhatmas.size,
          summary.completedKhatmas.size
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
      )
    }
  }
}

@Composable
private fun RecentDaysCard(summary: WirdSummary) {
  val countsByDay = remember(summary.recentDays) { summary.recentDays.associateBy { it.day } }
  val days = remember(summary.today) { (6 downTo 0).map { summary.today - it } }
  val maxPages = remember(countsByDay, summary.goal) {
    maxOf(summary.goal, countsByDay.values.maxOfOrNull { it.pages } ?: 0, 1)
  }
  val dayFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
  val barColor = MaterialTheme.colorScheme.primary
  val metColor = MaterialTheme.colorScheme.tertiary
  val trackColor = MaterialTheme.colorScheme.surfaceVariant

  SectionCard {
    Text(
      text = stringResource(R.string.wird_last_week),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(12.dp))
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .height(96.dp),
      horizontalArrangement = Arrangement.spacedBy(8.dp),
      verticalAlignment = Alignment.Bottom
    ) {
      days.forEach { day ->
        val count: DailyCount? = countsByDay[day]
        val pages = count?.pages ?: 0
        val fraction = (pages.toFloat() / maxPages).coerceIn(0f, 1f)
        Column(
          modifier = Modifier.weight(1f),
          horizontalAlignment = Alignment.CenterHorizontally
        ) {
          Text(text = "$pages", style = MaterialTheme.typography.labelSmall)
          Box(
            modifier = Modifier
              .fillMaxWidth()
              .height(56.dp)
              .clip(RoundedCornerShape(4.dp))
              .background(trackColor),
            contentAlignment = Alignment.BottomCenter
          ) {
            Box(
              modifier = Modifier
                .fillMaxWidth()
                .height((56 * fraction).dp)
                .background(if (pages >= summary.goal) metColor else barColor)
            )
          }
          Text(
            text = dayFormat.format(Date(localNoonMillis(day))),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center
          )
        }
      }
    }
  }
}

private fun localNoonMillis(day: Int): Long {
  val utcMidnight = day * WirdClock.DAY_MILLIS
  return utcMidnight - TimeZone.getDefault().getOffset(utcMidnight) + WirdClock.DAY_MILLIS / 2
}

@Composable
private fun GoalCard(settings: WirdSettings, eventSink: (WirdEvent) -> Unit) {
  SectionCard {
    Text(
      text = stringResource(R.string.wird_goal_title),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold
    )
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      FilledTonalIconButton(
        onClick = { eventSink(WirdEvent.SetGoal(settings.pagesPerDay - 1)) },
        enabled = settings.pagesPerDay > WirdSettings.MIN_PAGES_PER_DAY
      ) { Text("−", style = MaterialTheme.typography.titleLarge) }
      Text(
        text = pluralStringResource(R.plurals.wird_goal_pages, settings.pagesPerDay, settings.pagesPerDay),
        style = MaterialTheme.typography.titleMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier.weight(1f)
      )
      FilledTonalIconButton(
        onClick = { eventSink(WirdEvent.SetGoal(settings.pagesPerDay + 1)) },
        enabled = settings.pagesPerDay < WirdSettings.MAX_PAGES_PER_DAY
      ) { Text("+", style = MaterialTheme.typography.titleLarge) }
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      GOAL_PRESETS.forEach { preset ->
        FilterChip(
          selected = settings.pagesPerDay == preset,
          onClick = { eventSink(WirdEvent.SetGoal(preset)) },
          label = {
            Text(
              if (preset == JUZ_PAGES) stringResource(R.string.wird_goal_preset_juz) else "$preset"
            )
          }
        )
      }
    }
  }
}

private const val JUZ_PAGES = 20
private val GOAL_PRESETS = listOf(1, 2, 4, 10, JUZ_PAGES)

@Composable
private fun ReminderCard(settings: WirdSettings, eventSink: (WirdEvent) -> Unit) {
  val context = LocalContext.current
  var showTimePicker by remember { mutableStateOf(false) }
  var permissionDenied by remember { mutableStateOf(false) }

  val permissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { granted ->
    permissionDenied = !granted
    if (granted) eventSink(WirdEvent.SetReminderEnabled(true))
  }

  SectionCard {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(R.string.wird_reminder_title),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = stringResource(R.string.wird_reminder_enabled),
          style = MaterialTheme.typography.bodyMedium
        )
      }
      Switch(
        checked = settings.reminderEnabled,
        onCheckedChange = { enabled ->
          if (enabled && !hasNotificationPermission(context)) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
          } else {
            eventSink(WirdEvent.SetReminderEnabled(enabled))
          }
        }
      )
    }
    if (permissionDenied) {
      Text(
        text = stringResource(R.string.wird_reminder_permission_denied),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error
      )
    }
    if (settings.reminderEnabled) {
      Spacer(Modifier.height(12.dp))
      SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        ReminderMode.entries.forEachIndexed { index, mode ->
          SegmentedButton(
            selected = settings.reminderMode == mode,
            onClick = { eventSink(WirdEvent.SetReminderMode(mode)) },
            shape = SegmentedButtonDefaults.itemShape(index = index, count = ReminderMode.entries.size)
          ) {
            Text(
              when (mode) {
                ReminderMode.FIXED -> stringResource(R.string.wird_reminder_mode_fixed)
                ReminderMode.ADAPTIVE -> stringResource(R.string.wird_reminder_mode_adaptive)
              }
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      Text(
        text = when (settings.reminderMode) {
          ReminderMode.FIXED -> stringResource(R.string.wird_reminder_mode_fixed_desc)
          ReminderMode.ADAPTIVE -> stringResource(R.string.wird_reminder_mode_adaptive_desc)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
      Spacer(Modifier.height(8.dp))
      Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
          text = stringResource(R.string.wird_reminder_time),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f)
        )
        TextButton(onClick = { showTimePicker = true }) {
          Text(formatMinuteOfDay(context, settings.reminderMinuteOfDay))
        }
      }
    }
  }

  if (showTimePicker) {
    val pickerState = rememberTimePickerState(
      initialHour = settings.reminderMinuteOfDay / 60,
      initialMinute = settings.reminderMinuteOfDay % 60,
      is24Hour = DateFormat.is24HourFormat(context)
    )
    AlertDialog(
      onDismissRequest = { showTimePicker = false },
      confirmButton = {
        TextButton(onClick = {
          eventSink(WirdEvent.SetReminderTime(pickerState.hour * 60 + pickerState.minute))
          showTimePicker = false
        }) { Text(stringResource(R.string.wird_ok)) }
      },
      dismissButton = {
        TextButton(onClick = { showTimePicker = false }) { Text(stringResource(R.string.wird_cancel)) }
      },
      text = { TimePicker(state = pickerState) }
    )
  }
}

private fun hasNotificationPermission(context: Context): Boolean {
  if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
  return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
    PackageManager.PERMISSION_GRANTED
}

private fun formatMinuteOfDay(context: Context, minuteOfDay: Int): String {
  val calendar = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, minuteOfDay / 60)
    set(Calendar.MINUTE, minuteOfDay % 60)
    set(Calendar.SECOND, 0)
  }
  return DateFormat.getTimeFormat(context).format(calendar.time)
}

@Composable
private fun AchievementRow(achievement: Achievement, unlocked: UnlockedAchievement?) {
  val isUnlocked = unlocked != null
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier
        .padding(horizontal = 16.dp, vertical = 12.dp)
        .alpha(if (isUnlocked) 1f else 0.45f),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(text = AchievementStrings.emoji(achievement), style = MaterialTheme.typography.headlineSmall)
      Spacer(Modifier.width(12.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = stringResource(AchievementStrings.title(achievement)),
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold
        )
        Text(
          text = stringResource(AchievementStrings.description(achievement)),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
          text = if (unlocked != null) {
            stringResource(
              R.string.wird_achievement_unlocked_on,
              java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(Date(unlocked.unlockedAtMillis))
            )
          } else {
            stringResource(R.string.wird_achievement_locked)
          },
          style = MaterialTheme.typography.labelSmall,
          color = if (isUnlocked) MaterialTheme.colorScheme.primary else Color.Unspecified
        )
      }
    }
  }
}
