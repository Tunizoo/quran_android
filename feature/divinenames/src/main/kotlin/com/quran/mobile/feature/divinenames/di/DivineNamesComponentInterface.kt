package com.quran.mobile.feature.divinenames.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
interface DivineNamesComponentInterface {
  fun divineNamesComponentFactory(): DivineNamesComponent.Factory
}
