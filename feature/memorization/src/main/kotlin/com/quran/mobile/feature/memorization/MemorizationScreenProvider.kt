package com.quran.mobile.feature.memorization

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraScreenProvider
import com.quran.mobile.feature.memorization.ui.MemorizationActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Adds a "Memorization" entry to the home screen overflow menu. */
@ContributesIntoSet(AppScope::class)
@Inject
class MemorizationScreenProvider : ExtraScreenProvider {
  override val order: Int = 11
  override val id: Int = R.id.menu_memorization
  override val titleResId: Int = R.string.memo_title

  override fun onClick(context: Context): Boolean {
    context.startActivity(Intent(context, MemorizationActivity::class.java))
    return true
  }
}
