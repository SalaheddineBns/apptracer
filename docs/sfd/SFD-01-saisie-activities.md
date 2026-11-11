# SFD-01 — Recording activities

## 1. Goal
A USER records, edits, deletes and consults their activities over one hour, one day or a range of days.

## 2. Data
| Field | Type | Required | Rule |
|---|---|---|---|
| granularity | enum HOUR / DAY / RANGE | yes | |
| startDate | date | yes | RG-01-07 |
| endDate | date | for RANGE | RG-01-03 |
| startTime / endTime | time | for HOUR | RG-01-01 |
| activityType | reference | yes | RG-01-04 |
| project | reference | depending on the type | RG-01-05 |
| comment | text | no | ≤ 500 characters (RG-01-09) |
| durationMinutes | integer | computed | RG-01-08 |

## 3. Business rules
- **RG-01-01**: for the HOUR granularity, startTime and endTime are on the same day, endTime > startTime, in steps
  of 15 minutes. A slot therefore cannot end at midnight: the latest end time is 23:45.
- **RG-01-02**: for the DAY granularity, the time entry covers the whole day of startDate.
- **RG-01-03**: for the RANGE granularity, endDate > startDate and the range lasts at most 31 calendar days, bounds
  included.
- **RG-01-04**: the activity type must exist and be active.
- **RG-01-05**: for the TRAVAIL_PROJET and REVUE_MR types, the project is required; for the others, it is forbidden.
  The rule relies on the type's `projectRequired` flag (RG-02-03); the chosen project must be active (RG-02-06).
- **RG-01-06**: a time entry cannot overlap another time entry of the same user. An HOUR slot includes its start time
  and excludes its end time: 09:00–10:00 and 10:00–11:00 do not overlap. A DAY or a RANGE fully occupies each day it
  covers, weekends included, and therefore excludes any other time entry on those days.
- **RG-01-07**: an activity whose start date is in the future cannot be recorded. Only startDate is checked, against
  today's date in Europe/Paris (RG-00-01): a RANGE may end in the future, and an HOUR slot of today may be later than
  the current time.
- **RG-01-08**: computed duration: HOUR = gap in minutes; DAY = 1 standard day (RG-00-02); RANGE = number of working
  days (Monday–Friday) × standard day. A DAY on a Saturday or a Sunday is allowed and counts as one standard day; a
  RANGE without a working day is allowed and counts as 0 min. The standard day is the setting adjusted by the ADMIN
  (SFD-06), 480 min at installation. The duration is frozen when the time entry is saved (creation or update).
- **RG-01-09**: the comment is at most 500 characters long.
- **RG-01-10**: a USER can only edit or delete their own time entries.
- **RG-01-11**: traceability (RG-00-04): each time entry keeps who created it and when, and who last changed it and
  when. Deletion is logical (who, when): a deleted time entry is no longer shown and no longer taken into account in
  the overlap check.
- **RG-01-12**: on update, every field can be changed, granularity included, with the same checks as on creation;
  the overlap check ignores the edited time entry itself.
- **RG-01-13**: in the journal, a RANGE is shown on each day it covers. For a RANGE, the week total only counts its
  working days within the displayed week × standard day.
- **RG-01-14**: recording time is open to the USER and MANAGER roles.

## 4. Screens
- **E1 My journal**: week view (Monday → Sunday), previous / next week navigation, time entries shown per day with
  their type (coloured tag), project, period and comment (instead of the duration, which can be read in the total);
  week total in hours. A New time entry button. Each day that is not in the future (RG-01-07) also has a + button,
  which opens the time entry dialog with that day's date already filled in.
- **E2 Time entry dialog**: choice of the granularity (hour / day / range), date and time fields that adapt, activity
  type, project (shown only when required), comment, computed duration shown live. Save and Cancel buttons.
- **E3 Deletion**: confirmation before deleting.

## 5. Acceptance criteria
- **CA-01-01** — Given a USER, When they record 05/10/2026 from 09:00 to 10:30 as REUNION, Then the time entry is
  created with a duration of 90 min and appears in their journal.
- **CA-01-02** — Given a USER, When they enter an end time before the start time, Then the field is in error and saving
  is blocked.
- **CA-01-03** — Given a USER, When they record a RANGE from Monday 05/10/2026 to Friday 16/10/2026 as FORMATION, Then
  the computed duration is 10 working days (4,800 min).
- **CA-01-04** — Given a time entry 09:00–10:00 on 05/10/2026, When the USER records 09:30–10:30 on the same day, Then
  the API answers 409 with the code RG-01-06 and a message stating the overlap.
- **CA-01-05** — Given the TRAVAIL_PROJET type, When the USER saves without a project, Then the project field is in
  error.
- **CA-01-06** — Given the REUNION type, Then the project field is not shown.
- **CA-01-07** — Given a USER, When they enter a start date in the future, Then the time entry is refused.
- **CA-01-08** — Given a time entry belonging to another USER, When a USER tries to edit it through the API, Then the
  response is 403.
- **CA-01-09** — Given time entries of 90 min and 8 h in the week, Then the displayed total is 9.5 h.

## 6. Out of scope
- Public holidays (counted as working days in v1), validation of time entries by the manager, export, import.
