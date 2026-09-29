package com.quran.mobile.feature.wird.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.wird.WirdActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface WirdComponent {
  fun inject(wirdActivity: WirdActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): WirdComponent
  }
}
