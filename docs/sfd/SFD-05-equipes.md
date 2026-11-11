# SFD-05 — Teams

## 1. Goal
An ADMIN builds the teams: they give each one a name, a manager and members. Teams define what a MANAGER can consult
(SFD-04, RG-00-03).

## 2. Data
### Team
| Field | Type | Required | Rule |
|---|---|---|---|
| name | text | yes | RG-05-01 |
| manager | reference to an account | yes | RG-05-02 |
| members | list of references to accounts | no | RG-05-03, RG-05-04 |

## 3. Business rules
- **RG-05-01**: a team's name is unique, case-insensitive, 2 to 100 characters long.
- **RG-05-02**: a team has exactly one manager, who is an active account with the MANAGER role.
- **RG-05-03**: a team's members are accounts with the USER or MANAGER role. An ADMIN cannot be a member of a team
  (they do not record activities, RG-01-14).
- **RG-05-04**: an account belongs to at most one team.
- **RG-05-05**: a team's manager is not one of its members: they consult their team, but their own time entries do
  not appear in it. They can be a member of another team.
- **RG-05-06**: the same MANAGER can manage several teams.
- **RG-05-07**: a team may have no members.
- **RG-05-08**: only an ADMIN can create, edit or delete a team, and change its manager or members.
- **RG-05-09**: deleting a team is permanent. Its members end up without a team; their accounts and time entries are
  not changed.
- **RG-05-10**: as long as an account manages at least one team, it can neither be deactivated nor lose the MANAGER
  role: another manager must be appointed or the team deleted first.
- **RG-05-11**: a member whose account is deactivated stays a member of their team, flagged as inactive.
- **RG-05-12**: a member whose role becomes ADMIN is removed from their team (RG-05-03).
- **RG-05-13**: the list of teams is sorted by name; a team's members are sorted by last name then first name.
- **RG-05-14**: every creation, update or deletion of a team is recorded (RG-00-04).

## 4. Screens
- **E1 Teams** (ADMIN): list (name, manager, number of members), New team button, Edit and Delete actions.
- **E2 Team dialog** (ADMIN): name, manager (chosen among the active accounts with the MANAGER role), members (chosen
  among the eligible accounts; an account already in another team is flagged).
- **E3 Deletion**: confirmation stating the team's name and the number of members concerned.

## 5. Acceptance criteria
- **CA-05-01** — Given an ADMIN, When they create the team "Produit" with a manager and two members, Then the team
  appears in the list with its manager and "2 members".
- **CA-05-02** — Given a team "Produit", When an ADMIN creates a team "PRODUIT", Then the API answers 409 with the
  code RG-05-01 and the name field is in error.
- **CA-05-03** — Given an account with the USER role, When an ADMIN tries to appoint it as manager through the API,
  Then the API answers 409 with the code RG-05-02; and this account is not offered in the list of managers.
- **CA-05-04** — Given an account already a member of team A, When an ADMIN adds it to team B, Then the API answers
  409 with the code RG-05-04 and a message naming team A.
- **CA-05-05** — Given an account with the ADMIN role, When an ADMIN tries to add it as a member through the API, Then
  the API answers 409 with the code RG-05-03; and this account is not offered in the list of members.
- **CA-05-06** — Given the manager of team A, When an ADMIN tries to add them as a member of team A, Then the API
  answers 409 with the code RG-05-05.
- **CA-05-07** — Given an account that manages a team, When an ADMIN tries to deactivate it, Then the API answers 409
  with the code RG-05-10 and a message naming the team.
- **CA-05-08** — Given an account that manages a team, When an ADMIN tries to change its role to USER, Then the API
  answers 409 with the code RG-05-10.
- **CA-05-09** — Given a team of three members, When an ADMIN deletes it after confirmation, Then the team disappears
  from the list and the three accounts still exist, without a team.
- **CA-05-10** — Given a member of a team, When their account is deactivated, Then they stay in the team, flagged as
  inactive.
- **CA-05-11** — Given a USER or a MANAGER, When they call the team management API, Then the response is 403; and the
  Teams menu is not shown.
- **CA-05-12** — Given an ADMIN, When they change a team's manager, Then the former manager can no longer consult
  that team and the new one can (SFD-04).

## 6. Out of scope
- History of memberships (who was in which team on which date).
- Team hierarchy, sub-teams, several managers per team.
- Linking a project to a team, covered by SFD-10.
- Management of their own team by the MANAGER.
