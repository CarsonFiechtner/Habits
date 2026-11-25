# KeepUp — Android Habit Tracker: Design Document

| | |
|---|---|
| **Status** | Draft v0.3: second round of requirements answered |
| **Last updated** | 2026-11-25 |
| **Audience** | Me (sole user and developer), plus anyone or anything helping build it |

> Requirements settled so far are recorded in [§17](#17-decisions-and-open-questions). Anything still
> undecided is labeled **[Open → Qn]**.

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
17. [Decisions and Open Questions](#17-decisions-and-open-questions)
18. [Risks](#18-risks)
19. [Appendix: Example Habit Configurations](#appendix-a-example-habit-configurations)

---

## 1. Summary

KeepUp is a personal, offline-first Android app for tracking any number of habits. Each habit has:

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
- **G4.** Trustworthy data: **history is append-only and locked.** Once a day is over, its entries can't
  be added, edited or deleted, and goal changes only apply going forward. A streak can't be "repaired"
  after the fact. Nothing is ever silently lost; the export is lossless.
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
- Streak forgiveness: no skips, freezes, grace days or partial credit. If a period is missed, the streak
  ends.
- "Every N days" scheduling.
- Logging after midnight for the previous day. A day ends at midnight, full stop.
- Backfilling or editing past days (see §6.5).
- Scheduled auto-export. Auto Backup plus manual export is enough.

## 3. Core Concepts and Glossary

| Term | Meaning |
|---|---|
| **Habit** | Something I want to track, with a period, unit, goal and data source. |
| **Unit** | What's being measured. Either *binary* (done/not done) or *quantitative* (a number with a label such as "min", "L" or "pages"). Units are user-defined and reusable across habits. |
| **Period** | The window a goal applies to: `DAILY`, `WEEKLY` or `MONTHLY`. Weekly and monthly periods are **anchored to the habit's start date**, not to the calendar (§6.0). |
| **Entry** | One logged data point: a value (1 for binary) on a date, plus an optional note. A habit can have many entries per day; for example, "250 ml" logged several times. |
| **Goal** | Direction (`AT_LEAST` / `AT_MOST`) + target value + counting mode (see below). |
| **Counting mode** | For weekly/monthly habits, how the period's progress is computed: **TOTAL** (sum all entries in the period, e.g. "70,000 steps per week"). Daily habits always use the day's total. A second mode, **DAYS** (count days that met a daily threshold, e.g. "gym 3 days per week"), is deferred but the model leaves room for it. |
| **Data source** | `MANUAL`, `HEALTH_CONNECT(metric)` or `USAGE_STATS(metric, apps)`. |
| **Period status** | The result of evaluating a period: `COMPLETED`, `MISSED`, `IN_PROGRESS`, `NOT_SCHEDULED`. |
| **Streak** | Number of consecutive completed periods, counted in the habit's own period: days, weeks or months. |

## 4. Functional Requirements

Priority: **P0** = MVP, **P1** = soon after MVP, **P2** = stretch or nice-to-have.

### 4.1 Habit management

| ID | Requirement | Pri |
|---|---|---|
| H1 | Create, edit, archive and delete habits. Archived habits are hidden from Today but keep their history. What can be edited after creation is limited (§6.5). Deleting removes all history, after a typed confirmation. | P0 |
| H2 | Habit fields: name, optional description, icon (emoji or Material icon), color, period, unit, goal, data source. | P0 |
| H3 | Daily habits can apply to specific weekdays only (e.g. Mon/Wed/Fri). Not needed yet; the schema keeps a `schedule_days` column so it can be added without a migration. | P2 |
| H4 | Changing a goal or target takes effect at the **start of the next period**. The current and past periods keep their old goal (§6.5). | P0 |
| H5 | Reorder habits on the Today screen (drag and drop). | P1 |
| H6 | Group habits into categories or tags (e.g. Health, Mind, Digital). Backlog. | P2 |
| H7 | Weekly/monthly **DAYS** counting mode ("gym on 3 days per week"). | P2 |
| H8 | Non-numeric value types: **time of day** ("in bed by 23:00") and **rating** (mood 1–5). The `UnitKind` enum is designed so these can be added later. | P2 |

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
| L3 | Multiple entries per day are summed. **Today's** entries can be viewed, edited and deleted. Past entries are read-only. | P0 |
| L4 | ~~Backfill past dates~~. Dropped: you can only log for today (§6.5). | — |
| L5 | Optional note per entry. | P1 |
| L7 | Undo snackbar after any log or delete (same day only). | P0 |
| L8 | Manual **"at most"** habits (e.g. ≤ 2 coffees) have a **"Log 0"** action. A day with nothing logged is a miss, so you have to say "zero today" explicitly. The nightly reminder offers it too. | P0 |

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
| A1 | A habit can use Health Connect **steps** as its data source, with a daily or weekly target. Other metrics (see §10.2) are future work. | P2 |
| A2 | A habit can use Usage Stats as its data source: **total screen time**, or time in a **named app group** of one or more apps (e.g. "Social" = Instagram + Reddit). Unlock count is future work. | P2 |
| A3 | Auto-tracked values refresh when the app opens and in the background. Each one shows its source and last-sync time. | P2 |
| A4 | Auto-tracked habits are read-only by default, with an optional manual adjustment entry on top. | P2 |
| A5 | Only data from the habit's creation date onward counts. Earlier source data may be shown greyed out in charts for context, but it never affects streaks or stats. | P2 |

### 4.6 Other

| ID | Requirement | Pri |
|---|---|---|
| O1 | **Nightly reminder** at 21:00 local time (time configurable): one notification listing every goal not yet met today. No notification if everything is done. | P1 |
| O2 | Home-screen widget showing today's habits, with tap-to-log. Post-MVP. | P2 |
| O3 | Export and import all data as JSON. Export entries as CSV. | P0 for export, P1 for import |
| O4 | Settings: reminder time, theme (system/light/dark). | P0 |
| O5 | **KeepUp white-and-green theme** (fixed brand colors, no wallpaper-based dynamic color), with a matching dark variant (§7.3). | P0 |
| O6 | Layout handles **up to ~25 active habits**, optimized so **10** fit on one Pixel 7 screen without scrolling. | P0 |

## 5. Domain Model

The domain layer is pure Kotlin with no Android dependencies, so it can be unit-tested exhaustively.

```kotlin
enum class Period { DAILY, WEEKLY, MONTHLY }
enum class Direction { AT_LEAST, AT_MOST }
enum class CountingMode { TOTAL /*, DAYS (future, H7) */ }   // only meaningful for WEEKLY / MONTHLY

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
    val target: Double,              // the period's quantity
    val countingMode: CountingMode,
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
    val anchorDate: LocalDate,           // first day of the first period; = creation date (§6.0)
    val unit: Unit,
    val goals: List<Goal>,               // sorted by effectiveFrom; the one in force for a date wins
    val schedule: Schedule,              // always All for now; weekday schedules are future (H3)
    val source: DataSource,
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
```

### Why goals are versioned

If I raise "water" from 2 L to 3 L, last month's days shouldn't turn from green to red. Each goal row has an
`effectiveFrom` date. To evaluate a period, we use the goal in force on the period's **start date**.
A goal edit always gets `effectiveFrom` = the start of the **next** period. It can't be applied to the current
period or to history (§6.5).

### Binary habits

A binary habit is just a quantitative habit with value `1` per completion and an `AT_LEAST 1` daily goal.
That lets one engine handle everything. Toggling on inserts an entry with value `1`; toggling off deletes
that day's entries.

## 6. Progress Evaluation, Status and Streaks

This is the heart of the app. It lives in pure functions that are easy to test.

### 6.0 Period boundaries

Periods are anchored to the habit's `anchorDate`, which is the day it was created:

- **Daily:** each calendar day, midnight to midnight.
- **Weekly:** 7-day blocks starting on the anchor's weekday. A habit created on a Wednesday runs
  **Wednesday → Tuesday**, every week. Period *k* = `[anchor + 7k, anchor + 7k + 6]`.
- **Monthly:** one-month blocks starting on the anchor's day of the month. A habit created on the 15th
  runs **15th → 14th**. Period *k* starts at `anchor.plusMonths(k)`, always computed from the anchor, not
  chained. Anchors on the 29th–31st clamp to the last day in short months (Jan 31 → Feb 28 → Mar 31).
  **[Open → Q1]**
- The first period starts on the creation day itself, so it's always a full period.
- Each habit has its own boundaries, so on any given day different weekly habits can be at different
  points in their week. The Today screen shows "3 days left" per weekly/monthly habit, not a shared
  "This week" header.

```kotlin
fun evaluate(habit: Habit, period: PeriodRange, entries: List<Entry>, today: LocalDate): PeriodResult

data class PeriodResult(
    val range: PeriodRange,       // start..end dates
    val value: Double,            // sum of the period's entries
    val hasData: Boolean,         // at least one entry exists in the period (needed for AT_MOST)
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
| WEEKLY/MONTHLY | DAYS *(future)* | Number of days in the period whose daily sum meets a daily threshold |

### 6.2 Status rules

Strict rules: a period either met its goal or it didn't, and **a period with no data is a miss**.

| Situation | `AT_LEAST` | `AT_MOST` |
|---|---|---|
| Period is in the future, or before the habit was created | `NOT_SCHEDULED` | `NOT_SCHEDULED` |
| Current period, goal met | `COMPLETED` (locks in early) | `IN_PROGRESS` ("on track"). It can't be confirmed until the period ends |
| Current period, goal not yet met | `IN_PROGRESS` | `MISSED` as soon as the limit is exceeded; otherwise `IN_PROGRESS` |
| Past period, goal met | `COMPLETED` | `COMPLETED`, **but only if the period has at least one entry** |
| Past period, goal not met (including a partial amount) | `MISSED` | `MISSED` |
| Past period, no entries at all | `MISSED` | `MISSED` |

> **"At most" habits and missing data.** For a *manual* limit habit such as "≤ 2 coffees", an empty day
> could mean zero coffees or a forgotten log, so it counts as a miss. To claim a zero day, use
> **Log 0** (L8). For *auto-tracked* habits, the sync writes an entry for every day it has source data for,
> including real zeros (e.g. 0 min of social media). A day is empty only if the source had nothing,
> for example because permission was revoked. That day is then a miss, consistent with the rule above.
> It becomes a success automatically if a later sync fills it in.

### 6.3 Streaks

- **Current streak** = the number of consecutive `COMPLETED` periods, walking backward from the most recent
  *closed* period, plus 1 if the current period is already `COMPLETED`. An `IN_PROGRESS` current period
  doesn't break the streak (you still have until midnight, or the end of the week or month).
- `MISSED` ends the streak. There are no skips, freezes, grace days or partial credit.
- `NOT_SCHEDULED` only applies before a habit's creation date, so in practice a streak can't span one.
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

### 6.5 History lock (integrity rules)

Past data is immutable so streaks and stats can be trusted.

| Action | Today | Past days |
|---|---|---|
| Add an entry (incl. Log 0) | ✅ | ❌ No backfilling. New entries are always dated today |
| Edit or delete an entry | ✅ (with undo) | ❌ Read-only |
| Change goal / target | Takes effect at the **start of the next period** | ❌ Never retroactive |
| Change name, icon, color, description | ✅ Any time (doesn't affect results) | n/a |
| Change period, unit or data source | ✅ Only on the habit's **creation day**, to fix setup mistakes. Afterwards, archive the habit and create a new one | n/a |
| Archive / unarchive | ✅ History is kept | n/a |
| Delete habit | ✅ After typing the habit's name to confirm. All of its history is removed | n/a |
| Auto-tracked values | Updated by sync | Updated by sync only during the 2-day settle window (§10.1), then frozen |

Enforcement lives in the **domain use cases** (`LogEntry`, `EditEntry`, `UpdateGoal`, …), which check
`entry.date == today(clock)`. The UI only mirrors these rules. The database isn't made tamper-proof:
this is a personal app, and the aim is to prevent accidental or impulsive edits, not deliberate tampering
with the database file. JSON import is a whole-database restore (§13), not an editing path.

## 7. User Experience

Built with Material 3 and Jetpack Compose. Navigation uses a bottom bar with three destinations:
**Today**, **History** and **Settings**.

### 7.1 Screens

1. **Today** (home)
   - Date header showing today. ◀ moves back to view past days **read-only**; logging is only possible
     on today.
   - Habits are grouped into sections: **Daily**, then **Weekly & monthly**. Each period row shows "3 days
     left", because each habit has its own boundaries (§6.0). Each row is a single compact line of about
     64 dp, so 10 habits fit on screen (O6). A row shows the icon, name, a progress ring or bar,
     "1.2 / 2 L" text, and the streak 🔥.
     - Binary: a tap on the row's check toggles it.
     - Quantitative: a "+" chip adds the quick-add step; a long-press or tap on the value opens the numeric
       entry sheet.
     - Manual "at most": "+" chip plus a **Log 0** action while the day has no entries.
     - Auto-tracked: shows a source badge (♥ Health / 📱 Usage) and "synced 5 min ago". The row has no "+"
       chip.
   - Completed habits can optionally sink to the bottom or collapse into a "Done (n)" row. Both sections
     are collapsible. At 25 habits the screen scrolls, with unfinished habits first.
   - FAB: **New habit**.
2. **Add / Edit Habit**: a single scrollable form with progressive disclosure:
   1. Name, icon, color
   2. **What are you tracking?** → Done/not done · A quantity (pick or create a unit) · Automatic (Health /
      Screen time). *Automatic is hidden until M4/M5.*
   3. **How often?** → Daily · Weekly · Monthly
   4. **Goal** → At least / At most · target
   5. On edit, only name, icon, color and the goal can change. The goal change is labeled "takes effect
      on <next period start>". Period, unit and source are fixed after the creation day (§6.5).
   6. A live preview sentence: *"At least **2 L** of water **every day**"* / *"At least **70,000
      steps** each **week**"*.
3. **Habit Detail**
   - Header stats: current streak, best streak, completion % (selectable range).
   - Calendar heatmap (month pages for daily habits; a week or month grid for others).
   - Chart of values with the goal line overlaid (week / month / year ranges).
   - Entry list: past entries read-only (lock icon); today's entries editable.
   - Goal history ("since Mar 3: 3 L; before: 2 L").
4. **History / Overview**: across all habits, a week-by-week grid (rows = habits, columns = days),
   plus the overall completion trend.
5. **Units**: list and edit custom units. Reached from Settings and from the unit picker.
6. **Settings**: nightly reminder (on/off, time), theme (system/light/dark), export/import,
   permissions and integrations status (Health Connect, Usage access), archived habits, about/version.

### 7.2 Interaction principles

- Every destructive action has an **Undo** snackbar or a confirmation.
- Haptic feedback on completion; a subtle animation when a goal flips to completed.
- Every log takes one tap from Today. Detail screens are for reviewing.
- Accessibility: content descriptions on progress indicators, minimum 48dp touch targets, and dynamic type
  scaling.

### 7.3 Visual identity

- **Name:** KeepUp. **Package ID:** `io.github.carsonfiechtner.keepup`.
- **Palette:** white surfaces with green as the brand color. These are fixed brand colors; wallpaper-based
  dynamic color is off. Starting point:
  - primary green `#2E7D32` (buttons, progress, completed state);
  - lighter green `#A5D6A7` / container `#E8F5E9` (progress tracks, chips, completed rows);
  - white `#FFFFFF` surfaces, off-white `#F7FAF7` background;
  - neutral greys for text;
  - a single muted red/orange for missed status only.

  The final tones come from the Material 3 theme builder seeded with the primary green, so contrast is
  correct.
- **Dark variant:** follows the system setting by default. Near-black surfaces with the same greens
  lightened for contrast. Light-only can be forced in Settings. **[Open → Q2]**
- **App icon:** a simple green checkmark/upward-arrow mark on white. Adaptive icon with a monochrome
  layer for themed icons.

## 8. Technical Architecture

### 8.1 Stack

| Concern | Choice | Notes |
|---|---|---|
| Language | **Kotlin** (latest stable, K2) | |
| UI | **Jetpack Compose + Material 3** | Custom KeepUp color scheme (§7.3), edge-to-edge. |
| Architecture | **MVVM + unidirectional data flow** | `ViewModel` exposes `StateFlow<UiState>`. Screens send events. |
| Async | Kotlin **Coroutines + Flow** | Room DAOs return `Flow`, so the UI updates reactively. |
| Persistence | **Room** (SQLite) | Exported schemas plus migration tests. |
| Preferences | **DataStore (Preferences)** | Settings: reminder time, theme, last-sync times. |
| DI | **Hilt** | Manual DI is viable at this size, but Hilt integrates cleanly with WorkManager and ViewModels. |
| Navigation | **Navigation Compose** with type-safe routes | |
| Background work | **WorkManager** | Auto-tracking sync; reminder rescheduling. |
| Reminders | **AlarmManager** (`setExactAndAllowWhileIdle` with `USE_EXACT_ALARM`) + notifications | WorkManager isn't precise enough for "remind me at 21:00". |
| Widgets | **Jetpack Glance** | Compose-style API for app widgets. Post-MVP. |
| Charts | **Vico** (Compose charts library), or hand-drawn Canvas for the heatmap | The heatmap is simple enough to draw directly. |
| Health | **Health Connect** client (`androidx.health.connect:connect-client`) | Stretch. |
| Usage | **`UsageStatsManager`** (platform API) | Stretch. |
| Serialization | **kotlinx.serialization** | Export/import JSON and type-safe nav routes. |
| Date/time | **`java.time`** (native on API 26+) | |
| Testing | JUnit5/JUnit4, **Turbine** (Flow), **Truth**, Room in-memory DB, Compose UI test, Robolectric where useful | |
| Build | Gradle Kotlin DSL, **version catalog** (`libs.versions.toml`), KSP | |
| Static analysis | **ktlint** (via Spotless) + **detekt**, Android Lint | Kept light; it's a personal project. |

**SDK levels:** the target device is a **Pixel 7 on Android 17**. Since this is a single-user app, we can
set `minSdk 34` (Android 14) and `targetSdk`/`compileSdk` to the latest stable. Android 14 is the first
version with Health Connect built into the OS. Setting it as the floor removes all "install Health Connect"
handling and the older permission and notification code paths. The cost is that the app won't run on
anything older than Android 14, which doesn't matter here.

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
  └─ io.github.carsonfiechtner.keepup
       ├─ ui/            today/, edit/, detail/, history/, settings/, units/, components/, theme/
       ├─ data/          db/ (entities, DAOs, migrations), repo/, prefs/, export/
       ├─ autotrack/     health/, usage/, sync/
       ├─ reminders/     NightlyReminderScheduler, receivers
       ├─ widget/        Glance widget + actions (post-MVP)
       └─ di/
:core:domain             models, evaluator, streaks, period math, use-case interfaces
```

Package / application ID: `io.github.carsonfiechtner.keepup`.

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
  period        TEXT NOT NULL,          -- DAILY | WEEKLY | MONTHLY (immutable after creation day)
  anchor_date   INTEGER NOT NULL,       -- epoch day; start of the first period (§6.0)
  unit_id       INTEGER NOT NULL REFERENCES unit(id),
  schedule_days INTEGER NOT NULL DEFAULT 127,  -- bitmask Mon..Sun; reserved for H3, always 127 for now
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
  counting_mode   TEXT NOT NULL,        -- TOTAL (DAYS reserved for H7)
  daily_threshold REAL,                 -- reserved for DAYS mode
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

app_group (                            -- stretch: named groups of apps for usage habits
  id            INTEGER PK,
  name          TEXT NOT NULL           -- "Social"
)

app_group_member (
  group_id      INTEGER NOT NULL REFERENCES app_group(id) ON DELETE CASCADE,
  package       TEXT NOT NULL,          -- "com.instagram.android"
  PRIMARY KEY (group_id, package)
)

-- Stretch: raw usage data captured before the OS discards it (see §10.3)
usage_daily (
  date          INTEGER NOT NULL,       -- epoch day
  package       TEXT NOT NULL,          -- "*" = total screen time; "#unlocks" reserved for unlock count
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
3. It upserts one entry per day per habit, keyed `"<source>:<metric>:<date>"`. Source data can arrive
   late (e.g. Fit syncing steps from a watch hours later), so a day stays **open to the sync** for a
   settle window of **2 days** after it ends, then freezes like any other past day. **[Open → Q3]**
   This is the only way a past day can change, and only the data source can change it, not the user.
4. It records the last-sync time in DataStore, and the UI shows it.

Manual adjustments on auto habits (A4) are separate `MANUAL` entries, so they survive re-syncs.

### 10.2 Health Connect

Health Connect is Android's on-device health data hub. Google Fit, Fitbit, Samsung Health, Oura and others
write to it, and we read from it. **Scope for now: steps only.** The adapter is generic, so other
metrics can be added later.

**Data path for this setup:** Google Fit (current step source) → Health Connect → KeepUp. Before M4,
check that Fit is syncing into Health Connect: *Settings → Health Connect → Data and access → Activity →
Steps* should list Fit as a source with recent data. Google has been moving Fit users toward Fitbit and
Health Connect. Because we read only from Health Connect, a future switch from Fit to another app needs
no code changes.

| Metric (steps now, rest future) | Record type | Aggregation | Example habit |
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
  granted**. `READ_HEALTH_DATA_HISTORY` unlocks older data; we'd only need it for the optional pre-creation chart context (A5), so we can skip it.
- With `minSdk 34`, Health Connect is always present as part of the OS. We still check
  `HealthConnectClient.getSdkStatus()` defensively.

### 10.3 Usage Stats (screen time, app usage, unlocks)

- **Permission:** `PACKAGE_USAGE_STATS` is a special app-op permission, granted by the user in *Settings →
  Usage access*. We declare it in the manifest (`tools:ignore="ProtectedPermissions"`), check it with
  `AppOpsManager.unsafeCheckOpNoThrow(OPSTR_GET_USAGE_STATS, …)`, and deep-link to
  `Settings.ACTION_USAGE_ACCESS_SETTINGS`. Play policy restricts this permission, but that doesn't matter
  for a sideloaded personal app.
- **Accurate foreground time:** `queryUsageStats(INTERVAL_DAILY, …)` buckets are known to be imprecise
  around day boundaries. We instead use **`queryEvents`** and pair `ACTIVITY_RESUMED` / `ACTIVITY_PAUSED`
  (API 29+) events per package. We clip sessions at midnight and sum them
  per day. Totals may still differ slightly from Digital Wellbeing, whose exact method isn't public.
- **Unlocks / pickups (future):** count `KEYGUARD_HIDDEN` events.
- **Total screen time:** either the sum across all launchable apps, excluding the launcher and this app,
  or `SCREEN_INTERACTIVE`/`SCREEN_NON_INTERACTIVE` intervals. Prototype both and pick whichever matches
  Digital Wellbeing more closely.
- **Retention problem:** the OS keeps raw *events* for only a limited time (on the order of days), then
  aggregates and eventually drops them. We therefore **persist per-day per-package totals** into
  `usage_daily` at every sync. That makes our history permanent and also lets a habit's tracked app list
  change later and be recomputed, as far back as we have data.
- **App groups:** a named group of one or more apps, e.g. "Social" = Instagram + TikTok + Reddit.
  Groups are managed in their own screen and reused across habits. Editing a group recomputes history
  from `usage_daily`.
- **App picker:** list launchable apps with a `<queries>` element for `MAIN/LAUNCHER` intents. This
  avoids `QUERY_ALL_PACKAGES`.
- Natural fit with **`AT_MOST`** goals: "≤ 30 min social/day", "≤ 3 h screen time/day".

### 10.4 Failure and edge cases

| Case | Behavior |
|---|---|
| Permission revoked | Habit shows a "Reconnect" chip. Days with no data count as missed (§6.2), but they flip to completed if the permission is restored and a sync fills them in within the 2-day settle window |
| Phone off for days | Filled in on the next sync if within the 2-day settle window; otherwise those days are frozen as missed |
| Time-zone change | Days computed in the zone where the data was recorded, when the source provides it; otherwise the current zone (§12) |
| Duplicate sync runs | Idempotent thanks to `external_key` upserts |

## 11. Reminders, Notifications and Widgets

### 11.1 Nightly reminder (P1)

- **One** notification per night at **21:00 local time** (configurable in Settings), only if something is
  unfinished. Example: *"3 goals left today: Water 1.2/2 L · Read · Steps 6,400/10,000"*.
- **Which goals it lists:**
  - every daily `AT_LEAST` habit not yet completed, auto-tracked steps included (after a fresh sync);
  - every manual `AT_MOST` habit with **no entries today**, so you don't forget to log 0;
  - weekly/monthly habits on the **second-to-last day of their period**, if not yet met. This leaves a
    full day to catch up. Example: *"Weekly steps 52,300/70,000. Tomorrow is the last day"*.
  - Auto-tracked `AT_MOST` habits (screen time) are never listed, because there's nothing to "do".
- **Mechanics:** `NightlyReminderScheduler` sets one alarm for the next 21:00 with
  `setExactAndAllowWhileIdle`. For a sideloaded app, `USE_EXACT_ALARM` is granted at install time. When
  the alarm fires, a receiver starts a short job: it syncs auto-tracked habits, evaluates today, posts the
  notification if needed, and schedules tomorrow's alarm. The alarm is re-registered on `BOOT_COMPLETED`,
  `TIMEZONE_CHANGED`, `TIME_SET` and app update.
- **Long lists:** the notification uses `InboxStyle`, showing up to ~6 lines plus "+N more".
- **Actions:** tapping opens Today. If exactly one binary habit is left, a **"Done"** action logs it from
  the notification.
- `POST_NOTIFICATIONS` is requested during first-run setup, with an explanation of the nightly reminder.

### 11.2 Widget (P2, post-MVP)

- A Glance widget in resizable sizes. It lists today's daily habits, plus weekly and monthly progress if
  there's room.
- Tapping a binary habit toggles it via `ActionCallback`. Tapping a quantitative habit adds quick-add.
  Tapping the title opens the app.
- The widget refreshes after any write in the app (repository → `GlanceAppWidget.updateAll`), after
  auto-sync, and at midnight.

## 12. Time, Dates and Time Zones

- **Logical day:** an entry belongs to the `LocalDate` the user sees, not to a UTC date. That date is
  fixed at log time and stored as `date`.
- **Days end at midnight local time.** There's no late-night cutoff and no backfilling. New entries are
  always dated today.
- **Weeks and months:** anchored per habit (§6.0). There's no global week-start setting.
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
    "entries": [...], "appGroups": [...], "settings": {...} }
  ```
- **CSV export** of entries (date, habit, value, unit, note, origin), for spreadsheets.
- **Import:** replace-all, with a confirmation and an automatic pre-import backup. This is meant for
  restoring a backup onto a new or reset phone, not for editing history. Merge import is dropped.
- **Android Auto Backup** is enabled for the database and DataStore. **Decided:** this plus manual export is
  the backup strategy.
  - **How it works:** about once a day, when the phone is idle, charging and on Wi-Fi, Android copies the
    app's private data (up to 25 MB, far more than we'll use) to the Google account's backup storage. On
    Android 9+ with a screen lock set, the copy is end-to-end encrypted with your lock-screen credential.
    It doesn't count against Drive storage, and you can't browse it.
  - **Restore:** it happens automatically when the app is installed on a phone set up from that backup,
    such as a new phone, a factory reset, or uninstall then reinstall. You get only the latest snapshot.
    There's no history and no "restore last Tuesday's version".
  - **Caveat for sideloaded apps:** restore is reliable for Play Store installs during phone setup. For a
    sideloaded APK it should also apply when the app is reinstalled, but it's less tested. We'll verify it
    during development with `adb shell bmgr backupnow` and `bmgr restore`, and treat it as a convenience
    rather than the primary safety net.
- A Settings line shows "Last manual export: N days ago" as a gentle nudge.

## 14. Testing Strategy

| Layer | What | Tools |
|---|---|---|
| Domain (highest value) | Period math (week starts, month lengths, leap years, DST), evaluator status table (§6.2) for every direction × mode × period, anchored weekly/monthly boundaries (incl. 29th–31st anchors), strict streak rules (missing data, at-most confirmation, goal versioning), history-lock rules (§6.5) | JUnit on the JVM, parameterized tests; injected `Clock` |
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
- **Release builds:** signed with a personal release keystore. Its file and passwords are stored as GitHub
  Actions secrets. Pushing a tag `vX.Y.Z` makes CI build a signed release APK
  (`keepup-vX.Y.Z.apk`) and publish it as a GitHub Release. Every release uses the same signature, so
  installs upgrade in place and keep data. **[Open → Q4]**
- **Install and update via [Obtainium](https://github.com/ImranR98/Obtainium):** add the repo's URL in
  Obtainium once. It checks GitHub Releases, notifies you of new versions and installs them. If the repo is
  private, Obtainium needs a GitHub fine-grained personal access token with read-only access to this repo.
- **Release cadence:** a release at the end of each milestone, plus ad-hoc fix releases. Debug APKs from
  every push stay available as CI artifacts for testing, but they're signed differently and can't
  upgrade over a release install.
- `versionCode` comes from the CI run number or commit count; `versionName` comes from the tag.

## 16. Milestones

Each milestone ends with an installable APK I can use daily.

| # | Milestone | Contents | Rough size |
|---|---|---|---|
| **M0** | Skeleton | Project setup, version catalog, Hilt, Room, KeepUp theme + icon, navigation shell, CI producing an APK, signed tag releases installable via Obtainium | S |
| **M1** | Core tracking (MVP) | Units, create/edit/archive habits (all periods, anchored periods, both directions, next-period goal versioning), Today screen with logging + "Log 0", same-day entry editing + undo, history lock, domain evaluator + streaks with full tests, JSON export | L |
| **M2** | History & insights | Habit detail (stats, heatmap, chart, entry list), history overview, CSV export, JSON import | M |
| **M3** | Nightly reminder | 21:00 summary notification, exact alarm, boot/time-zone handling, "Done" action | S |
| **M4** | Health Connect | Adapter, permissions & rationale screen, sync worker, steps (daily and weekly goals), settle-window sync | M |
| **M5** | Usage Stats | Usage access flow, event → duration algorithm, `usage_daily` persistence, app picker, app groups, total screen time and group habits | M |
| **M6** | Polish & extras | Widget, reordering, animations, accessibility pass, edge-case hardening | M |
| Later | Ideas backlog | Time-of-day and rating values, weekday schedules, DAYS counting mode, unlock counts, more Health Connect metrics, categories | — |

## 17. Decisions and Open Questions

### 17.1 Decided

| Topic | Decision |
|---|---|
| Name & look | **KeepUp**, `io.github.carsonfiechtner.keepup`, white-and-green brand theme (§7.3). |
| Device | Pixel 7, Android 17. `minSdk 34`, target latest. |
| Health data | Google Fit → Health Connect. **Steps only** for now (daily or weekly targets). |
| Phone usage | **Total screen time** and **named app groups** (1+ apps). Unlocks later. |
| Scheduling | Daily / weekly / monthly with **TOTAL** counting. No weekday schedules, no "every N days", no DAYS mode for now. |
| Period boundaries | Weekly (and monthly) periods are **anchored to the habit's creation date**, e.g. Wed → Tue (§6.0). |
| Streaks | Strict: a missed or partial period ends the streak. No skips or freezes. |
| Missing data | A day with nothing logged is a **miss**, including for "at most" habits, which get a **Log 0** action. |
| History | **Locked.** No backfill and no editing of past days. Goal changes apply from the next period (§6.5). |
| Reminders | One **nightly reminder at 21:00** for unfinished daily goals, plus weekly/monthly goals on the **second-to-last day** of their period. Widget is post-MVP. |
| Value types | Time of day and rating are out of scope for v1 but on the backlog. |
| Day boundary | Midnight, not configurable. |
| Backup | Android Auto Backup + manual JSON/CSV export. No scheduled export. |
| Organization | Notes on entries: yes (P1). Categories: backlog. |
| Distribution | Signed GitHub Releases, installed and updated with Obtainium. |
| Scale | Up to ~25 active habits; optimized for 10. |

### 17.2 Still open

Each question lists the **default** I'll use if it isn't answered.

| # | Question | Default |
|---|---|---|
| Q1 | **Monthly habits:** should they also anchor to the creation day (created on the 15th → 15th–14th)? Or use calendar months? | Anchor to the creation day, matching weekly |
| Q2 | **Dark mode:** should the app follow the system's dark mode with a dark-green variant, or always stay white and green? | Follow system, with a light-only override in Settings |
| Q3 | **Auto-tracked settle window:** how many days can late-arriving step and usage data still update a past day before it freezes? | 2 days |
| Q4 | **Signing key and repo visibility:** is the GitHub repo private? It needs to be public, or Obtainium needs a read-only token. Do you want to generate the release keystore yourself, or should I generate it here and walk you through adding it as GitHub secrets? | I'll generate it and give step-by-step instructions |
| Q5 | **Goal edits:** is "takes effect at the start of the next period", with free edits on the creation day only, the right integrity rule? | Yes |

## 18. Risks

| Risk | Impact | Mitigation |
|---|---|---|
| Losing the release signing key | Can't upgrade in place; must uninstall (export first) and reinstall | Keep a copy of the keystore and passwords in a password manager, outside GitHub |
| Usage Stats totals differ from Digital Wellbeing | Confusing numbers | Event-based algorithm, document the difference, compare during M5 and tune |
| OS discards usage events before we read them | Gaps in history (counted as misses) | Frequent background sync; persist `usage_daily`; sync again at the nightly reminder |
| Background work or alarms get deferred by battery optimization (less aggressive on Pixel than other brands) | Missed syncs or reminders | Foreground sync on open; exact alarm for the reminder; optional battery-optimization exemption |
| Health Connect API and permission changes | Integration breaks after an update | Isolated behind `DataSourceAdapter`; feature checks at runtime |
| Google Fit stops writing to Health Connect (Fit being wound down) | Steps stop syncing | We read only Health Connect, so switching the source app (Fitbit, or phone-native counting) needs no code change |
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
| Gym *(needs DAYS mode, backlog)* | Weekly | Done | ≥ 3 **days** | Manual |
| Run distance | Weekly | km | ≥ 20 **total** | Manual or HC distance |
| Deep work | Daily, Mon–Fri | minutes (duration) | ≥ 120 | Manual |
| Call family | Monthly | times | ≥ 4 **total** | Manual |
| Steps | Daily | steps | ≥ 10,000 | Health Connect |
| Weekly steps | Weekly (e.g. Wed → Tue) | steps | ≥ 70,000 **total** | Health Connect |
| Social media | Daily | minutes | **≤ 30** | Usage Stats (app group "Social") |
| Screen time | Daily | minutes | **≤ 180** | Usage Stats (total) |
| Coffee | Daily | cups | **≤ 2** (needs a log, "Log 0" on coffee-free days) | Manual |
| *Sleep (backlog)* | Daily | hours | ≥ 7 | Health Connect |
| *Bedtime (backlog)* | Daily | time of day | ≤ 23:00 | Manual |
