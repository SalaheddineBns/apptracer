# SFD-07 — Daily total time

> Depends on SFD-01 (time entries, journal) and SFD-06 (standard day).

## 1. Goal
While filling in their journal, an employee sees for each day of the week the total time they recorded on it. They
can check at a glance that each day is complete, without adding up their time entries themselves.

## 2. Data
No new data. A day's total time is computed from the SFD-01 time entries.

| Data shown | Source |
|---|---|
| Total time of each day of the week | computed, RG-07-02 |
| Week total | SFD-01, RG-01-13 (unchanged) |

## 3. Business rules
- **RG-07-01**: in My journal (SFD-01, E1), each day of the displayed week, Monday to Sunday, shows the total time
  recorded that day by the employee.
- **RG-07-02**: a day's total time is computed like the week total (RG-01-13):
  - an HOUR or DAY time entry counts its recorded duration on its date;
  - a RANGE counts the standard day frozen on the time entry for each working day (Monday–Friday) it covers, and 0
    for a Saturday or a Sunday it covers.
- **RG-07-03**: deleted time entries are not counted (RG-01-11).
- **RG-07-04**: the sum of the seven days' total times equals the week total (RG-01-13).
- **RG-07-05**: the total time uses the frozen durations of the time entries: a later change of the standard day does
  not change it (RG-01-08, RG-06-04).
- **RG-07-06**: every day follows the same rule, future days included: a future day covered by a RANGE shows the time
  the range counts on it (RG-01-07).
- **RG-07-07**: a day's total time is updated as soon as a time entry is created, edited or deleted, without
  reloading the page.
- **RG-07-08**: the total is shown to the roles that have a journal, USER and MANAGER (RG-01-14), each for their own
  time entries (RG-00-03).

## 4. Screens
- **E1 My journal** (SFD-01, E1, changed):
  - in each day's header, to the right of the day's name (the date and the + button are on the next line when the
    week is shown in seven columns; the name is then abbreviated, "Mon", if the column is too narrow): the day's total
    time in decimal hours, in the same format as the week total ("8 h", "3.5 h", "0.25 h", up to 2 decimals, without
    useless zeros);
  - a day whose total time is 0 shows "–"; a screen reader announces "Monday total: 0 h" ("Monday total: 3.5 h" for a
    day with time);
  - the totals have no state of their own: they arrive with the week's time entries and follow the journal's states
    (loading, error); in a week without time entries, each day shows "–".

## 5. Acceptance criteria
Examples on the week of Monday 05/10/2026.

- **CA-07-01** — Given a USER who recorded Monday 05/10/2026 from 09:00 to 10:30 and from 14:00 to 16:00, When they
  open their journal on that week, Then Monday shows 3.5 h.
- **CA-07-02** — Given a USER, When they record a RANGE from Thursday 08/10/2026 to Tuesday 13/10/2026, Then Thursday
  and Friday show 8 h, Saturday and Sunday show "–".
- **CA-07-03** — Given a USER, When they record a DAY on Saturday 10/10/2026, Then Saturday shows 8 h.
- **CA-07-04** — Given a Monday at 3.5 h, When the USER deletes their time entry from 09:00 to 10:30, Then Monday
  shows 2 h and the week total decreases by 1.5 h, without reloading the page.
- **CA-07-05** — Given a 90-min time entry on Monday and a DAY on Tuesday, Then Monday shows 1.5 h, Tuesday 8 h, the
  other days "–", and the week total is 9.5 h.
- **CA-07-06** — Given a DAY saved on Tuesday with 480 min, When the ADMIN changes the standard day to 450 min, Then
  Tuesday still shows 8 h.
- **CA-07-07** — Given a MANAGER with time entries in the week, When they open their own journal, Then they see the
  total time of each of their days.
- **CA-07-08** — Given a time entry from 09:00 to 09:15 on Monday, Then Monday shows 0.25 h.

## 6. Out of scope
- Comparing the day's time with the standard day (colour, alert for an incomplete or exceeded day).
- Total time per month, per project or per activity type.
- A member's journal and the team view table (SFD-04), which already shows each day's duration per member.
- Export.
