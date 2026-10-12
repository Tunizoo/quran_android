package com.quran.mobile.feature.memorization.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.memorization.ui.MemorizationActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface MemorizationComponent {
  fun inject(activity: MemorizationActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): MemorizationComponent
  }
}
