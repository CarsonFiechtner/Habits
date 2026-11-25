# Open questions & judgment calls

Things I decided without asking so I could keep moving. None of them block anything. Each has the
choice I made; say the word and I'll change it.

## Start dates (v0.3.0)

- **Q-S1. Moving a start date later.** While a habit is still in setup (its first day hasn't ended), you
  can move the start date. If you logged something on the old first day and then push the start date
  later, those entries stay in the database but no longer count (they're before the start).
  *Alternative:* block moving the start date once something is logged.

## History & backups (v0.3.0)

- **Q-H1. Restore safety copies.** Restoring a backup first saves your current data to the app's private
  storage (newest 5 kept). There's no button to get back to that copy yet; it's a safety net I can
  recover with you if needed. *Alternative:* add "Undo last restore" in Settings.
- **Q-H2. Settings aren't in backups.** Theme and (later) the reminder time aren't part of the JSON
  export. *Alternative:* include them.
- **Q-H3. History trend.** The "daily habits completed" chart on the History tab only covers daily habits
  (weekly/monthly ones don't have a per-day result). Days where nothing was decided yet (e.g. today
  before anything is done) show no bar.
- **Q-H4. Archived habits' stats** are computed up to the day they were archived, so archiving doesn't
  turn the following days into misses.

## Nightly reminder (v0.4.0)

- **Q-R1. On by default.** The reminder is on at 21:00 and the app asks for notification permission
  once, on first launch after updating. Turn it off or change the time in Settings → Reminders.
- **Q-R2. Weekly/monthly "at most" habits** (e.g. "at most 3 drinks a week") are listed on the
  second-to-last day only if nothing at all has been logged that period, since an empty period counts as
  a miss. *Alternative:* never list them.
- **Q-R3. "Done" button** on the notification appears only when exactly one done/not-done habit is left
  (with several, it'd be unclear which one it marks). Tapping the notification opens Today.
- **Q-R4. Battery optimization.** The reminder uses an exact alarm, which fires on time even in Doze, so
  I haven't added a prompt to exempt KeepUp from battery optimization. If reminders ever arrive late,
  tell me and I'll add it.

## Health Connect steps (v0.5.0)

- **Q-A1. No manual adjustments on automatic habits.** Step habits only take values from Health Connect;
  the "+" and Log buttons are hidden. (The design doc listed manual adjustments as optional; I left them
  out to keep auto data trustworthy.) *Alternative:* allow a manual "+steps" entry on top.
- **Q-A2. Days without step data are misses,** consistent with the strict rule. If Health Connect has
  nothing for a day (phone off, Fit not syncing), that day counts as missed once it's past the 2-day
  settle window.
- **Q-A3. When syncing happens:** when you open the app, right before the 9pm reminder, from Settings →
  Sync now, and roughly hourly in the background. Background reads need Health Connect's "read in
  background" permission, which KeepUp requests when your Health Connect version supports it.
- **Q-A4. No backfill before the start date.** A new step habit starts counting from its start date,
  even though Health Connect may have older steps.

## Screen time & app groups (v0.6.0)

- **Q-U1. What counts as screen time.** The sum of time every app is in the foreground, excluding home
  screens (launchers) and System UI, measured from Android's usage events. It may differ a little from
  Digital Wellbeing, whose exact method isn't public. Compare the two for a few days and tell me if the
  gap is big.
- **Q-U2. No usage means zero.** Once Usage access is allowed, a day with no recorded usage is a real 0
  minutes, so "at most" screen-time habits complete on it, unlike manual "at most" habits, which need
  Log 0.
- **Q-U3. Minutes, not h:mm.** Screen-time habits use the Minutes unit (e.g. "at most 120 min").
  Showing them as "2h 0m" is part of the duration-format work still on the backlog.
- **Q-U4. App groups are shared.** One group can back several habits (e.g. daily and weekly limits on
  "Social"). Editing a group's apps changes what's counted from the next sync on; days already outside
  the 2-day window keep their values.
- **Q-U5. Backups now include app groups and saved per-app usage** (backup format version 2). Older
  backups still restore.
