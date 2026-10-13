package com.quran.mobile.feature.wird.reward

import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.mobile.di.RewardEvent
import com.quran.mobile.di.RewardSink
import com.quran.mobile.feature.wird.data.WirdRepository
import com.quran.mobile.feature.wird.model.RecordResult
import com.quran.mobile.feature.wird.reminder.WirdNotifier
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.launch

/** Receives rewardable events from other features and turns them into points and notifications. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class WirdRewardSink @Inject constructor(
  private val repository: WirdRepository,
  private val notifier: WirdNotifier,
  private val appCoroutineScope: AppCoroutineScope
) : RewardSink {

  override fun earn(event: RewardEvent, ref: String?) {
    appCoroutineScope.launch {
      notifier.showRewards(repository.earn(event, ref))
    }
  }
}

/** Notifications for everything a [RecordResult] may carry besides the page itself. */
fun WirdNotifier.showRewards(result: RecordResult) {
  result.completedKhatmaNumber?.let { showKhatmaCompleted(it) }
  if (result.newAchievements.isNotEmpty()) showAchievements(result.newAchievements)
  result.levelReached?.let { showLevelUp(it) }
  if (result.challengeCompleted) showChallengeCompleted()
}
