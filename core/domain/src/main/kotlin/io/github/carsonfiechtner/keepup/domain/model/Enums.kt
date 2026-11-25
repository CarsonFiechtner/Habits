package io.github.carsonfiechtner.keepup.domain.model

/** Whether the goal is a minimum ("at least 2 L") or a limit ("at most 2 coffees"). */
enum class Direction { AT_LEAST, AT_MOST }

/** BINARY units are done/not done (value 1 per completion); NUMBER units take any non-negative value. */
enum class UnitKind { BINARY, NUMBER }

/** Where an entry came from. Only MANUAL entries can be edited, and only on their own day. */
enum class EntryOrigin { MANUAL, HEALTH_CONNECT, USAGE_STATS, IMPORT }
