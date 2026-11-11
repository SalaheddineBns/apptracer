# SFD-09 — Job titles

> Depends on SFD-03 (accounts and roles), follows the reference data model of SFD-02, and follows SFD-08 (interface
> language).

## 1. Goal
The ADMIN sets each employee's job title (Developer, Tech Lead, QA, Scrum Master, Product Owner, Data Analyst…) and
can create others. The job title describes the employee's trade; it does not change their rights, which still come
from their role (USER, MANAGER, ADMIN).

## 2. Data
### Job title (reference data)
| Field | Type | Required | Rule |
|---|---|---|---|
| code | text | yes | RG-09-05 |
| label | text | yes | ≤ 100 characters, RG-09-05 |
| active | boolean | yes | true on creation, RG-09-06 |

### User account (SFD-03, field added)
| Field | Type | Required | Rule |
|---|---|---|---|
| jobTitle | reference to a job title | no | RG-09-02, RG-09-03 |

## 3. Business rules
- **RG-09-01**: the job title is purely descriptive: it neither grants nor removes any right. An account's rights are
  those of its role (RG-03-10); a USER account, whatever its job title, only edits its own time entries (RG-01-10).
- **RG-09-02**: an account has at most one job title, optional. Only accounts with the USER or MANAGER role can have
  one; an ADMIN account has none.
- **RG-09-03**: role and job title are independent: the job title does not determine the role, and changing an
  account's role does not change its job title (for example, a USER account "Tech Lead" can become MANAGER and stay
  "Tech Lead"), except when switching to the ADMIN role: the job title is then removed. Switching back to USER or
  MANAGER does not restore it.
- **RG-09-04**: only an ADMIN assigns, changes or removes an account's job title, when creating or editing the
  account. The employee does not change their own job title.
- **RG-09-05**: a job title's code is unique (case-insensitive), in capitals, made of letters, digits and `_`, 2 to
  30 characters long, and cannot be changed after creation. The label is required, 100 characters at most; two job
  titles may have the same label.
- **RG-09-06**: a job title is never deleted, only deactivated, and can be reactivated. An inactive job title is no
  longer offered for assignment; accounts that already have it keep it, and it is shown with the Inactive mention.
  When editing such an account, the ADMIN can keep it or choose an active one.
- **RG-09-07**: only an ADMIN creates, edits, deactivates or reactivates a job title. Any signed-in user can read the
  active job titles; the ADMIN also reads the inactive ones.
- **RG-09-08**: the list of job titles is sorted by label, in alphabetical order.
- **RG-09-09**: six job titles are created at installation, active:

  | Code | Label |
  |---|---|
  | DEVELOPER | Developer |
  | TECH_LEAD | Tech Lead |
  | QA | QA |
  | SCRUM_MASTER | Scrum Master |
  | PRODUCT_OWNER | Product Owner |
  | DATA_ANALYST | Data Analyst |

- **RG-09-10**: a job title's label is data: it is not translated by a language change (RG-08-07).
- **RG-09-11**: every creation, update, deactivation or reactivation of a job title, and every change of an account's
  job title, is recorded (RG-00-04).

## 4. Screens
- **E1 Job titles** (ADMIN, `/admin/job-titles`): list (code, label, active), New job title button, Edit, Deactivate
  (with confirmation) and Reactivate actions. A Job titles menu entry reserved for the ADMIN, between Projects and
  Settings.
- **E2 Job title dialog** (ADMIN): code (on creation only), label.
- **E3 Deactivation**: confirmation before deactivating a job title.
- **Accounts** (SFD-03, E4 changed): Job title column (empty if none, Inactive mention for a deactivated job title).
- **Account dialog** (SFD-03, E5 changed): optional Job title dropdown, among the active job titles (plus the inactive
  one the account already has), offered only for the USER and MANAGER roles.

## 5. Acceptance criteria
- **CA-09-01** — Given a fresh installation, When the ADMIN opens Job titles, Then they see six active job titles, in
  this order: Data Analyst, Developer, Product Owner, QA, Scrum Master, Tech Lead.
- **CA-09-02** — Given an ADMIN, When they create the job title "UX Designer" (code `UX_DESIGNER`), Then it appears in
  the list and among the choices of the account dialog.
- **CA-09-03** — Given the job title `TECH_LEAD`, When an ADMIN creates a job title with the code `TECH_LEAD` or
  `tech_lead`, Then the API answers 409 and the code field is in error; Given the code "tech lead", Then the field is
  in error and saving is blocked.
- **CA-09-04** — Given a USER account, When the ADMIN assigns it "Developer", Then the Job title column shows
  "Developer"; this employee signs in, only sees My journal, and the API answers 403 if they try to edit someone
  else's time entry.
- **CA-09-05** — Given a USER account with the job title "Tech Lead", Then it has no access to the team view; When the
  ADMIN switches it to the MANAGER role and appoints it manager of a team, Then it keeps the "Tech Lead" job title and
  can access the team view.
- **CA-09-06** — Given an ADMIN account, When an ADMIN tries to assign it a job title through the API, Then the
  response is 409; and the account dialog offers no job title for the Administrator role.
- **CA-09-07** — Given the job title "QA" held by an account, When the ADMIN deactivates it, Then that account shows
  "QA" with the Inactive mention and "QA" is no longer offered for another account; When the ADMIN reactivates it,
  Then it is offered again.
- **CA-09-08** — Given a USER account with the job title "Developer", When the ADMIN switches it to the ADMIN role,
  Then its job title is removed.
- **CA-09-09** — Given a USER or a MANAGER, When they call the job title create or update API, Then the response is
  403; and the Job titles menu is not shown.
- **CA-09-10** — Given the interface in French, Then the label "Developer" is shown as is; Given the interface in
  English, Then the screen is titled "Job titles" and its texts are in English.

## 6. Out of scope
- Rights that differ by job title.
- Several job titles per account.
- Changing one's own job title.
- Showing the job title anywhere other than the accounts (header, team view, team dialog).
- Filter, sort or statistics by job title.
- Permanent deletion of a job title.
