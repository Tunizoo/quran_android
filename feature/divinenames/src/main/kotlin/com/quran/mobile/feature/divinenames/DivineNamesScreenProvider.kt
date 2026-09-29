package com.quran.mobile.feature.divinenames

import android.content.Context
import android.content.Intent
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraScreenProvider
import com.quran.mobile.feature.divinenames.ui.DivineNamesActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Adds "Names of Allah" to the home screen overflow menu. */
@ContributesIntoSet(AppScope::class)
@Inject
class DivineNamesScreenProvider : ExtraScreenProvider {
  override val order: Int = 11
  override val id: Int = R.id.menu_divine_names
  override val titleResId: Int = R.string.divine_names_title

  override fun onClick(context: Context): Boolean {
    context.startActivity(Intent(context, DivineNamesActivity::class.java))
    return true
  }
}
