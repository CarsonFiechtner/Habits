package io.github.carsonfiechtner.keepup.domain.usage

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** A simplified Usage Stats event (design doc §10.3). */
data class UsageEvent(val packageName: String?, val time: Instant, val type: Type) {
    enum class Type {
        /** An app's activity came to the foreground. */
        RESUMED,

        /** An app's activity left the foreground. */
        PAUSED,

        /** Screen turned off, phone locked or shut down: nothing is in the foreground anymore. */
        SCREEN_OFF,
    }
}

/**
 * Turns resume/pause events into foreground time per local day per app. Sessions that cross midnight
 * are split between the two days.
 */
object UsageAggregator {

    fun foregroundByDay(
        events: List<UsageEvent>,
        zone: ZoneId,
        rangeStart: Instant,
        rangeEnd: Instant,
    ): Map<LocalDate, Map<String, Long>> {
        val result = mutableMapOf<LocalDate, MutableMap<String, Long>>()
        var current: String? = null
        var since: Instant = rangeStart
        var sawAnyEvent = false

        fun close(at: Instant) {
            val pkg = current ?: return
            addSplitByDay(result, pkg, since, minOf(at, rangeEnd), zone)
            current = null
        }

        for (event in events.sortedBy { it.time }) {
            if (event.time.isAfter(rangeEnd)) break
            val t = maxOf(event.time, rangeStart)
            when (event.type) {
                UsageEvent.Type.RESUMED -> {
                    val pkg = event.packageName ?: continue
                    if (current != pkg) {
                        close(t)
                        current = pkg
                        since = t
                    }
                }
                UsageEvent.Type.PAUSED -> {
                    val pkg = event.packageName
                    if (current == pkg) {
                        close(t)
                    } else if (!sawAnyEvent && current == null && pkg != null) {
                        // The app was already in the foreground when the range started.
                        addSplitByDay(result, pkg, rangeStart, t, zone)
                    }
                }
                UsageEvent.Type.SCREEN_OFF -> close(t)
            }
            sawAnyEvent = true
        }
        close(rangeEnd) // still in the foreground now
        return result
    }

    private fun addSplitByDay(
        into: MutableMap<LocalDate, MutableMap<String, Long>>,
        pkg: String,
        start: Instant,
        end: Instant,
        zone: ZoneId,
    ) {
        var from = start
        while (from.isBefore(end)) {
            val day = from.atZone(zone).toLocalDate()
            val nextMidnight = day.plusDays(1).atStartOfDay(zone).toInstant()
            val to = minOf(end, nextMidnight)
            val ms = to.toEpochMilli() - from.toEpochMilli()
            if (ms > 0) {
                val perApp = into.getOrPut(day) { mutableMapOf() }
                perApp[pkg] = (perApp[pkg] ?: 0L) + ms
            }
            from = to
        }
    }
}
