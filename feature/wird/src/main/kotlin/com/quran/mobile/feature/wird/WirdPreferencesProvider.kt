package com.quran.mobile.feature.wird

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Adds a "Daily wird" category to the settings screen that opens [WirdActivity]. */
@ContributesIntoSet(AppScope::class)
@Inject
class WirdPreferencesProvider : ExtraPreferencesProvider {
  override val order: Int = 10

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = context.getString(R.string.wird_title)
      isIconSpaceReserved = false
    }
    val preference = Preference(context).apply {
      key = PREFERENCE_KEY
      title = context.getString(R.string.wird_settings_entry)
      summary = context.getString(R.string.wird_settings_summary)
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(preference)
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    if (preference.key != PREFERENCE_KEY) return false
    preference.context.startActivity(Intent(preference.context, WirdActivity::class.java))
    return true
  }

  companion object {
    private const val CATEGORY_KEY = "wird_settings_category"
    private const val PREFERENCE_KEY = "wird_settings"
  }
}
