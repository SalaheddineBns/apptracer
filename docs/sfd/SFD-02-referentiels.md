# SFD-02 — Reference data (activity types, projects)

## 1. Goal
An ADMIN manages the list of activity types and the list of projects offered when recording activities. Any
signed-in user consults the active types and projects to record their activities.

## 2. Data
### Activity type
| Field | Type | Required | Rule |
|---|---|---|---|
| code | text | yes | RG-02-01 |
| label | text | yes | ≤ 100 characters |
| projectRequired | boolean | yes | RG-02-03 |
| color | enum (design system palette) | yes | RG-02-04 |
| active | boolean | yes | true on creation, RG-02-05 |

### Project
| Field | Type | Required | Rule |
|---|---|---|---|
| code | text | yes | RG-02-01 |
| name | text | yes | ≤ 100 characters |
| active | boolean | yes | true on creation, RG-02-05 |

## 3. Business rules
- **RG-02-01**: the code is unique within its reference list, in capitals, made of letters, digits and `_`, 2 to 30
  characters long. It cannot be changed after creation.
- **RG-02-02**: the initial activity types are TRAVAIL_PROJET, REUNION, LECTURE_SFD, REVUE_MR, FORMATION and AUTRE,
  all active.
- **RG-02-03**: `projectRequired` says whether a time entry of this type must have a project (RG-01-05). It is true for
  TRAVAIL_PROJET and REVUE_MR, false for the other initial types.
- **RG-02-04**: each activity type has a colour chosen from the design system palette; it is used for the tag shown in
  the journal (SFD-01, E1).
- **RG-02-05**: a type or a project is never deleted, only deactivated, and can be reactivated. The existing time
  entries that reference it are kept and still shown.
- **RG-02-06**: an inactive type or project is no longer offered for a new time entry nor when editing a time entry.
- **RG-02-07**: `projectRequired` can no longer be changed once a time entry uses the type.
- **RG-02-08**: only an ADMIN can create, edit, deactivate or reactivate a type or a project.
- **RG-02-09**: any signed-in user can read the active types and projects; only an ADMIN also sees the inactive ones.
- **RG-02-10**: the lists are sorted by label (types) and by name (projects), in alphabetical order.

## 4. Screens
- **E1 Activity types** (ADMIN): list (code, label, coloured tag, project required, active), New type button, Edit
  and Deactivate / Reactivate actions.
- **E2 Type dialog** (ADMIN): code (creation only), label, colour, project required.
- **E3 Projects** (ADMIN): list (code, name, active), New project button, Edit and Deactivate / Reactivate actions.
- **E4 Project dialog** (ADMIN): code (creation only), name.
- **E5 Deactivation**: confirmation before deactivating a type or a project.

## 5. Acceptance criteria
- **CA-02-01** — Given a fresh installation, When a signed-in user lists the activity types, Then they get the six
  initial types, active.
- **CA-02-02** — Given the initial types, Then TRAVAIL_PROJET and REVUE_MR have `projectRequired` set to true and the
  four others to false.
- **CA-02-03** — Given an ADMIN, When they create a type with a code that already exists, Then the API answers 409
  with the code RG-02-01.
- **CA-02-04** — Given an ADMIN, When they enter the code `réunion client`, Then the field is in error and saving is
  blocked.
- **CA-02-05** — Given an existing type, When an ADMIN opens the edit dialog, Then the code field cannot be changed.
- **CA-02-06** — Given an ADMIN, When they deactivate project P1, Then P1 no longer appears in the list read by a
  USER, but stays visible (inactive) for the ADMIN.
- **CA-02-07** — Given a deactivated type used by an existing time entry, Then the time entry is still shown in the
  journal with the type's label and colour.
- **CA-02-08** — Given an ADMIN, When they reactivate a deactivated project, Then it is offered to USERs again.
- **CA-02-09** — Given a USER, When they call the API to create or edit a type or a project, Then the response is
  403; and the Activity types and Projects menus are not shown.
- **CA-02-10** — Given a type used by at least one time entry, When an ADMIN tries to change `projectRequired`, Then
  the API answers 409 with the code RG-02-07.
- **CA-02-11** — Given a user who is not signed in, When they call the reference data API, Then the response is 401.

## 6. Out of scope
- Permanent deletion of a type or a project.
- Linking a project to a client or to members; project start and end dates. Linking a project to a team is covered
  by SFD-10.
- Import / export of the reference data.
- Setting the standard day (RG-00-02), covered by SFD-06.
