package com.quran.mobile.feature.wird.di

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.wird.db.WirdDatabase
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn

@BindingContainer
@ContributesTo(AppScope::class)
object WirdDataModule {

  @SingleIn(AppScope::class)
  @Provides
  fun provideWirdDatabase(@ApplicationContext context: Context): WirdDatabase {
    val driver = AndroidSqliteDriver(
      schema = WirdDatabase.Schema,
      context = context,
      name = "wird.db"
    )
    return WirdDatabase(driver)
  }
}
