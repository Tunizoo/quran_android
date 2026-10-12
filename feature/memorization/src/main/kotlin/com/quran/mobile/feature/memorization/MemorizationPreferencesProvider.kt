package com.quran.mobile.feature.memorization

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.memorization.ui.MemorizationActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Adds a "Memorization" category to the settings screen that opens [MemorizationActivity]. */
@ContributesIntoSet(AppScope::class)
@Inject
class MemorizationPreferencesProvider : ExtraPreferencesProvider {
  override val order: Int = 11

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = context.getString(R.string.memo_title)
      isIconSpaceReserved = false
    }
    val preference = Preference(context).apply {
      key = PREFERENCE_KEY
      title = context.getString(R.string.memo_settings_entry)
      summary = context.getString(R.string.memo_settings_summary)
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(preference)
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    if (preference.key != PREFERENCE_KEY) return false
    preference.context.startActivity(Intent(preference.context, MemorizationActivity::class.java))
    return true
  }

  companion object {
    private const val CATEGORY_KEY = "memorization_settings_category"
    private const val PREFERENCE_KEY = "memorization_settings"
  }
}
