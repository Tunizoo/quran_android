package com.quran.mobile.feature.wird.di

import com.quran.data.di.AppScope
import com.quran.mobile.feature.wird.data.WirdRepository
import com.quran.mobile.feature.wird.reminder.WirdNotifier
import com.quran.mobile.feature.wird.reminder.WirdReminderScheduler
import dev.zacsweers.metro.ContributesTo

/**
 * Merged into the application graph. Gives the activity its own graph extension, and lets
 * components that are instantiated by the system (WorkManager workers) reach app-scoped objects.
 */
@ContributesTo(AppScope::class)
interface WirdComponentInterface {
  fun wirdComponentFactory(): WirdComponent.Factory

  val wirdRepository: WirdRepository
  val wirdNotifier: WirdNotifier
  val wirdReminderScheduler: WirdReminderScheduler
}
