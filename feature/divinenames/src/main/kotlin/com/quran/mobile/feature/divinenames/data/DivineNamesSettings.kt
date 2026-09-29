package com.quran.mobile.feature.divinenames.data

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import com.quran.data.di.AppScope
import com.quran.mobile.di.qualifier.ApplicationContext
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * User preferences for the divine names feature. Stored in the default shared preferences so the
 * switch added to the settings screen (a [androidx.preference.SwitchPreferenceCompat]) writes the
 * same key.
 */
@SingleIn(AppScope::class)
class DivineNamesSettings @Inject constructor(
  @ApplicationContext context: Context
) {
  private val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

  private val highlightEnabledInternal =
    MutableStateFlow(preferences.getBoolean(KEY_HIGHLIGHT, DEFAULT_HIGHLIGHT))
  val highlightEnabled: StateFlow<Boolean> = highlightEnabledInternal.asStateFlow()

  // kept as a field: SharedPreferences only holds listeners weakly
  private val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
    if (key == KEY_HIGHLIGHT) {
      highlightEnabledInternal.value = prefs.getBoolean(KEY_HIGHLIGHT, DEFAULT_HIGHLIGHT)
    }
  }

  init {
    preferences.registerOnSharedPreferenceChangeListener(listener)
  }

  val isHighlightEnabled: Boolean get() = highlightEnabledInternal.value

  fun setHighlightEnabled(enabled: Boolean) {
    preferences.edit().putBoolean(KEY_HIGHLIGHT, enabled).apply()
  }

  companion object {
    const val KEY_HIGHLIGHT = "divineNamesHighlight"
    const val DEFAULT_HIGHLIGHT = true
  }
}
