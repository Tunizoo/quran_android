package com.quran.mobile.feature.divinenames.ui

import android.app.Activity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.quran.labs.androidquran.common.ui.core.QuranTheme
import com.quran.mobile.feature.divinenames.R
import com.quran.mobile.feature.divinenames.model.DivineName
import com.quran.mobile.feature.divinenames.model.NameOccurrence

/** Bottom sheet with the explanation of one name or attribute, opened by a double tap on the page. */
object DivineNameSheet {

  fun show(activity: Activity, name: DivineName, occurrence: NameOccurrence) {
    val dialog = BottomSheetDialog(activity)
    val content = ComposeView(activity).apply {
      setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
      setContent {
        QuranTheme {
          Surface(color = MaterialTheme.colorScheme.surface) {
            SheetContent(
              name = name,
              occurrence = occurrence,
              onOpenIndex = {
                dialog.dismiss()
                activity.startActivity(DivineNamesActivity.intent(activity, name.id))
              }
            )
          }
        }
      }
    }
    dialog.setContentView(content)
    dialog.show()
  }

  @Composable
  private fun SheetContent(name: DivineName, occurrence: NameOccurrence, onOpenIndex: () -> Unit) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .verticalScroll(rememberScrollState())
        .padding(horizontal = 20.dp, vertical = 16.dp)
        .navigationBarsPadding()
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = name.arabic,
            style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Rtl),
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
          )
          Text(
            text = name.transliteration,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
          Text(
            text = stringResource(
              if (name.isAttribute) R.string.divine_names_kind_attribute else R.string.divine_names_kind_name
            ),
            style = MaterialTheme.typography.labelMedium,
            color = if (name.isAttribute) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.secondary
          )
          Text(
            text = stringResource(R.string.divine_names_sheet_location, occurrence.form, occurrence.sura, occurrence.ayah),
            style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Rtl),
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      DivineNameDetails(name)
      TextButton(onClick = onOpenIndex, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.divine_names_sheet_open_index))
      }
    }
  }
}
