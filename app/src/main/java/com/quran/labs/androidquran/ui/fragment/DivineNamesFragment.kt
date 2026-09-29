package com.quran.labs.androidquran.ui.fragment

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import com.quran.data.core.QuranInfo
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.labs.androidquran.ui.PagerActivity
import com.quran.mobile.di.AyahActionFragmentProvider
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.ui.DivineNamesActivity
import com.quran.mobile.feature.divinenames.ui.DivineNamesPanel
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow

/** Ayah panel tab listing the names of Allah in the selected ayah with their explanation. */
class DivineNamesFragment : AyahActionFragment() {

  @Inject
  lateinit var quranInfo: QuranInfo

  @Inject
  lateinit var divineNamesDatabase: DivineNamesDatabase

  private val selectedRange = MutableStateFlow<IntRange?>(null)

  object Provider : AyahActionFragmentProvider {
    override val order = DIVINE_NAMES_PAGE
    override val iconResId = com.quran.mobile.feature.divinenames.R.drawable.ic_divine_names
    override fun newAyahActionFragment() = DivineNamesFragment()
  }

  override fun onAttach(context: Context) {
    super.onAttach(context)
    (activity as? PagerActivity)?.pagerActivityComponent?.inject(this)
  }

  override fun onCreateView(
    inflater: LayoutInflater,
    container: ViewGroup?,
    savedInstanceState: Bundle?
  ): View {
    return ComposeView(requireContext()).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      setContent {
        QuranTheme {
          val range by selectedRange.collectAsState()
          DivineNamesPanel(
            ayahIdRange = range,
            database = divineNamesDatabase,
            onOpenIndex = { nameId -> startActivity(DivineNamesActivity.intent(requireContext(), nameId)) }
          )
        }
      }
    }
  }

  override fun refreshView() {
    val selectionStart = start
    val selectionEnd = end
    selectedRange.value = if (selectionStart != null && selectionEnd != null) {
      quranInfo.getAyahId(selectionStart.sura, selectionStart.ayah)..quranInfo.getAyahId(selectionEnd.sura, selectionEnd.ayah)
    } else {
      null
    }
  }

  companion object {
    const val DIVINE_NAMES_PAGE = 3
  }
}
