package com.quran.mobile.feature.wird

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraScreenProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Adds a "Daily wird" entry to the home screen overflow menu. */
@ContributesIntoSet(AppScope::class)
@Inject
class WirdScreenProvider : ExtraScreenProvider {
  override val order: Int = 10
  override val id: Int = R.id.menu_wird
  override val titleResId: Int = R.string.wird_title

  override fun onClick(context: Context): Boolean {
    context.startActivity(Intent(context, WirdActivity::class.java))
    return true
  }
}
