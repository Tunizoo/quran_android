package com.quran.mobile.feature.wird.domain

/**
 * Computes reading streaks from per-day page counts. A day "counts" when at least [goal] pages
 * were read on it. The current streak is the run of consecutive counted days ending today, or
 * ending yesterday when today's goal has not been reached yet (the streak is then "at risk", not
 * broken).
 *
 * The current goal is applied uniformly to history, so raising the goal can shorten past streaks.
 */
object StreakCalculator {
  data class Result(val current: Int, val best: Int, val isTodayMet: Boolean)

  fun compute(pagesPerDay: Map<Int, Int>, goal: Int, today: Int): Result {
    val effectiveGoal = goal.coerceAtLeast(1)
    val metDays = pagesPerDay.filterValues { it >= effectiveGoal }.keys
    val sortedMetDays = metDays.sorted()

    var best = 0
    var run = 0
    var previous: Int? = null
    for (day in sortedMetDays) {
      run = if (previous != null && day == previous + 1) run + 1 else 1
      if (run > best) best = run
      previous = day
    }

    val isTodayMet = today in metDays
    var cursor = if (isTodayMet) today else today - 1
    var current = 0
    while (cursor in metDays) {
      current++
      cursor--
    }

    return Result(current = current, best = best, isTodayMet = isTodayMet)
  }
}
