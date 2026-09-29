package com.quran.mobile.feature.divinenames.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quran.mobile.feature.divinenames.R
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.model.NameInSelection

/**
 * Content of the ayah panel tab: the names of Allah found in the selected ayah range with their
 * explanation. [ayahIdRange] is a global ayah id range (see QuranInfo.getAyahId), or null when
 * nothing is selected.
 */
@Composable
fun DivineNamesPanel(
  ayahIdRange: IntRange?,
  database: DivineNamesDatabase,
  onOpenIndex: (nameId: String?) -> Unit,
  modifier: Modifier = Modifier
) {
  val names by produceState<List<NameInSelection>?>(initialValue = null, ayahIdRange, database) {
    value = if (ayahIdRange == null) emptyList() else database.namesInAyahRange(ayahIdRange.first, ayahIdRange.last)
  }
  var expandedId by rememberSaveable(ayahIdRange) { mutableStateOf<String?>(null) }

  val list = names
  when {
    list == null -> Unit
    list.isEmpty() -> Box(
      modifier = modifier
        .fillMaxSize()
        .padding(24.dp),
      contentAlignment = Alignment.Center
    ) {
      Text(
        text = stringResource(
          if (ayahIdRange == null) R.string.divine_names_panel_no_selection else R.string.divine_names_panel_empty
        ),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    else -> LazyColumn(
      modifier = modifier.fillMaxSize(),
      contentPadding = PaddingValues(12.dp),
      verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
      items(list, key = { it.name.id }) { item ->
        DivineNameCard(
          name = item.name,
          expanded = expandedId == item.name.id,
          onToggle = { expandedId = if (expandedId == item.name.id) null else item.name.id }
        )
        Text(
          text = pluralStringResource(
            R.plurals.divine_names_panel_occurrences,
            item.occurrences.size,
            item.occurrences.size
          ),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 8.dp)
        )
      }
      item {
        TextButton(
          onClick = { onOpenIndex(null) },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.divine_names_panel_open_index))
        }
      }
    }
  }
}
