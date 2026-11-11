# SFD-04 — Team view (MANAGER)

> Depends on SFD-01 (time entries) and SFD-05 (teams).

## 1. Goal
A MANAGER consults, read-only, the activities recorded by the members of the teams they manage, week by week
(RG-00-03).

## 2. Data
No new data. The view relies on the time entries (SFD-01), the activity types and projects (SFD-02) and the teams
(SFD-05).

| Data shown | Source |
|---|---|
| Team members | SFD-05 |
| A member's time entries: day, type (coloured tag), project, duration, comment | SFD-01 |
| Duration per day and week total | computed, RG-04-05 |

## 3. Business rules
- **RG-04-01**: the team view is reserved for the MANAGER role. A MANAGER who manages no team can open it and sees a
  message saying that no team is assigned to them.
- **RG-04-02**: a MANAGER only sees the time entries of the members of the teams they manage. Any request about
  another team, or about an account outside their teams, is refused (403).
- **RG-04-03**: viewing is read-only: the MANAGER can neither create, edit nor delete a member's time entry
  (RG-01-10).
- **RG-04-04**: the view covers one week, Monday to Sunday, with navigation to the previous and next week, like the
  journal (SFD-01, E1).
- **RG-04-05**: a day's duration and a member's week total are computed as in their journal: a RANGE counts its
  working days within the displayed week × the standard day frozen on the time entry (RG-01-08, RG-01-13). Deleted
  time entries are not counted (RG-01-11).
- **RG-04-06**: the scope is current membership: a MANAGER sees all the time entries, older ones included, of the
  current members of their team, and no longer sees those of a member who left it.
- **RG-04-07**: a member whose account is inactive is still shown, flagged as inactive (RG-05-11).
- **RG-04-08**: a MANAGER of several teams chooses the team shown; the first one in alphabetical order is shown by
  default.
- **RG-04-09**: members are sorted by last name then first name.
- **RG-04-10**: the manager's own time entries do not appear in their team's view (RG-05-05).

## 4. Screens
- **E1 My team** (MANAGER): team choice (if several), week navigation, table with one row per member and one column
  per day (day's duration in hours), week total per member. A click on a member opens E2.
- **E2 A member's journal** (MANAGER): the member's journal for the week, presented like My journal (SFD-01, E1):
  time entries per day with type, project, duration, comment, and the week total. No create, edit or delete button.
  Back to E1, keeping the week.

## 5. Acceptance criteria
- **CA-04-01** — Given a MANAGER of a team of two members who recorded 90 min and 8 h in the week, When they open My
  team, Then they see both members with their totals, 1.5 h and 8 h.
- **CA-04-02** — Given a MANAGER, When they open the journal of a member of their team, Then they see the member's
  time entries for the week and no New time entry, Edit or Delete button.
- **CA-04-03** — Given a MANAGER of team A, When they request through the API the time entries of a member of team B,
  Then the response is 403.
- **CA-04-04** — Given a MANAGER, When they try through the API to edit or delete a time entry of a member of their
  team, Then the response is 403.
- **CA-04-05** — Given a USER, When they call the team view API, Then the response is 403; and the My team menu is
  not shown.
- **CA-04-06** — Given an ADMIN, Then the My team menu is not shown and the team view API answers 403.
- **CA-04-07** — Given a member with a RANGE from Thursday 08/10/2026 to Tuesday 13/10/2026, When the MANAGER displays
  the week of 05/10/2026, Then the member's total for that week is 16 h (Thursday and Friday).
- **CA-04-08** — Given a member who deleted a time entry, Then that time entry does not appear in the team view and is
  not counted in their total.
- **CA-04-09** — Given a MANAGER without a team, When they open My team, Then a message says that no team is assigned
  to them.
- **CA-04-10** — Given a MANAGER of two teams, When they change team in the selector, Then the list of members and the
  totals are those of the chosen team, for the same week.
- **CA-04-11** — Given a member removed from the team, Then the MANAGER no longer sees them and the API answers 403
  for their time entries.
- **CA-04-12** — Given a member whose account is deactivated, Then they are still shown with the Inactive mention and
  their time entries can still be consulted.

## 6. Out of scope
- Validation or refusal of time entries by the manager, manager comments.
- Export, printing, indicators and charts, month view.
- Alerts on incomplete weeks, reminders.
- Recording or correcting time on behalf of a member.
- Team consultation by the ADMIN.
