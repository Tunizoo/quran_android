package com.quran.mobile.feature.memorization.data

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorization.db.MemorizationDatabase
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object MemorizationDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideMemorizationDatabase(@ApplicationContext context: Context): MemorizationDatabase {
    val driver = AndroidSqliteDriver(
      schema = MemorizationDatabase.Schema,
      context = context,
      name = "memorization.db"
    )
    return MemorizationDatabase(driver)
  }
}
