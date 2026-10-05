package com.quran.mobile.feature.tajweed

import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Settings entries: the tajweed colouring switch (off by default) and a description of the legend. */
@ContributesIntoSet(AppScope::class)
@Inject
class TajweedPreferencesProvider : ExtraPreferencesProvider {
  override val order: Int = 12

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = context.getString(R.string.tajweed_title)
      isIconSpaceReserved = false
    }
    val enable = SwitchPreferenceCompat(context).apply {
      key = TajweedSettings.KEY_ENABLED
      title = context.getString(R.string.tajweed_pref_enable)
      summary = context.getString(R.string.tajweed_pref_enable_summary)
      setDefaultValue(TajweedSettings.DEFAULT_ENABLED)
      isIconSpaceReserved = false
    }
    val legend = Preference(context).apply {
      key = LEGEND_KEY
      title = context.getString(R.string.tajweed_pref_legend_title)
      summary = context.getString(R.string.tajweed_pref_legend_summary)
      isSelectable = false
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(enable)
    category.addPreference(legend)
  }

  override fun onPreferenceClick(preference: Preference): Boolean = false

  companion object {
    private const val CATEGORY_KEY = "tajweed_category"
    private const val LEGEND_KEY = "tajweed_legend"
  }
}
