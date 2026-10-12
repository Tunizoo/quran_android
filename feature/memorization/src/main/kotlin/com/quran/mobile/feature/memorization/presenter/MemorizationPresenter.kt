package com.quran.mobile.feature.memorization.presenter

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import com.quran.data.di.AppCoroutineScope
import com.quran.mobile.di.qualifier.ApplicationContext
import com.quran.mobile.feature.memorization.data.MemorizationRepository
import com.quran.mobile.feature.memorization.session.MemorizationSession
import com.quran.mobile.feature.memorization.state.MemorizationEvent
import com.quran.mobile.feature.memorization.state.MemorizationState
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MemorizationPresenter @Inject constructor(
  private val repository: MemorizationRepository,
  private val session: MemorizationSession,
  @ApplicationContext private val context: Context,
  private val appCoroutineScope: AppCoroutineScope
) {

  @Composable
  fun present(): MemorizationState {
    val summary by repository.summary.collectAsState(initial = null)

    val eventSink: (MemorizationEvent) -> Unit = { event ->
      when (event) {
        is MemorizationEvent.AddPortion -> appCoroutineScope.launch { repository.addItem(event.range) }
        is MemorizationEvent.DeletePortion -> appCoroutineScope.launch { repository.deleteItem(event.itemId) }
        is MemorizationEvent.Review -> appCoroutineScope.launch {
          val item = repository.item(event.itemId) ?: return@launch
          withContext(Dispatchers.Main) {
            session.start(item.range, itemId = item.id, hidden = true)
            openReader(item.range.start.sura, item.range.start.ayah)
          }
        }
      }
    }

    return MemorizationState(summary = summary, eventSink = eventSink)
  }

  /** Opens the reader at an ayah through the app's quran://sura/ayah deep link. */
  private fun openReader(sura: Int, ayah: Int) {
    val intent = Intent(Intent.ACTION_VIEW, "quran://$sura/$ayah".toUri())
      .setPackage(context.packageName)
      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
  }
}
