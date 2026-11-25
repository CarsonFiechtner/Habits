package io.github.carsonfiechtner.keepup.domain.usage

import com.google.common.truth.Truth.assertThat
import io.github.carsonfiechtner.keepup.domain.usage.UsageEvent.Type.PAUSED
import io.github.carsonfiechtner.keepup.domain.usage.UsageEvent.Type.RESUMED
import io.github.carsonfiechtner.keepup.domain.usage.UsageEvent.Type.SCREEN_OFF
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class UsageAggregatorTest {
    private val zone = ZoneId.of("America/Chicago")
    private val day = LocalDate.parse("2026-11-25")
    private fun at(h: Int, m: Int = 0, d: LocalDate = day): Instant = d.atTime(h, m).atZone(zone).toInstant()
    private fun ev(pkg: String?, t: Instant, type: UsageEvent.Type) = UsageEvent(pkg, t, type)
    private val start = day.atStartOfDay(zone).toInstant()
    private val min = 60_000L

    @Test
    fun `resume then pause counts the session`() {
        val events = listOf(ev("ig", at(9), RESUMED), ev("ig", at(9, 30), PAUSED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))).isEqualTo(mapOf(day to mapOf("ig" to 30 * min)))
    }

    @Test
    fun `switching apps closes the previous one`() {
        val events = listOf(
            ev("ig", at(9), RESUMED),
            ev("reddit", at(9, 10), RESUMED), // ig's pause arrives after the next resume
            ev("ig", at(9, 10), PAUSED),
            ev("reddit", at(9, 25), PAUSED),
        )
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day])
            .containsExactly("ig", 10 * min, "reddit", 15 * min)
    }

    @Test
    fun `screen off ends the session`() {
        val events = listOf(ev("ig", at(9), RESUMED), ev(null, at(9, 5), SCREEN_OFF), ev("ig", at(10), PAUSED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day]).containsExactly("ig", 5 * min)
    }

    @Test
    fun `sessions crossing midnight are split`() {
        val next = day.plusDays(1)
        val events = listOf(ev("yt", at(23, 30), RESUMED), ev("yt", at(0, 20, next), PAUSED))
        val result = UsageAggregator.foregroundByDay(events, zone, start, at(12, 0, next))
        assertThat(result[day]).containsExactly("yt", 30 * min)
        assertThat(result[next]).containsExactly("yt", 20 * min)
    }

    @Test
    fun `an app open at the start of the range counts from the start`() {
        val events = listOf(ev("ig", at(0, 15), PAUSED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day]).containsExactly("ig", 15 * min)
    }

    @Test
    fun `an app still open counts until the end of the range`() {
        val events = listOf(ev("ig", at(22, 50), RESUMED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day]).containsExactly("ig", 10 * min)
    }

    @Test
    fun `stray pauses are ignored`() {
        val events = listOf(ev("ig", at(9), RESUMED), ev("ig", at(9, 10), PAUSED), ev("other", at(9, 20), PAUSED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day]).containsExactly("ig", 10 * min)
    }

    @Test
    fun `repeated resumes of the same app do not double count`() {
        val events = listOf(ev("ig", at(9), RESUMED), ev("ig", at(9, 5), RESUMED), ev("ig", at(9, 10), PAUSED))
        assertThat(UsageAggregator.foregroundByDay(events, zone, start, at(23))[day]).containsExactly("ig", 10 * min)
    }
}
