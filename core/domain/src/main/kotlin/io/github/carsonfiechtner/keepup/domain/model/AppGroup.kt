package io.github.carsonfiechtner.keepup.domain.model

/** A named set of apps whose screen time is tracked together, e.g. "Social". */
data class AppGroup(val id: Long, val name: String, val packages: Set<String>)
