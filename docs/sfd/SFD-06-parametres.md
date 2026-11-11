# SFD-06 — Settings (standard day)

## 1. Goal
An ADMIN sets the length of the standard working day (RG-00-02), used to compute the duration of time entries for a
day and over a range of days (RG-01-08).

## 2. Data
### Application settings
| Field | Type | Required | Rule |
|---|---|---|---|
| standardDayMinutes | integer | yes | RG-06-02 |
| lastModifiedBy / lastModifiedAt | account, date and time | computed | RG-06-05 |

## 3. Business rules
- **RG-06-01**: at installation, the standard day lasts 480 minutes (8 h). An existing installation whose standard
  day was never changed by an ADMIN also moves to 480 minutes; a value chosen by an ADMIN is kept.
- **RG-06-02**: the standard day is between 60 and 1,440 minutes, in steps of 15 minutes.
- **RG-06-03**: only an ADMIN can change the standard day. Any signed-in user can read it, because it is used to show
  the computed duration while recording time (SFD-01, E2).
- **RG-06-04**: a change applies to time entries created or edited afterwards. Existing time entries keep the
  duration frozen when they were saved (RG-01-08): they are not recalculated.
- **RG-06-05**: the last change is recorded: who and when (RG-00-04).
- **RG-06-06**: saving a value identical to the value in force has no effect and is not recorded.

## 4. Screens
- **E1 Settings** (ADMIN): length of the standard day, entered in hours and minutes, with a help text saying that the
  time entries already saved are not recalculated. Save button. Mention of the last change (author, date).

## 5. Acceptance criteria
- **CA-06-01** — Given a fresh installation, When a signed-in user reads the settings, Then the standard day lasts
  480 minutes.
- **CA-06-02** — Given an ADMIN, When they save 7 h 30, Then the standard day lasts 450 minutes and the screen shows
  their name and the date as the last change.
- **CA-06-03** — Given a standard day of 450 minutes, When a USER records a DAY, Then the time entry lasts 450 minutes.
- **CA-06-04** — Given a DAY time entry saved with 480 minutes, When the ADMIN changes the standard day to 450 minutes,
  Then that time entry still lasts 480 minutes.
- **CA-06-05** — Given an ADMIN, When they enter 7 h 10, Then the field is in error and saving is blocked.
- **CA-06-06** — Given an ADMIN, When they send 30 minutes through the API, Then the API answers 400 with the field in
  error.
- **CA-06-07** — Given a USER or a MANAGER, When they call the settings update API, Then the response is 403; and the
  Settings menu is not shown.
- **CA-06-08** — Given a user who is not signed in, When they call the settings API, Then the response is 401.

## 6. Out of scope
- A different standard day per user, team or day of the week.
- Public holidays, company calendar, part-time work.
- Other application settings (time zone, time step, maximum length of a range).
- Recalculating existing time entries.
