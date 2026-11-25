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
