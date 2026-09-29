package com.quran.mobile.feature.divinenames.ui

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.labs.androidquran.common.ui.core.modifier.autoMirror
import com.quran.mobile.feature.divinenames.R
import com.quran.mobile.feature.divinenames.data.DivineNamesDatabase
import com.quran.mobile.feature.divinenames.model.DivineName

@Composable
fun DivineNamesScreen(
  database: DivineNamesDatabase,
  initialNameId: String?,
  onBackPressed: () -> Unit,
  modifier: Modifier = Modifier
) {
  val names by produceState<List<DivineName>?>(initialValue = null, database) {
    value = database.allNames()
  }
  var query by rememberSaveable { mutableStateOf("") }
  var expandedId by rememberSaveable { mutableStateOf(initialNameId) }
  val listState = rememberLazyListState()

  val filtered = remember(names, query) {
    val all = names.orEmpty()
    if (query.isBlank()) all else all.filter {
      it.arabic.contains(query) || it.transliteration.contains(query, ignoreCase = true)
    }
  }

  LaunchedEffect(names, initialNameId) {
    val index = filtered.indexOfFirst { it.id == initialNameId }
    if (index >= 0) listState.scrollToItem(index)
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.divine_names_title)) },
        navigationIcon = {
          IconButton(onClick = onBackPressed) {
            Icon(
              imageVector = QuranIcons.ArrowBack,
              contentDescription = stringResource(R.string.divine_names_back),
              modifier = Modifier.autoMirror()
            )
          }
        },
        windowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout)
          .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
      )
    }
  ) { innerPadding ->
    Column(
      modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding)
        .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
        .navigationBarsPadding()
    ) {
      OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        singleLine = true,
        label = { Text(stringResource(R.string.divine_names_search)) },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 8.dp)
      )
      Text(
        text = stringResource(R.string.divine_names_methodology),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
      )
      LazyColumn(
        state = listState,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
      ) {
        itemsIndexed(filtered, key = { _, item -> item.id }) { _, name ->
          DivineNameCard(
            name = name,
            expanded = expandedId == name.id,
            onToggle = { expandedId = if (expandedId == name.id) null else name.id }
          )
        }
      }
    }
  }
}

@Composable
fun DivineNameCard(
  name: DivineName,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier
) {
  Card(
    modifier = modifier
      .fillMaxWidth()
      .clickable(onClick = onToggle)
  ) {
    Column(modifier = Modifier.padding(16.dp)) {
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
        if (name.isAttribute) {
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.divine_names_kind_attribute),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
          )
        } else if (!name.isInQuran) {
          Spacer(Modifier.width(8.dp))
          Text(
            text = stringResource(R.string.divine_names_from_sunnah),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary
          )
        }
      }
      AnimatedVisibility(visible = expanded) {
        DivineNameDetails(name)
      }
    }
  }
}

@Composable
fun DivineNameDetails(name: DivineName, modifier: Modifier = Modifier) {
  Column(modifier = modifier.padding(top = 12.dp)) {
    Text(
      text = name.meaning,
      style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Rtl, lineHeight = 24.sp)
    )
    if (name.evidence.isNotBlank() || name.evidenceRef.isNotBlank()) {
      Spacer(Modifier.height(10.dp))
      HorizontalDivider()
      Spacer(Modifier.height(10.dp))
      if (name.evidence.isNotBlank()) {
        Text(
          text = "﴿ ${name.evidence} ﴾",
          style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Rtl, lineHeight = 30.sp),
          color = MaterialTheme.colorScheme.primary
        )
      }
      if (name.evidenceRef.isNotBlank()) {
        Text(
          text = name.evidenceRef,
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }
    }
    if (name.note.isNotBlank()) {
      Spacer(Modifier.height(8.dp))
      Text(
        text = name.note,
        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Rtl),
        color = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(Modifier.height(8.dp))
    Text(
      text = name.sources,
      style = MaterialTheme.typography.labelSmall.copy(textDirection = TextDirection.Rtl),
      color = MaterialTheme.colorScheme.outline
    )
  }
}
