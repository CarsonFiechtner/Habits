package io.github.carsonfiechtner.keepup.data.export

/** Entries as CSV for spreadsheets (design doc §13). One row per entry. */
object CsvExport {
    val HEADER = listOf("date", "habit", "value", "unit", "note", "origin", "logged_at")

    fun build(file: ExportFile): String {
        val habits = file.habits.associateBy { it.id }
        val units = file.units.associateBy { it.id }
        val rows = file.entries
            .sortedWith(compareBy({ it.date }, { it.loggedAt }))
            .map { e ->
                val habit = habits[e.habitId]
                val unit = habit?.let { units[it.unitId] }
                listOf(
                    e.date,
                    habit?.name.orEmpty(),
                    e.value.toBigDecimal().stripTrailingZeros().toPlainString(),
                    unit?.let { it.label.ifBlank { it.name } }.orEmpty(),
                    e.note.orEmpty(),
                    e.origin,
                    e.loggedAt,
                )
            }
        return (listOf(HEADER) + rows).joinToString(separator = "\r\n", postfix = "\r\n") { row ->
            row.joinToString(",") { escape(it) }
        }
    }

    /** RFC 4180 quoting. */
    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
}
