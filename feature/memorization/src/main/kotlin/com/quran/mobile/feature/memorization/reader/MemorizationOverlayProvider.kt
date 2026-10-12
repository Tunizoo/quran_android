package com.quran.mobile.feature.memorization.reader

import android.view.View
import android.widget.Toast
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.quran.data.di.AppCoroutineScope
import com.quran.data.di.AppScope
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.memorization.R
import com.quran.mobile.feature.memorization.data.MemorizationRepository
import com.quran.mobile.feature.memorization.model.AyahRange
import com.quran.mobile.feature.memorization.model.Grade
import com.quran.mobile.feature.memorization.session.MemorizationSession
import com.quran.mobile.feature.memorization.ui.format
import com.quran.page.common.data.QuranNaming
import com.quran.page.common.extension.ReaderHost
import com.quran.page.common.extension.ReaderOverlay
import com.quran.page.common.extension.ReaderOverlayProvider
import dev.zacsweers.metro.ContributesIntoSet
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Adds the memorization bar to the reader and a "Memorization mode" entry to its overflow menu.
 * The bar is visible whenever a session is running (started from the menu or from the schedule).
 */
@ContributesIntoSet(AppScope::class)
class MemorizationOverlayProvider @Inject constructor(
  private val session: MemorizationSession,
  private val repository: MemorizationRepository,
  private val naming: QuranNaming,
  private val appCoroutineScope: AppCoroutineScope
) : ReaderOverlayProvider {
  override val order: Int = 10
  override val menuTitleResId: Int = R.string.memo_menu_reader

  override fun createOverlay(host: ReaderHost): ReaderOverlay = Overlay(host)

  private inner class Overlay(private val host: ReaderHost) : ReaderOverlay {
    private var repeats = DEFAULT_REPEATS

    override val view: View = ComposeView(host.activity).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
      ViewCompat.setOnApplyWindowInsetsListener(this) { v, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
        v.updatePadding(bottom = bottom)
        insets
      }
      setContent {
        QuranTheme {
          val state by session.state.collectAsState()
          val range = state.range
          if (range != null) {
            MemorizationBar(
              state = state,
              title = range.format(context, naming),
              actions = actions
            )
          }
        }
      }
    }

    private val actions = object : MemorizationBarActions {
      override fun close() = session.stop()
      override fun listen(times: Int) {
        val range = session.state.value.range ?: return
        repeats = times
        session.setHidden(false)
        host.playRange(range.start, range.end, verseRepeat = times - 1, rangeRepeat = 0)
      }
      override fun setHidden(hidden: Boolean) = session.setHidden(hidden)
      override fun setHint(hint: Boolean) = session.setHint(hint)
      override fun revealWord() = session.revealNextWord()
      override fun revealAyah() = session.revealNextAyah()
      override fun revealAll() = session.revealAll()
      override fun hideAll() = session.hideAll()
      override fun grade(grade: Grade) {
        val itemId = session.state.value.itemId ?: return
        appCoroutineScope.launch {
          val item = repository.review(itemId, grade)
          withContext(Dispatchers.Main) {
            if (item != null) {
              val days = item.intervalDays
              val text = host.activity.resources.getQuantityString(R.plurals.memo_next_review_days, days, days)
              Toast.makeText(host.activity, text, Toast.LENGTH_SHORT).show()
            }
            session.stop()
          }
        }
      }
      override fun savePortion() {
        val range = session.state.value.range ?: return
        appCoroutineScope.launch {
          val item = repository.addItem(range)
          withContext(Dispatchers.Main) {
            session.attachItem(item.id)
            Toast.makeText(host.activity, R.string.memo_saved, Toast.LENGTH_SHORT).show()
          }
        }
      }
    }

    override fun onMenuSelected() {
      if (session.state.value.isActive) {
        session.stop()
        return
      }
      val selection = host.selectedAyahRange()
      val range = if (selection != null) {
        AyahRange(selection.first, selection.second)
      } else {
        session.pageRange(host.currentPage())
      }
      host.clearSelection()
      session.start(range, itemId = null, hidden = true)
      host.refreshPages()
    }

    override fun onPageChanged(page: Int) {
      // a review opened through the deep link arrives with the first ayah selected; the selection
      // would otherwise swallow the taps meant to peek at words
      if (session.state.value.isActive) host.clearSelection()
    }

    override fun onDestroy() = Unit
  }

  companion object {
    private const val DEFAULT_REPEATS = 3
  }
}
