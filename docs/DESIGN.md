# Habits — Android Habit Tracker: Design Document

| | |
|---|---|
| **Status** | Draft v0.1, open for review |
| **Last updated** | 2026-11-25 |
| **Audience** | Me (sole user and developer), plus anyone or anything helping build it |

> Every decision here that I made on my own is labeled **[Assumption]** and linked to a question in
> [§17 Open Questions](#17-open-questions). Once those are answered I'll update the doc and drop the labels.

---

## Table of Contents

1. [Summary](#1-summary)
2. [Goals and Non-Goals](#2-goals-and-non-goals)
3. [Core Concepts and Glossary](#3-core-concepts-and-glossary)
4. [Functional Requirements](#4-functional-requirements)
5. [Domain Model](#5-domain-model)
6. [Progress Evaluation, Status and Streaks](#6-progress-evaluation-status-and-streaks)
7. [User Experience](#7-user-experience)
8. [Technical Architecture](#8-technical-architecture)
9. [Data Layer and Schema](#9-data-layer-and-schema)
10. [Automatic Tracking (Stretch)](#10-automatic-tracking-stretch)
11. [Reminders, Notifications and Widgets](#11-reminders-notifications-and-widgets)
12. [Time, Dates and Time Zones](#12-time-dates-and-time-zones)
13. [Backup, Export and Privacy](#13-backup-export-and-privacy)
14. [Testing Strategy](#14-testing-strategy)
15. [Build, CI and Distribution](#15-build-ci-and-distribution)
16. [Milestones](#16-milestones)
17. [Open Questions](#17-open-questions)
18. [Risks](#18-risks)
19. [Appendix: Example Habit Configurations](#appendix-a-example-habit-configurations)

---

## 1. Summary

Habits is a personal, offline-first Android app for tracking any number of habits. Each habit has:

- **a period**: daily, weekly or monthly;
- **a unit I define**: done/not done, minutes, liters, pages, and so on;
- **a goal**: e.g. "at least 2 L per day", "at most 60 min of social media per day", "gym 3 times per week".

I log progress by hand. As a stretch goal, some habits fill in automatically from **Health Connect** (steps,
sleep, exercise, and similar) and from Android's **Usage Stats** API (screen time, per-app usage, unlocks).

The app is for one user on one device. It has no accounts and no server. All data stays on the phone, with
export and backup to a file.

## 2. Goals and Non-Goals

### Goals

- **G1.** Fast daily logging: you can log any habit from the Today screen in one or two taps.
- **G2.** Flexible habit definitions: any period, any user-defined unit, "at least" and "at most" goals,
  and period goals counted either as a total quantity or as a number of successful days.
- **G3.** Useful history: a calendar or heatmap view, charts, streaks and completion rates per habit.
- **G4.** Trustworthy data: past entries can be edited, and changing a goal doesn't rewrite history. Nothing
  is ever silently lost; the export is lossless.
- **G5 (stretch).** Automatic tracking from Health Connect and Usage Stats, so habits like "10k steps" or
  "under 2 h screen time" need no manual input.
- **G6.** Maintainable codebase: modern Android stack, a tested domain core, and CI that builds an
  installable APK.

### Non-Goals (for now)

- Multiple users, accounts, social features, sharing.
- A cloud backend or cross-device sync. File export/import covers moving to a new phone.
- iOS, web or Wear OS apps. Wear OS or a tile could come later.
- Gamification beyond streaks (XP, levels, rewards).
- Publishing on the Play Store. The app is sideloaded. Some decisions below rely on this, notably the
  Usage Stats permission.
- Blocking or limiting apps. We *measure* usage; we don't enforce limits.

## 3. Core Concepts and Glossary

| Term | Meaning |
|---|---|
| **Habit** | Something I want to track, with a period, unit, goal and data source. |
| **Unit** | What's being measured. Either *binary* (done/not done) or *quantitative* (a number with a label such as "min", "L" or "pages"). Units are user-defined and reusable across habits. |
| **Period** | The window a goal applies to: `DAILY`, `WEEKLY` or `MONTHLY`. |
| **Entry** | One logged data point: a value (1 for binary) on a date, plus an optional note. A habit can have many entries per day; for example, "250 ml" logged several times. |
| **Goal** | Direction (`AT_LEAST` / `AT_MOST`) + target value + counting mode (see below). |
| **Counting mode** | For weekly/monthly habits, how the period's progress is computed: **TOTAL** (sum all entries in the period, e.g. "read 300 pages per month") or **DAYS** (count days that met a daily threshold, e.g. "gym 3 days per week"). Daily habits always use the day's total. |
| **Data source** | `MANUAL`, `HEALTH_CONNECT(metric)` or `USAGE_STATS(metric, apps)`. |
| **Period status** | The result of evaluating a period: `COMPLETED`, `PARTIAL`, `MISSED`, `SKIPPED`, `IN_PROGRESS`, `NOT_SCHEDULED`. |
| **Streak** | Number of consecutive completed periods, counted in the habit's own period: days, weeks or months. |
| **Skip** | Marking a period as excused (sick, vacation). It neither breaks nor extends a streak. |

## 4. Functional Requirements

Priority: **P0** = MVP, **P1** = soon after MVP, **P2** = stretch or nice-to-have.

### 4.1 Habit management

| ID | Requirement | Pri |
|---|---|---|
| H1 | Create, edit, archive and delete habits. Archived habits are hidden from Today but keep their history. | P0 |
| H2 | Habit fields: name, optional description, icon (emoji or Material icon), color, period, unit, goal, data source. | P0 |
| H3 | Daily habits can apply to specific weekdays only (e.g. Mon/Wed/Fri). Unscheduled days show as `NOT_SCHEDULED` and don't break streaks. **[Assumption → Q3]** | P0 |
| H4 | Changing a goal or target takes effect from a chosen date (default: today). Past periods keep their old goal. | P0 |
| H5 | Reorder habits on the Today screen (drag and drop). | P1 |
| H6 | Group habits into categories or tags (e.g. Health, Mind, Digital). **[Assumption → Q12]** | P2 |
| H7 | "Every N days" scheduling and "X times per period" with a minimum gap. | P2 |

### 4.2 Units

| ID | Requirement | Pri |
|---|---|---|
| U1 | Built-in units: *Done* (binary), *times*, *minutes*, *hours*, *steps*, *liters*, *ml*, *pages*, *km*. | P0 |
| U2 | Create custom units: name, short label, decimal places (0–3), and a default quick-add step (e.g. +250 for ml). | P0 |
| U3 | Duration units accept `h:mm` input and display as `1h 25m`. | P1 |
| U4 | Unit conversion (e.g. log in ml, goal in L). Deferred. Each habit uses a single unit. | P2 |

### 4.3 Logging

| ID | Requirement | Pri |
|---|---|---|
| L1 | Binary habits: one tap on Today toggles done/undone. | P0 |
| L2 | Quantitative habits: tap a quick-add chip (+1 step), or open a number pad to enter an exact value. | P0 |
| L3 | Multiple entries per day are summed. View, edit and delete individual entries. | P0 |
| L4 | Log for past dates (backfill) from the calendar or detail screen. | P0 |
| L5 | Optional note per entry. | P1 |
| L6 | Mark a period as skipped (with an optional reason). | P1 |
| L7 | Undo snackbar after any log or delete. | P0 |

### 4.4 History and insights

| ID | Requirement | Pri |
|---|---|---|
| I1 | Per habit: current streak, best streak, completion rate (last 30 days / 12 weeks / 12 months, and all-time). | P0 |
| I2 | Per habit: calendar or heatmap view of period statuses, with intensity proportional to progress. | P0 |
| I3 | Per habit: bar or line chart of values over time, with the goal line overlaid. | P1 |
| I4 | Overview: a "today" ring and a weekly summary across all habits. | P1 |

### 4.5 Automatic tracking (stretch)

| ID | Requirement | Pri |
|---|---|---|
| A1 | A habit can use Health Connect as its data source: steps, plus others (see §10.2). | P2 |
| A2 | A habit can use Usage Stats as its data source: total screen time, time in selected apps, or unlock count. | P2 |
| A3 | Auto-tracked values refresh when the app opens and in the background. Each one shows its source and last-sync time. | P2 |
| A4 | Auto-tracked habits are read-only by default, with an optional manual adjustment entry on top. | P2 |
| A5 | Backfill history on first setup, as far back as each source allows. | P2 |

### 4.6 Other

| ID | Requirement | Pri |
|---|---|---|
| O1 | Per-habit reminder notifications at a set time, on scheduled days, suppressed if already completed. **[Assumption → Q7]** | P1 |
| O2 | Home-screen widget showing today's habits, with tap-to-log. **[Assumption → Q8]** | P1 |
| O3 | Export and import all data as JSON. Export entries as CSV. | P0 for export, P1 for import |
| O4 | Settings: first day of week, day-boundary hour, theme (system/light/dark, Material You dynamic color). | P0 |
| O5 | Light and dark themes, with dynamic color on Android 12+. | P0 |

## 5. Domain Model

The domain layer is pure Kotlin with no Android dependencies, so it can be unit-tested exhaustively.

```kotlin
enum class Period { DAILY, WEEKLY, MONTHLY }
enum class Direction { AT_LEAST, AT_MOST }
enum class CountingMode { TOTAL, DAYS }        // only meaningful for WEEKLY / MONTHLY

data class Unit(
    val id: Long,
    val name: String,          // "Liters"
    val label: String,         // "L"
    val kind: UnitKind,        // BINARY, NUMBER, DURATION
    val decimals: Int,         // display precision
    val quickAddStep: Double,  // default "+" amount
)

data class Goal(
    val direction: Direction,
    val target: Double,              // TOTAL mode: period quantity; DAYS mode: number of days
    val countingMode: CountingMode,
    val dailyThreshold: Double?,     // DAYS mode only: what counts as a "successful day" (binary → 1)
    val effectiveFrom: LocalDate,    // goal versioning (H4)
)

sealed interface DataSource {
    data object Manual : DataSource
    data class HealthConnect(val metric: HealthMetric) : DataSource
    data class UsageStats(val metric: UsageMetric, val packages: Set<String>) : DataSource
}

data class Habit(
    val id: Long,
    val name: String,
    val description: String?,
    val icon: String,
    val color: Int,
    val period: Period,
    val unit: Unit,
    val goals: List<Goal>,               // sorted by effectiveFrom; the one in force for a date wins
    val schedule: Schedule,              // e.g. weekdays for DAILY habits; All for others
    val source: DataSource,
    val reminder: Reminder?,
    val archivedAt: Instant?,
    val sortOrder: Int,
    val createdAt: Instant,
)

data class Entry(
    val id: Long,
    val habitId: Long,
    val date: LocalDate,        // the "logical day" this counts toward (see §12)
    val loggedAt: Instant,      // wall-clock time of the log
    val value: Double,          // 1.0 for binary
    val note: String?,
    val origin: EntryOrigin,    // MANUAL, HEALTH_CONNECT, USAGE_STATS, IMPORT
)

data class Skip(val habitId: Long, val periodStart: LocalDate, val reason: String?)
```

### Why goals are versioned

If I raise "water" from 2 L to 3 L, last month's days shouldn't turn from green to red. Each goal row has an
`effectiveFrom` date. To evaluate a period, we use the goal in force on the period's **start date**.
Editing a goal in the UI asks "Apply from: today / start of this period / all history", defaulting to
today.

### Binary habits

A binary habit is just a quantitative habit with value `1` per completion and an `AT_LEAST 1` daily goal.
That lets one engine handle everything. Toggling on inserts an entry with value `1`; toggling off deletes
that day's entries.

## 6. Progress Evaluation, Status and Streaks

This is the heart of the app. It lives in a pure function that is easy to test:

```kotlin
fun evaluate(habit: Habit, period: PeriodRange, entries: List<Entry>, skips: List<Skip>, today: LocalDate): PeriodResult

data class PeriodResult(
    val range: PeriodRange,       // start..end dates
    val value: Double,            // total or day-count, depending on counting mode
    val target: Double,
    val progress: Float,          // 0..1, clamped. For AT_MOST: 1 = still within the limit
    val status: PeriodStatus,
)
```

### 6.1 Computing the period value

| Period | Counting mode | Value |
|---|---|---|
| DAILY | (n/a) | Sum of the day's entry values |
| WEEKLY/MONTHLY | TOTAL | Sum of all entry values in the period |
| WEEKLY/MONTHLY | DAYS | Number of days in the period whose daily sum meets `dailyThreshold` |

### 6.2 Status rules

| Situation | `AT_LEAST` | `AT_MOST` |
|---|---|---|
| Period is in the future, or before the habit was created | `NOT_SCHEDULED` | `NOT_SCHEDULED` |
| Day not in schedule (DAILY with weekdays) | `NOT_SCHEDULED` | `NOT_SCHEDULED` |
| A skip exists for the period | `SKIPPED` | `SKIPPED` |
| Current period, goal met | `COMPLETED` (locks in early) | `IN_PROGRESS` ("on track"). It can't be confirmed until the period ends |
| Current period, goal not yet met | `IN_PROGRESS` | `MISSED` as soon as the limit is exceeded; otherwise `IN_PROGRESS` |
| Past period, goal met | `COMPLETED` | `COMPLETED` |
| Past period, 0 < value < target | `PARTIAL` (counts as missed for streaks) | n/a |
| Past period, value = 0 | `MISSED` | `COMPLETED` |

> **Edge case: `AT_MOST` with no data.** For a *manual* "at most" habit (e.g. "≤ 2 coffees"), no entries
> could mean "zero coffees" or "forgot to log". I'll treat no data as success, since that's what the data
> says. **[Assumption → Q5]** For *auto-tracked* habits, a day with no sync data (e.g. the phone was off,
> or there's a gap in the data) is shown as `NO_DATA` and neither breaks nor extends streaks.

### 6.3 Streaks

- **Current streak** = the number of consecutive `COMPLETED` periods, walking backward from the most recent
  *closed* period, plus 1 if the current period is already `COMPLETED`. An `IN_PROGRESS` current period
  doesn't break the streak.
- `SKIPPED`, `NOT_SCHEDULED` and `NO_DATA` periods are **transparent**: they don't break or extend the streak.
- `MISSED` and `PARTIAL` break it.
- **Best streak** uses the same rule over all history.
- Streak units match the period: "12 days", "5 weeks", "3 months".

### 6.4 Performance

The number of habits (tens) and the data volume (a few years ≈ thousands of rows per habit) are small. The
plan:

- Today and Widget screens query only the current period's entries per habit, via an indexed
  `(habit_id, date)` query.
- History screens load one habit's full entry list, aggregate it per day in SQL (`GROUP BY date`), and run
  `evaluate` in memory. Thousands of rows take microseconds.
- If needed later, a `period_summary` cache table can be added. It isn't needed for the MVP.

## 7. User Experience

Built with Material 3 and Jetpack Compose. Navigation uses a bottom bar with three destinations:
**Today**, **History** and **Settings**.

### 7.1 Screens

1. **Today** (home)
   - Date header with ◀ ▶ arrows to move between days. Logging for another day happens here too.
   - Habits are grouped into sections: **Daily**, **This week**, **This month**. Each row shows the icon,
     name, a progress ring or bar, "1.2 / 2 L" text, and the streak 🔥.
     - Binary: a tap on the row's check toggles it.
     - Quantitative: a "+" chip adds the quick-add step; a long-press or tap on the value opens the numeric
       entry sheet.
     - Auto-tracked: shows a source badge (♥ Health / 📱 Usage) and "synced 5 min ago". The row has no "+"
       chip.
   - Completed habits can optionally sink to the bottom or collapse.
   - FAB: **New habit**.
2. **Add / Edit Habit**: a single scrollable form with progressive disclosure:
   1. Name, icon, color
   2. **What are you tracking?** → Done/not done · A quantity (pick or create a unit) · Automatic (Health /
      Screen time). *Automatic is hidden until M4/M5.*
   3. **How often?** → Daily (choose weekdays) · Weekly · Monthly
   4. **Goal** → At least / At most · target · (weekly/monthly) "in total" vs "on N days" with a daily
      threshold
   5. Reminder (time, days)
   6. A live preview sentence: *"At least **2 L** of water **every day**"* / *"Gym on **3 days** each
      **week**"*.
3. **Habit Detail**
   - Header stats: current streak, best streak, completion % (selectable range).
   - Calendar heatmap (month pages for daily habits; a week or month grid for others).
   - Chart of values with the goal line overlaid (week / month / year ranges).
   - Entry list with edit, delete and add-past-entry actions; skip / unskip a period.
   - Goal history ("since Mar 3: 3 L; before: 2 L").
4. **History / Overview**: across all habits, a week-by-week grid (rows = habits, columns = days),
   plus the overall completion trend.
5. **Units**: list and edit custom units. Reached from Settings and from the unit picker.
6. **Settings**: first day of week, day-boundary hour, theme, reminders master switch, export/import,
   permissions and integrations status (Health Connect, Usage access), archived habits, about/version.

### 7.2 Interaction principles

- Every destructive action has an **Undo** snackbar or a confirmation.
- Haptic feedback on completion; a subtle animation when a goal flips to completed.
- Every log takes one tap from Today. Detail screens are for reviewing and correcting.
- Accessibility: content descriptions on progress indicators, minimum 48dp touch targets, and dynamic type
  scaling.

## 8. Technical Architecture

### 8.1 Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | **Kotlin** (latest stable, K2) | |
| UI | **Jetpack Compose + Material 3** | Dynamic color, edge-to-edge. |
| Architecture | **MVVM + unidirectional data flow** | `ViewModel` exposes `StateFlow<UiState>`. Screens send events. |
| Async | Kotlin **Coroutines + Flow** | Room DAOs return `Flow`, so the UI updates reactively. |
| Persistence | **Room** (SQLite) | Exported schemas plus migration tests. |
| Preferences | **DataStore (Preferences)** | Settings: week start, day boundary, theme, last-sync tokens. |
| DI | **Hilt** | Manual DI is viable at this size, but Hilt integrates cleanly with WorkManager and ViewModels. |
| Navigation | **Navigation Compose** with type-safe routes | |
| Background work | **WorkManager** | Auto-tracking sync; reminder rescheduling. |
| Reminders | **AlarmManager** (`setExactAndAllowWhileIdle` when permitted, otherwise inexact) + notifications | WorkManager isn't precise enough for "remind me at 21:00". |
| Widgets | **Jetpack Glance** | Compose-style API for app widgets. |
| Charts | **Vico** (Compose charts library), or hand-drawn Canvas for the heatmap | The heatmap is simple enough to draw directly. |
| Health | **Health Connect** client (`androidx.health.connect:connect-client`) | Stretch. |
| Usage | **`UsageStatsManager`** (platform API) | Stretch. |
| Serialization | **kotlinx.serialization** | Export/import JSON and type-safe nav routes. |
| Date/time | **`java.time`** (native on API 26+) | |
| Testing | JUnit5/JUnit4, **Turbine** (Flow), **Truth**, Room in-memory DB, Compose UI test, Robolectric where useful | |
| Build | Gradle Kotlin DSL, **version catalog** (`libs.versions.toml`), KSP | |
| Static analysis | **ktlint** (via Spotless) + **detekt**, Android Lint | Kept light; it's a personal project. |

**SDK levels [Assumption → Q1]:** `minSdk 28` (Android 9; the floor for Health Connect), `targetSdk`/`compileSdk`
= latest stable. On Android 14+, Health Connect is built into the OS. On 9–13, it's an installable app.

### 8.2 Layering

```
┌──────────────────────────────────────────────────────────────────────────┐
│ UI layer (Compose screens, Glance widget)                                │
│   TodayScreen  HabitEditScreen  HabitDetailScreen  HistoryScreen  ...    │
│        │ events ▲ UiState (StateFlow)                                    │
│ ViewModels ────────────────────────────────────────────────────────────  │
├──────────────────────────────────────────────────────────────────────────┤
│ Domain layer (pure Kotlin)                                               │
│   Models · PeriodCalculator · HabitEvaluator · StreakCalculator          │
│   Use cases: LogEntry, ToggleBinary, GetTodayOverview, GetHabitStats ... │
├──────────────────────────────────────────────────────────────────────────┤
│ Data layer                                                               │
│   HabitRepository  EntryRepository  UnitRepository  SettingsRepository   │
│        │                    ▲                                            │
│   Room (DAOs)          AutoTrackingSync ◄── DataSourceAdapters           │
│   DataStore              (WorkManager)       ├─ HealthConnectAdapter     │
│                                              └─ UsageStatsAdapter        │
├──────────────────────────────────────────────────────────────────────────┤
│ Platform: Notifications/AlarmManager · Health Connect · UsageStatsManager│
└──────────────────────────────────────────────────────────────────────────┘
```

### 8.3 Module layout

Start with a **single `:app` module** organized by package. A personal app doesn't justify multi-module
build overhead. The one exception is a separate **`:core:domain`** pure-Kotlin (JVM) module, which keeps
the evaluation logic Android-free and runs its tests fast on the JVM.

```
:app
  └─ com.<me>.habits
       ├─ ui/            today/, edit/, detail/, history/, settings/, units/, components/, theme/
       ├─ data/          db/ (entities, DAOs, migrations), repo/, prefs/, export/
       ├─ autotrack/     health/, usage/, sync/
       ├─ reminders/     ReminderScheduler, receivers
       ├─ widget/        Glance widget + actions
       └─ di/
:core:domain             models, evaluator, streaks, period math, use-case interfaces
```

The package/app ID is TBD. **[→ Q14]**

## 9. Data Layer and Schema

### 9.1 Room tables

```sql
unit (
  id            INTEGER PK,
  name          TEXT NOT NULL,
  label         TEXT NOT NULL,
  kind          TEXT NOT NULL,          -- BINARY | NUMBER | DURATION
  decimals      INTEGER NOT NULL DEFAULT 0,
  quick_add     REAL NOT NULL DEFAULT 1,
  is_builtin    INTEGER NOT NULL DEFAULT 0
)

habit (
  id            INTEGER PK,
  name          TEXT NOT NULL,
  description   TEXT,
  icon          TEXT NOT NULL,
  color         INTEGER NOT NULL,
  period        TEXT NOT NULL,          -- DAILY | WEEKLY | MONTHLY
  unit_id       INTEGER NOT NULL REFERENCES unit(id),
  schedule_days INTEGER NOT NULL DEFAULT 127,  -- bitmask Mon..Sun, DAILY only
  source_type   TEXT NOT NULL DEFAULT 'MANUAL',
  source_config TEXT,                   -- JSON: metric, package list, etc.
  sort_order    INTEGER NOT NULL,
  created_at    INTEGER NOT NULL,       -- epoch millis
  archived_at   INTEGER
)

habit_goal (
  id              INTEGER PK,
  habit_id        INTEGER NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
  effective_from  INTEGER NOT NULL,     -- epoch day
  direction       TEXT NOT NULL,        -- AT_LEAST | AT_MOST
  target          REAL NOT NULL,
  counting_mode   TEXT NOT NULL,        -- TOTAL | DAYS
  daily_threshold REAL,
  UNIQUE(habit_id, effective_from)
)

entry (
  id            INTEGER PK,
  habit_id      INTEGER NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
  date          INTEGER NOT NULL,       -- epoch day (logical day, see §12)
  logged_at     INTEGER NOT NULL,       -- epoch millis
  value         REAL NOT NULL,
  note          TEXT,
  origin        TEXT NOT NULL,          -- MANUAL | HEALTH_CONNECT | USAGE_STATS | IMPORT
  external_key  TEXT,                   -- for idempotent auto-sync upserts, e.g. "hc:steps:2026-11-25"
  UNIQUE(habit_id, external_key)
)
INDEX entry(habit_id, date)

skip (
  habit_id      INTEGER NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
  period_start  INTEGER NOT NULL,       -- epoch day of the period start
  reason        TEXT,
  PRIMARY KEY (habit_id, period_start)
)

reminder (
  id            INTEGER PK,
  habit_id      INTEGER NOT NULL REFERENCES habit(id) ON DELETE CASCADE,
  time_of_day   INTEGER NOT NULL,       -- minutes after midnight
  days_mask     INTEGER NOT NULL,       -- Mon..Sun bitmask
  enabled       INTEGER NOT NULL DEFAULT 1
)

-- Stretch: raw usage data captured before the OS discards it (see §10.3)
usage_daily (
  date          INTEGER NOT NULL,       -- epoch day
  package       TEXT NOT NULL,          -- "*" = total screen time; "#unlocks" = unlock count
  foreground_ms INTEGER NOT NULL,
  PRIMARY KEY (date, package)
)
```

### 9.2 Decisions

- **Values are `REAL` (double).** Precision is fine for personal quantities. Display rounding uses the
  unit's `decimals`. Sums are rounded to that precision before comparing with the target, so 0.1 + 0.2
  doesn't fail a 0.3 goal.
- **Dates are stored as epoch days**, not timestamps, for anything that's about "which day." This keeps
  time zones from moving entries between days (§12).
- **Auto-tracked values are entries too,** with `origin` ≠ `MANUAL` and a deterministic `external_key`.
  Re-syncing a day *replaces* that day's value rather than adding to it. Evaluation, history, export and
  charts then work the same for all habits.
- **Schema migrations:** `exportSchema = true`. Every version bump ships with a migration and a
  `MigrationTestHelper` test. Losing data in my own tracker is the worst failure this app can have.

## 10. Automatic Tracking (Stretch)

### 10.1 Common design

```kotlin
interface DataSourceAdapter {
    val type: SourceType
    suspend fun availability(): Availability   // NOT_INSTALLED, NEEDS_PERMISSION, READY
    suspend fun permissionsIntent(): Intent?
    /** Returns one value per logical day in [range]; null for days with no data. */
    suspend fun dailyValues(config: SourceConfig, range: ClosedRange<LocalDate>): Map<LocalDate, Double?>
}
```

`AutoTrackingSync` is a `CoroutineWorker`:

1. Runs **periodically** (every 15 min, the WorkManager minimum, or 1 h to save battery; configurable),
   when the app **comes to the foreground**, and on **pull-to-refresh** on Today.
2. For each non-archived auto habit, it fetches `dailyValues` for **[last successful sync − 1 day, today]**.
   A first sync fetches the maximum available history.
3. It upserts one entry per day per habit, keyed `"<source>:<metric>:<date>"`.
4. It records the last-sync time in DataStore, and the UI shows it.

Manual adjustments on auto habits (A4) are separate `MANUAL` entries, so they survive re-syncs.

### 10.2 Health Connect

Health Connect is Android's on-device health data hub. Samsung Health, Fitbit/Google Fit, Oura, Garmin
(via bridges) and others write to it, and we read from it. **[→ Q9: which app currently holds the data?]**

| Candidate metric | Record type | Aggregation | Example habit |
|---|---|---|---|
| Steps | `StepsRecord` | `COUNT_TOTAL` (deduplicated across sources by HC) | ≥ 10,000 steps/day |
| Exercise minutes | `ExerciseSessionRecord` | sum of durations (optionally filtered by exercise type) | ≥ 150 min/week |
| Sleep duration | `SleepSessionRecord` | session length attributed to wake-up day | ≥ 7 h/night |
| Distance | `DistanceRecord` | `DISTANCE_TOTAL` | ≥ 20 km/week |
| Active calories | `ActiveCaloriesBurnedRecord` | `ACTIVE_CALORIES_TOTAL` | |
| Hydration | `HydrationRecord` | `VOLUME_TOTAL` | ≥ 2 L/day (could also *write* manual water logs back to HC) |
| Mindfulness | `MindfulnessSessionRecord` (newer API) | sum of durations | ≥ 10 min/day |

Implementation notes:

- Use `aggregateGroupByPeriod` / `aggregateGroupByDuration` with per-day slices in the local zone. Health
  Connect's own aggregation **deduplicates overlapping sources** (e.g. phone and watch both counting
  steps) using the user's data-priority settings.
- **Permissions:** declare the specific `android.permission.health.READ_*` permissions in the manifest and
  request them with `PermissionController.createRequestPermissionResultContract()`. The manifest also
  needs the privacy-policy rationale activity (`ACTION_SHOW_PERMISSIONS_RATIONALE` /
  `VIEW_PERMISSION_USAGE` alias). Health Connect requires it even for sideloaded apps; a simple in-app
  text screen satisfies it.
- **Background reads** require `READ_HEALTH_DATA_IN_BACKGROUND`, which is available on recent Health
  Connect versions; check `HealthConnectFeatures` at runtime. Without it, sync only happens when the app
  is in the foreground. That's acceptable, since values backfill the next time the app opens.
- **History limit:** by default an app can read only data from **30 days before the permission was first
  granted**. `READ_HEALTH_DATA_HISTORY` unlocks older data; we'll request it for A5 backfill.
- Check `HealthConnectClient.getSdkStatus()` and deep-link to install or update Health Connect on Android
  ≤ 13 if it's missing.

### 10.3 Usage Stats (screen time, app usage, unlocks)

- **Permission:** `PACKAGE_USAGE_STATS` is a special app-op permission, granted by the user in *Settings →
  Usage access*. We declare it in the manifest (`tools:ignore="ProtectedPermissions"`), check it with
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, …)`, and deep-link to
  `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Play policy restricts this permission, but that doesn't matter
  for a sideloaded personal app.
- **Accurate foreground time:** `queryUsageStats(INTERVAL_DAILY, …)` buckets are known to be imprecise
  around day boundaries. We instead use **`queryEvents`** and pair `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`
  (API 29+) events per package. We clip sessions at midnight (or the configured day boundary) and sum them
  per day. Totals may still differ slightly from Digital Wellbeing, whose exact method isn't public.
- **Unlocks / pickups:** count `KEYGUARD_HIDDEN` events (API 28+).
- **Total screen time:** either the sum across all launchable apps, excluding the launcher and this app,
  or `SCREEN_INTERACTIVE`/`SCREEN_NON_INTERACTIVE` intervals. Prototype both and pick whichever matches
  Digital Wellbeing more closely.
- **Retention problem:** the OS keeps raw *events* for only a limited time (on the order of days), then
  aggregates and eventually drops them. We therefore **persist per-day per-package totals** into
  `usage_daily` at every sync. That makes our history permanent and also lets a habit's tracked app list
  change later and be recomputed, as far back as we have data.
- **App picker:** list launchable apps with a `<queries>` element for `MAIN/LAUNCHER` intents. This
  avoids `QUERY_ALL_PACKAGES`. The user can select one or more apps (e.g. Instagram + TikTok + Reddit = a
  "Social" habit).
- Natural fit with **`AT_MOST`** goals: "≤ 30 min social/day", "≤ 3 h screen time/day", "≤ 60 unlocks/day".

### 10.4 Failure and edge cases

| Case | Behavior |
|---|---|
| Permission revoked | Habit shows a "Reconnect" chip; streak frozen (`NO_DATA`), not broken |
| Health Connect uninstalled or outdated | Same as above, with a deep link to install or update |
| Phone off for days | Backfill on next sync, within source retention |
| Time-zone change | Days computed in the zone where the data was recorded, when the source provides it; otherwise current zone (§12) |
| Duplicate sync runs | Idempotent thanks to `external_key` upserts |

## 11. Reminders, Notifications and Widgets

### 11.1 Reminders (P1)

- Each habit has 0..n reminders (time + weekdays). `ReminderScheduler` registers the **next** alarm per
  reminder with `AlarmManager`. When the alarm fires, the receiver checks whether the habit is already
  complete for the period. If it isn't, it posts a notification and schedules the next alarm.
- Exact alarms need `SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`. On Android 14+, `SCHEDULE_EXACT_ALARM` is
  denied by default and needs a settings deep link. For a sideloaded personal app, `USE_EXACT_ALARM` is
  simplest. Fallback: inexact `setAndAllowWhileIdle`, which can be several minutes late.
- Re-register alarms on `BOOT_COMPLETED`, `TIMEZONE_CHANGED`, `TIME_SET` and app update.
- **Notification actions:** binary habits get **"Done"**, which logs without opening the app. Quantitative
  habits get **"+{quick-add}"** and **"Log…"** (opens the entry sheet). Both get **"Snooze 1h"**.
- `POST_NOTIFICATIONS` is a runtime permission on Android 13+. Request it the first time a reminder is
  created, not on first launch.
- Optional: one **daily summary** notification in the evening ("3 of 5 done today") instead of
  per-habit pings. **[→ Q7]**

### 11.2 Widget (P1)

- A Glance widget in resizable sizes. It lists today's daily habits, plus weekly and monthly progress if
  there's room.
- Tapping a binary habit toggles it via `ActionCallback`. Tapping a quantitative habit adds quick-add.
  Tapping the title opens the app.
- The widget refreshes after any write in the app (repository → `GlanceAppWidget.updateAll`), after
  auto-sync, and at the day boundary.

## 12. Time, Dates and Time Zones

- **Logical day:** an entry belongs to the `LocalDate` the user sees, not to a UTC date. That date is
  fixed at log time and stored as `date`.
- **Day-boundary hour (setting, default 00:00):** night owls can set e.g. 03:00, so logging at 01:30 counts
  toward "yesterday". **[→ Q13]** This applies to Today's default date and to auto-tracking slicing.
- **Week start:** setting, defaulting to the locale's first day (Monday in most places). **[→ Q2]**
- **Month:** calendar month.
- **Traveling:** stored dates never move. Today's date is computed in the device's current zone at app
  start, at resume, and on `TIMEZONE_CHANGED`.
- `java.time.Clock` is injected everywhere, so tests can simulate any date, zone or DST transition.

## 13. Backup, Export and Privacy

- **Privacy:** no network permission is required at all, except optional future features. No analytics
  and no crash reporting service. Health and usage data never leave the device unless I export them.
- **Export:** *Settings → Export* writes a versioned JSON file containing all tables, via the Storage
  Access Framework (`ACTION_CREATE_DOCUMENT`). That lets me save it to Drive, Downloads, etc. through the
  system picker.
  ```json
  { "schemaVersion": 1, "exportedAt": "…", "units": [...], "habits": [...], "goals": [...],
    "entries": [...], "skips": [...], "reminders": [...] }
  ```
- **CSV export** of entries (date, habit, value, unit, note, origin), for spreadsheets.
- **Import:** replace-all, with a confirmation and an automatic pre-import backup. Merge import is P2.
- **Android Auto Backup** is enabled for the database and DataStore, giving a free Google-account backup to
  the new phone on device restore. **[→ Q11]**
- **Optional (P2): automatic scheduled export** to a user-chosen folder, e.g. weekly into a Drive-synced
  folder.

## 14. Testing Strategy

| Layer | What | Tools |
|---|---|---|
| Domain (highest value) | Period math (week starts, month lengths, leap years, DST), evaluator status table (§6.2) for every direction × mode × period, streak rules incl. skips / not-scheduled / goal versioning | JUnit on the JVM, parameterized tests; injected `Clock` |
| Data | DAOs, aggregation queries, upsert idempotency, **every migration** | Room in-memory DB, `MigrationTestHelper` (instrumented or Robolectric) |
| Auto-tracking | Usage-event → per-day duration algorithm (sessions crossing midnight, missing pause events), HC aggregation mapping | JVM tests with fake event streams; HC `testing` artifact fakes |
| ViewModels | State transitions for Today / Edit / Detail | Turbine, `kotlinx-coroutines-test` |
| UI | A few smoke tests: create habit → log → see completion; screenshot tests are optional | Compose UI test |
| Export/import | Round-trip: export → wipe → import → identical DB | JVM / Robolectric |

## 15. Build, CI and Distribution

- **Gradle Kotlin DSL + version catalog**; KSP for Room and Hilt.
- **GitHub Actions** on every push:
  - `./gradlew lint detekt test assembleDebug`;
  - upload the debug APK as a workflow artifact, so I can download and install it on my phone without a
    local Android Studio setup.
- **Release builds:** signed with a personal keystore stored as GitHub Actions secrets. On a tag, CI
  builds a signed release APK and attaches it to a GitHub Release. Releases keep the same signature, so
  installs upgrade in place.
- **Install:** sideload, by downloading the APK on the phone or using `adb install -r`. Optionally use
  **Obtainium** to auto-update from GitHub Releases. **[→ Q10]**
- `versionCode` comes from the CI run number or commit count; `versionName` comes from the tag.

## 16. Milestones

Each milestone ends with an installable APK I can use daily.

| # | Milestone | Contents | Rough size |
|---|---|---|---|
| **M0** | Skeleton | Project setup, version catalog, Hilt, Room, theme, navigation shell, CI producing an APK | S |
| **M1** | Core tracking (MVP) | Units, create/edit/archive habits (all periods, both directions, both counting modes, weekday schedules, goal versioning), Today screen with logging, entry editing, undo, domain evaluator + streaks with full tests, JSON export | L |
| **M2** | History & insights | Habit detail (stats, heatmap, chart, entry list, skips), history overview, CSV export, JSON import | M |
| **M3** | Reminders & widget | Per-habit reminders with notification actions, boot/time-zone handling, Glance widget | M |
| **M4** | Health Connect | Adapter, permissions & rationale screen, sync worker, steps first, then other metrics, backfill | M |
| **M5** | Usage Stats | Usage access flow, event → duration algorithm, `usage_daily` persistence, app picker, screen time / per-app / unlocks habits | M |
| **M6** | Polish | Reordering, categories, animations, auto-export, accessibility pass, edge-case hardening | S–M |

## 17. Open Questions

Each question lists the **default** I'll use if it isn't answered.

| # | Question | Default |
|---|---|---|
| Q1 | What phone and Android version are you on? This sets `minSdk` and decides whether Health Connect is built in (14+). | minSdk 28, target latest |
| Q2 | Which day does your week start on? | Locale default (likely Monday) |
| Q3 | Do daily habits need **specific weekdays** (gym Mon/Wed/Fri)? Or is "3× per week" (weekly + DAYS mode) enough? Do you need "every N days"? | Weekdays: yes (P0). Every N days: P2 |
| Q4 | For weekly/monthly habits, is supporting **both** "total quantity" (300 pages/month) and "number of days" (gym 3 days/week) right? Are there habits that fit neither? | Support both |
| Q5 | **"At most" habits:** with no log, should a manual limit habit count as success? Or do you want an explicit "log zero" / "confirm day" action? | No log = success for manual; `NO_DATA` for auto |
| Q6 | **Streak strictness:** should a partial day break the streak? Do you want "freeze" days / skips? Do you want a grace setting (e.g. "6 of 7 days keeps the weekly streak alive")? | Partial breaks the streak; manual skips allowed; no grace rule |
| Q7 | **Reminders:** per-habit reminders, one daily summary notification, both, or none? | Per-habit (P1), summary optional |
| Q8 | **Home-screen widget:** wanted? Which size or content matters most? | Yes, a today list with tap-to-log (P1) |
| Q9 | **Health data:** which app currently tracks it (Samsung Health, Fitbit, Google Fit, Garmin, Oura…)? Which metrics besides steps do you want (sleep, exercise, water, weight, mindfulness)? Should manual water logs be **written** to Health Connect? | Steps first, then sleep and exercise; read-only |
| Q10 | **Usage tracking:** total screen time, specific apps, app groups, unlocks, or all of them? Do you have Android Studio locally, or will you install APKs straight from GitHub Actions? | All four metrics; install from CI artifacts |
| Q11 | **Backup:** is manual JSON export plus Android Auto Backup enough? Or do you want scheduled auto-export to a Drive folder, or a real cloud sync? | Manual export + Auto Backup; scheduled export P2 |
| Q12 | **Organization:** do you need categories/tags, notes on entries, or a journal/mood field? | Notes: yes. Categories: P2. No journal |
| Q13 | **Day boundary:** do you ever log after midnight for "yesterday"? Should a late-night cutoff be configurable? | Configurable, default midnight |
| Q14 | **App name, package ID, look:** keep "Habits"? Any color/style preferences? Material You dynamic colors OK? | "Habits", `io.github.<user>.habits`, Material You |
| Q15 | **Non-numeric measurements:** any habits that are a *time of day* (e.g. "in bed by 23:00", "wake before 7:00") or a rating (1–5 mood)? These need a different value type. | Out of scope for v1; designed so a `TIME_OF_DAY` unit kind can be added |
| Q16 | **Roughly how many habits** do you expect to track at once (5? 20? 50?) | ~5–20; affects Today layout only |

## 18. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Usage Stats totals differ from Digital Wellbeing | Confusing numbers | Event-based algorithm, document the difference, compare during M5 and tune |
| OS discards usage events before we read them | Gaps in history | Frequent background sync; persist `usage_daily`; mark gaps as `NO_DATA` |
| OEM battery optimization (Samsung, Xiaomi…) kills background work and alarms | Missed syncs or reminders | Foreground sync on open; prompt to exempt the app from battery optimization; exact alarms |
| Health Connect API and permission changes | Integration breaks after an update | Isolated behind `DataSourceAdapter`; feature checks at runtime |
| Data loss from a bad migration or import | Losing years of history | Migration tests; pre-import auto-backup; Auto Backup; export reminders |
| Scope creep (it's a personal project and fun to extend) | Never "done" | Strict milestone order; MVP (M1) usable on its own |

---

## Appendix A: Example Habit Configurations

How typical habits map onto the model:

| Habit | Period | Unit | Goal | Source |
|---|---|---|---|---|
| Meditate | Daily | Done | ≥ 1 | Manual |
| Drink water | Daily | L (2 decimals, quick-add 0.25) | ≥ 2 | Manual (or HC hydration) |
| Read | Daily | pages | ≥ 20 | Manual |
| Gym | Weekly | Done | ≥ 3 **days** (threshold 1) | Manual |
| Run distance | Weekly | km | ≥ 20 **total** | Manual or HC distance |
| Deep work | Daily, Mon–Fri | minutes (duration) | ≥ 120 | Manual |
| Call family | Monthly | times | ≥ 4 **total** | Manual |
| Steps | Daily | steps | ≥ 10,000 | Health Connect |
| Sleep | Daily | hours (duration) | ≥ 7 | Health Connect |
| Social media | Daily | minutes | **≤ 30** | Usage Stats (Instagram, TikTok, Reddit) |
| Screen time | Daily | minutes | **≤ 180** | Usage Stats (total) |
| Phone pickups | Daily | times | **≤ 60** | Usage Stats (unlocks) |
| Alcohol-free days | Weekly | Done | ≥ 5 **days** | Manual |
| Coffee | Daily | cups | **≤ 2** | Manual |
