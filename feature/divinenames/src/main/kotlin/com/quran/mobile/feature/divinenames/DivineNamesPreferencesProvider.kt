package com.quran.mobile.feature.divinenames

import android.content.Intent
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroup
import androidx.preference.SwitchPreferenceCompat
import com.quran.data.di.AppScope
import com.quran.mobile.di.ExtraPreferencesProvider
import com.quran.mobile.feature.divinenames.data.DivineNamesSettings
import com.quran.mobile.feature.divinenames.ui.DivineNamesActivity
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject

/** Settings entries: a switch for highlighting the names on the page, and a link to the index. */
@ContributesIntoSet(AppScope::class)
@Inject
class DivineNamesPreferencesProvider : ExtraPreferencesProvider {
  override val order: Int = 11

  override fun addPreferences(root: PreferenceGroup) {
    val context = root.context
    val category = PreferenceCategory(context).apply {
      key = CATEGORY_KEY
      title = context.getString(R.string.divine_names_title)
      isIconSpaceReserved = false
    }
    val highlight = SwitchPreferenceCompat(context).apply {
      key = DivineNamesSettings.KEY_HIGHLIGHT
      title = context.getString(R.string.divine_names_pref_highlight)
      summary = context.getString(R.string.divine_names_pref_highlight_summary)
      setDefaultValue(DivineNamesSettings.DEFAULT_HIGHLIGHT)
      isIconSpaceReserved = false
    }
    val index = Preference(context).apply {
      key = INDEX_KEY
      title = context.getString(R.string.divine_names_pref_index)
      summary = context.getString(R.string.divine_names_pref_index_summary)
      isIconSpaceReserved = false
    }
    root.addPreference(category)
    category.addPreference(highlight)
    category.addPreference(index)
  }

  override fun onPreferenceClick(preference: Preference): Boolean {
    if (preference.key != INDEX_KEY) return false
    preference.context.startActivity(Intent(preference.context, DivineNamesActivity::class.java))
    return true
  }

  companion object {
    private const val CATEGORY_KEY = "divine_names_category"
    private const val INDEX_KEY = "divine_names_index"
  }
}
