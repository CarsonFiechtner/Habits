package io.github.carsonfiechtner.keepup.domain.model

/** A user-defined (or built-in) unit such as "Liters (L)", "pages" or the binary "Done". */
data class MeasureUnit(
    val id: Long,
    val name: String,
    val label: String,
    val kind: UnitKind,
    val decimals: Int,
    val quickAddStep: Double,
    val isBuiltin: Boolean,
) {
    init {
        require(decimals in 0..MAX_DECIMALS) { "decimals must be in 0..$MAX_DECIMALS, was $decimals" }
        require(quickAddStep > 0) { "quickAddStep must be positive, was $quickAddStep" }
    }

    val isBinary: Boolean get() = kind == UnitKind.BINARY

    companion object {
        const val MAX_DECIMALS = 3
    }
}
