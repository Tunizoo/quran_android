package com.quran.mobile.feature.memorization.reader

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quran.labs.androidquran.common.ui.core.QuranIcons
import com.quran.mobile.feature.memorization.R
import com.quran.mobile.feature.memorization.model.Grade
import com.quran.mobile.feature.memorization.session.MemorizationSessionState

interface MemorizationBarActions {
  fun close()
  fun listen(times: Int)
  fun setHidden(hidden: Boolean)
  fun setHint(hint: Boolean)
  fun revealWord()
  fun revealAyah()
  fun revealAll()
  fun hideAll()
  fun grade(grade: Grade)
  fun savePortion()
}

private val REPEAT_CHOICES = listOf(1, 3, 5, 10)

/**
 * The control bar shown over the reader while a memorization session is running. Collapsed by
 * default to a single row so the bottom lines of the page stay visible and tappable; the chevron
 * expands it to the listening, hiding and grading controls.
 */
@Composable
fun MemorizationBar(
  state: MemorizationSessionState,
  title: String,
  actions: MemorizationBarActions,
  modifier: Modifier = Modifier
) {
  var repeatIndex by rememberSaveable { mutableIntStateOf(1) }
  var expanded by rememberSaveable { mutableStateOf(false) }

  Surface(
    modifier = modifier.fillMaxWidth(),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 3.dp,
    shadowElevation = 6.dp
  ) {
    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { actions.close() }) {
          Icon(QuranIcons.Close, contentDescription = stringResource(R.string.memo_bar_close))
        }
        Text(
          text = title,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f)
        )
        if (state.hidden) {
          Button(
            onClick = { actions.revealWord() },
            enabled = !state.completed,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
          ) { Text(stringResource(R.string.memo_next_word)) }
          Spacer(Modifier.width(6.dp))
          Button(
            onClick = { actions.revealAyah() },
            enabled = !state.completed,
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
          ) { Text(stringResource(R.string.memo_next_ayah)) }
        } else {
          FilterChip(
            selected = false,
            onClick = { actions.setHidden(true) },
            label = { Text(stringResource(R.string.memo_hide_again)) }
          )
        }
        IconButton(onClick = { expanded = !expanded }) {
          Icon(
            QuranIcons.ExpandMore,
            contentDescription = null,
            modifier = Modifier.rotate(if (expanded) 0f else 180f)
          )
        }
      }

      if (expanded) {
        Row(
          modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically
        ) {
          FilterChip(
            selected = false,
            onClick = { actions.listen(REPEAT_CHOICES[repeatIndex]) },
            label = { Text(stringResource(R.string.memo_listen)) },
            leadingIcon = { Icon(QuranIcons.PlayArrow, contentDescription = null) }
          )
          FilterChip(
            selected = false,
            onClick = { repeatIndex = (repeatIndex + 1) % REPEAT_CHOICES.size },
            label = { Text(stringResource(R.string.memo_listen_times, REPEAT_CHOICES[repeatIndex])) },
            leadingIcon = { Icon(QuranIcons.Repeat, contentDescription = null) }
          )
          FilterChip(
            selected = state.hidden,
            onClick = { actions.setHidden(!state.hidden) },
            label = { Text(stringResource(if (state.hidden) R.string.memo_hidden else R.string.memo_show)) }
          )
          FilterChip(
            selected = state.hint,
            onClick = { actions.setHint(!state.hint) },
            label = { Text(stringResource(R.string.memo_hint)) }
          )
          if (state.hidden) {
            FilterChip(
              selected = false,
              enabled = !state.completed,
              onClick = { actions.revealAll() },
              label = { Text(stringResource(R.string.memo_reveal_all)) }
            )
            FilterChip(
              selected = false,
              onClick = { actions.hideAll() },
              label = { Text(stringResource(R.string.memo_hide_again)) }
            )
          }
        }

        if (state.itemId != null) {
          Text(
            text = stringResource(if (state.completed) R.string.memo_completed else R.string.memo_rate_prompt),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 6.dp, start = 4.dp)
          )
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
          ) {
            GradeButton(R.string.memo_grade_again, Modifier.weight(1f)) { actions.grade(Grade.AGAIN) }
            GradeButton(R.string.memo_grade_hard, Modifier.weight(1f)) { actions.grade(Grade.HARD) }
            GradeButton(R.string.memo_grade_good, Modifier.weight(1f)) { actions.grade(Grade.GOOD) }
            GradeButton(R.string.memo_grade_easy, Modifier.weight(1f)) { actions.grade(Grade.EASY) }
          }
        } else {
          Row(
            modifier = Modifier
              .fillMaxWidth()
              .padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
          ) {
            Text(
              text = stringResource(R.string.memo_tap_hint),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = { actions.savePortion() }) {
              Text(stringResource(R.string.memo_save_portion))
            }
          }
        }
        Spacer(Modifier.height(2.dp))
      }
    }
  }
}

@Composable
private fun GradeButton(labelRes: Int, modifier: Modifier, onClick: () -> Unit) {
  OutlinedButton(
    onClick = onClick,
    modifier = modifier,
    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp)
  ) {
    Text(stringResource(labelRes), maxLines = 1, overflow = TextOverflow.Ellipsis)
  }
}
