package com.quran.mobile.feature.memorization.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.quran.data.core.QuranInfo
import com.quran.data.model.SuraAyah
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.memorization.R
import com.quran.mobile.feature.memorization.model.AyahRange
import com.quran.mobile.feature.memorization.model.MemoItem
import com.quran.mobile.feature.memorization.state.MemorizationEvent
import com.quran.mobile.feature.memorization.state.MemorizationState
import com.quran.page.common.data.QuranNaming
import com.quran.mobile.common.ui.core.R as UiCoreR

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemorizationScreen(
  state: MemorizationState,
  quranInfo: QuranInfo,
  naming: QuranNaming,
  onBackPressed: () -> Unit,
  modifier: Modifier = Modifier
) {
  var showAddDialog by rememberSaveable { mutableStateOf(false) }
  var pendingDelete by remember { mutableStateOf<MemoItem?>(null) }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.memo_title)) },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              imageVector = QuranIcons.ArrowBack,
              contentDescription = stringResource(R.string.memo_back),
              modifier = Modifier.autoMirror()
            )
          }
        },
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
          .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
      )
    }
  ) { innerPadding ->
    val summary = state.summary
    LazyColumn(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .navigationBarsPadding(),
      contentPadding = PaddingValues(16.dp),
      verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
      if (summary != null) {
        item {
          Text(
            text = stringResource(
              R.string.memo_stats, summary.items.size, summary.dueToday.size, summary.totalReviews.toInt()
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        item {
          SectionTitle(stringResource(R.string.memo_due_today_title))
        }
        if (summary.dueToday.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.memo_due_none),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        items(summary.dueToday, key = { "due-${it.id}" }) { item ->
          ItemCard(item, summary.today, naming, highlight = true, state.eventSink) { pendingDelete = item }
        }
        item {
          Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(stringResource(R.string.memo_all_items_title), Modifier.weight(1f))
            FilledTonalButton(onClick = { showAddDialog = true }) {
              Text(stringResource(R.string.memo_add_portion))
            }
          }
        }
        if (summary.items.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.memo_items_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant
            )
          }
        }
        items(summary.items, key = { it.id }) { item ->
          ItemCard(item, summary.today, naming, highlight = false, state.eventSink) { pendingDelete = item }
        }
      }
    }
  }

  if (showAddDialog) {
    AddPortionDialog(
      quranInfo = quranInfo,
      onDismiss = { showAddDialog = false },
      onAdd = { range ->
        state.eventSink(MemorizationEvent.AddPortion(range))
        showAddDialog = false
      }
    )
  }

  val toDelete = pendingDelete
  if (toDelete != null) {
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.memo_delete)) },
      text = { Text(stringResource(R.string.memo_delete_confirm)) },
      confirmButton = {
        TextButton(onClick = {
          state.eventSink(MemorizationEvent.DeletePortion(toDelete.id))
          pendingDelete = null
        }) { Text(stringResource(R.string.memo_delete)) }
      },
      dismissButton = {
        TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.memo_cancel)) }
      }
    )
  }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
  Text(
    text = text,
    style = MaterialTheme.typography.titleMedium,
    fontWeight = FontWeight.Bold,
    modifier = modifier
  )
}

@Composable
private fun ItemCard(
  item: MemoItem,
  today: Int,
  naming: QuranNaming,
  highlight: Boolean,
  eventSink: (MemorizationEvent) -> Unit,
  onDelete: () -> Unit
) {
  val context = LocalContext.current
  Card(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = item.range.format(context, naming),
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.Bold,
          color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(2.dp))
        val diff = item.dueDay - today
        val dueText = when {
          diff > 0 -> pluralStringResource(R.plurals.memo_due_in, diff, diff)
          diff == 0 -> stringResource(R.string.memo_due_today)
          else -> pluralStringResource(R.plurals.memo_overdue, -diff, -diff)
        }
        Text(
          text = dueText + " · " + pluralStringResource(R.plurals.memo_repetitions, item.repetitions, item.repetitions),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
      Spacer(Modifier.width(8.dp))
      Button(onClick = { eventSink(MemorizationEvent.Review(item.id)) }) {
        Text(stringResource(R.string.memo_review_button))
      }
      IconButton(onClick = onDelete) {
        Icon(QuranIcons.Close, contentDescription = stringResource(R.string.memo_delete))
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddPortionDialog(
  quranInfo: QuranInfo,
  onDismiss: () -> Unit,
  onAdd: (AyahRange) -> Unit
) {
  val context = LocalContext.current
  val suraNames = remember { context.resources.getStringArray(UiCoreR.array.sura_names).toList() }
  var sura by rememberSaveable { mutableIntStateOf(1) }
  var fromText by rememberSaveable { mutableStateOf("1") }
  var toText by rememberSaveable { mutableStateOf("") }
  var expanded by remember { mutableStateOf(false) }
  var error by remember { mutableStateOf(false) }

  val ayahCount = quranInfo.getNumberOfAyahs(sura)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.memo_add_portion)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
          OutlinedTextField(
            value = "$sura. ${suraNames.getOrElse(sura - 1) { "" }}",
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.memo_sura)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
              .fillMaxWidth()
              .menuAnchor(MenuAnchorType.PrimaryNotEditable)
          )
          ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            suraNames.forEachIndexed { index, name ->
              DropdownMenuItem(
                text = { Text("${index + 1}. $name") },
                onClick = {
                  sura = index + 1
                  fromText = "1"
                  toText = ""
                  expanded = false
                }
              )
            }
          }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          OutlinedTextField(
            value = fromText,
            onValueChange = { fromText = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text(stringResource(R.string.memo_from_ayah)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
          OutlinedTextField(
            value = toText,
            onValueChange = { toText = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text(stringResource(R.string.memo_to_ayah)) },
            placeholder = { Text(ayahCount.toString()) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f)
          )
        }
        if (error) {
          Text(
            text = stringResource(R.string.memo_invalid_range),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
          )
        }
      }
    },
    confirmButton = {
      TextButton(onClick = {
        val from = fromText.toIntOrNull() ?: 0
        val to = toText.toIntOrNull() ?: ayahCount
        if (from in 1..ayahCount && to in from..ayahCount) {
          onAdd(AyahRange(SuraAyah(sura, from), SuraAyah(sura, to)))
        } else {
          error = true
        }
      }) { Text(stringResource(R.string.memo_add)) }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text(stringResource(R.string.memo_cancel)) }
    }
  )
}
