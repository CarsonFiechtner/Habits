package io.github.carsonfiechtner.keepup.data.export

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvExportTest {
    @Test
    fun `fields with commas quotes or newlines are quoted`() {
        assertThat(CsvExport.escape("plain")).isEqualTo("plain")
        assertThat(CsvExport.escape("a,b")).isEqualTo("\"a,b\"")
        assertThat(CsvExport.escape("say \"hi\"")).isEqualTo("\"say \"\"hi\"\"\"")
        assertThat(CsvExport.escape("two\nlines")).isEqualTo("\"two\nlines\"")
    }

    @Test
    fun `builds one row per entry with habit and unit names`() {
        val file = ExportFile(
            appVersion = "test",
            exportedAt = "2026-11-25T12:00:00Z",
            units = listOf(UnitDto(2, "Liters", "L", "NUMBER", 2, 0.25, true)),
            habits = listOf(
                HabitDto(1, "Water, daily", null, "💧", 0, "DAILY", "2026-11-25", 2, "MANUAL", null, 0, "2026-11-25T08:00:00Z", null),
            ),
            goals = listOf(GoalDto(1, "2026-11-25", "AT_LEAST", 2.0)),
            entries = listOf(
                EntryDto(2, 1, "2026-11-26", "2026-11-26T09:00:00Z", 1.50, null, "MANUAL", null),
                EntryDto(1, 1, "2026-11-25", "2026-11-25T09:00:00Z", 0.25, "first", "MANUAL", null),
            ),
        )
        val lines = CsvExport.build(file).trimEnd().split("\r\n")
        assertThat(lines).containsExactly(
            "date,habit,value,unit,note,origin,logged_at",
            "2026-11-25,\"Water, daily\",0.25,L,first,MANUAL,2026-11-25T09:00:00Z",
            "2026-11-26,\"Water, daily\",1.5,L,,MANUAL,2026-11-26T09:00:00Z",
        ).inOrder()
    }
}
