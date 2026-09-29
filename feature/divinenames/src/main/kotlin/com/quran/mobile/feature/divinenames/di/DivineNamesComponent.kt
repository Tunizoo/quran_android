package com.quran.mobile.feature.divinenames.di

import com.quran.data.di.ActivityLevelScope
import com.quran.data.di.ActivityScope
import com.quran.mobile.feature.divinenames.ui.DivineNamesActivity
import dev.zacsweers.metro.GraphExtension

@ActivityScope
@GraphExtension(ActivityLevelScope::class)
interface DivineNamesComponent {
  fun inject(activity: DivineNamesActivity)

  @GraphExtension.Factory
  interface Factory {
    fun generate(): DivineNamesComponent
  }
}
