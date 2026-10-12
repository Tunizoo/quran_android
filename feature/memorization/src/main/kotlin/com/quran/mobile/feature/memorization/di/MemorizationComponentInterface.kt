package com.quran.mobile.feature.memorization.di

import com.quran.data.di.AppScope
import dev.zacsweers.metro.ContributesTo

/** Merged into the application graph; gives the memorization activity its own graph extension. */
@ContributesTo(AppScope::class)
interface MemorizationComponentInterface {
  fun memorizationComponentFactory(): MemorizationComponent.Factory
}
