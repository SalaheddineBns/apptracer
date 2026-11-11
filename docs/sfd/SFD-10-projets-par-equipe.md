# SFD-10 — Projects per team

> Depends on SFD-02 (projects), SFD-05 (teams) and SFD-01 (recording activities); follows SFD-08 (interface
> language).

## 1. Goal
The ADMIN assigns each project to the team that runs it. A team gathers the job titles needed to run its projects;
an employee is therefore only offered, when recording time, the projects of their team and the projects shared by
everyone. Lists are shorter and project mistakes rarer.

## 2. Data
### Project (SFD-02, field added)
| Field | Type | Required | Rule |
|---|---|---|---|
| team | reference to a team | no | RG-10-01, RG-10-02 |

## 3. Business rules
- **RG-10-01**: a project is assigned to at most one team. A team can have several projects. A project without a
  team is a shared project.
- **RG-10-02**: only an ADMIN assigns a project to a team, changes its team or removes it, when creating or editing
  the project, at any time, even if the project already has time entries. The manager does not change assignments.
- **RG-10-03**: the projects **allowed** for an account, when recording time, are the active projects (RG-02-06) that
  are:
  - without a team;
  - or assigned to the team the account belongs to (RG-05-04);
  - or, for a MANAGER, assigned to a team they manage (RG-05-02, RG-05-06).
  An account that belongs to no team and manages no team therefore only has the projects without a team.
- **RG-10-04**: when creating a time entry, the chosen project must be allowed for its author (RG-10-03). Otherwise
  the API answers 409 with the code RG-10-04, with the error on the project field, even if the screen does not offer
  that project.
- **RG-10-05**: when editing a time entry, an unchanged project is accepted even if it is no longer allowed for its
  author; a new project must be allowed (RG-10-04). The dialog of that time entry offers the allowed projects, plus
  the project already on the time entry. RG-02-06 still applies: an inactive project is refused, even unchanged.
- **RG-10-06**: changing a project's team, removing a project from a team or moving an employee to another team
  changes no existing time entry: they keep their project, stay displayed and count in the totals.
- **RG-10-07**: when a team is deleted (RG-05-09), its projects become projects without a team.
- **RG-10-08**: deactivating then reactivating a project does not change its team.
- **RG-10-09**: the list of projects offered when recording time is sorted by name (RG-02-10); the list of teams
  offered in the project dialog is sorted by name (RG-05-13).
- **RG-10-10**: every assignment, change or removal of a project's team is recorded (RG-00-04).
- **RG-10-11**: a USER or a MANAGER consults their projects in My projects, read-only: one group per team they
  belong to or manage (teams sorted by name), with the team's active projects, then the Shared projects group with
  the active projects without a team; projects are sorted by name and shown with their name and code. These are
  exactly their allowed projects (RG-10-03). An account without a team sees "You are not part of any team", then the
  shared projects. The ADMIN does not have this page (no journal); the API answers them 403.

## 4. Screens
- **Projects** (SFD-02, E3 changed, ADMIN): Team column (empty for a project without a team).
- **Project dialog** (SFD-02, E4 changed, ADMIN): optional Team dropdown, among the existing teams sorted by name,
  which can be cleared (project without a team).
- **Time entry dialog** (SFD-01, changed): the Project list only contains the allowed projects (RG-10-03), plus, when
  editing, the project already on the time entry (RG-10-05). Presentation unchanged.
- **E1 My projects** (USER, MANAGER, `/projects`): My projects menu entry right after My journal. Groups described by
  RG-10-11. States: loading (list skeleton), error (message and Retry), empty (no active project anywhere: "No
  projects are offered to you yet."), and, for a team without an active project, "No projects for this team" in its
  group.
- The journal cards, the team view and the Teams screen do not change.

## 5. Acceptance criteria
Shared data set, week of Monday 05/10/2026: team **Dev** is managed by Marion (MANAGER) and has project **ALPHA**;
team **QA** has project **BETA**; project **COMMUN** has no team. Alice (USER) is a member of Dev, Bruno (USER) is a
member of QA, Chloé (USER) has no team.

- **CA-10-01** — Given an ADMIN, When they assign ALPHA to team Dev from the project dialog, Then ALPHA's Team column
  shows "Dev".
- **CA-10-02** — Given the shared data set, When each one opens New time entry with the Travail projet type, Then
  Alice sees ALPHA and COMMUN, Bruno sees BETA and COMMUN, Chloé sees COMMUN, and Marion sees ALPHA and COMMUN.
- **CA-10-03** — Given Alice, When she records through the API a time entry on 05/10/2026 from 09:00 to 10:00, type
  Travail projet, project BETA, Then the API answers 409 with the code RG-10-04 and the project field is in error.
- **CA-10-04** — Given a time entry of Alice on 05/10/2026 on ALPHA, When the ADMIN assigns ALPHA to team QA, Then the
  time entry stays in Alice's journal and counts in her totals; When Alice only edits its comment, Then saving is
  accepted; When she chooses BETA instead, Then the API answers 409 (RG-10-04).
- **CA-10-05** — Given project BETA of team QA, When the ADMIN deletes team QA, Then BETA no longer has a team and
  Chloé sees it in New time entry.
- **CA-10-06** — Given ALPHA assigned to Dev, When the ADMIN deactivates then reactivates it, Then ALPHA is still
  assigned to Dev.
- **CA-10-07** — Given a USER or a MANAGER, When they call the project update API to change its team, Then the
  response is 403.
- **CA-10-08** — Given the interface in English, Then the column and the field are titled "Team".
- **CA-10-09** — Given the shared data set, When each one opens My projects, Then Alice sees the Dev group with ALPHA,
  then Shared projects with COMMUN; Chloé sees "You are not part of any team", then COMMUN; Marion sees Dev with ALPHA,
  then COMMUN; and the ADMIN does not have the My projects entry and gets 403 from the API.

## 6. Out of scope
- A project shared by several teams.
- Project assignment by the manager.
- A time spent per project view.
- A team filter in the list of projects.
- Restricting activity types per team.
- In My projects: time spent per project, list of the team's members, inactive projects, any action (the page is
  read-only).
