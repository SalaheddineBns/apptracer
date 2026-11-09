# AppTracer — Activity log (TimeLog)

AppTracer is a web application where each employee records what they worked on, for one hour, one day or a range of
days, by choosing an **activity type** (project work, meeting, spec reading, merge request review, training…).
Managers then consult, in read-only mode, the activity of the teams they manage, and administrators run the
reference data, the accounts and the teams.

The interface is available in English and French: a selector switches the language. This README quotes the English
screen labels.

---

## Key concepts

| Concept | Meaning |
|---|---|
| **Time entry** | One declared activity over a period, with an activity type, an optional project and an optional comment (500 characters max). |
| **Granularity** | **Hour**: a time slot within one day, by 15-minute steps. **Day**: a full day. **Range**: several consecutive days, 31 at most. |
| **Activity type** | The nature of the work. Initial types (stored in French, shown as is): *Travail projet* (project work), *Réunion* (meeting), *Lecture de SFD* (spec reading), *Revue de MR* (merge request review), *Formation* (training), *Autre* (other). Each type has a colour and says whether a project is required. |
| **Project** | What the work is about. Required for some types (e.g. *Travail projet*, *Revue de MR*), forbidden for the others. |
| **Standard day** | The length of a working day: **8 h (480 min)** at installation, adjustable by an ADMIN. It is used to compute the duration of a DAY or a RANGE. |
| **Team** | A named group with exactly one manager and any number of members. A person belongs to at most one team. |

## How it works

1. **Sign in** with your e-mail and password (`/login`). There is no self sign-up: an ADMIN creates every account.
   The session stays open thanks to a renewable token, and **Sign out** (user menu) closes it.
2. **Fill your journal** (`/journal`, **My journal**). The page shows one week, Monday to Sunday, with:
   - each day's entries (type as a coloured tag, project, period, comment);
   - the **total time of each day**, next to the day name, and the **week total** at the bottom;
   - **Previous week / Next week** to move between weeks (the week is kept in the URL).
3. **Add an entry** with **New time entry** (top right) or the **+** of a given day (the date is then
   pre-filled). Choose the granularity, the date and times, the activity type and, when required, the project.
   The **duration is computed live** before you save.
4. **Edit or delete** an entry from its card (pencil and bin icons). Deletion asks for an explicit confirmation.

### Rules applied to every entry

- **No overlap**: two entries of the same person cannot cover the same time. An HOUR slot excludes its end time
  (09:00–10:00 and 10:00–11:00 are fine); a DAY or a RANGE occupies every day it covers, weekends included.
- **No future start**: the start date cannot be after today (Europe/Paris time). A RANGE may end in the future.
- **Durations**:
  - HOUR = the exact gap, by 15-minute steps (latest end time 23:45);
  - DAY = one standard day, even on a Saturday or Sunday;
  - RANGE = number of working days (Monday–Friday) × standard day; public holidays count as working days.
- **Frozen durations**: a duration is computed and stored when the entry is saved. Changing the standard day
  later never recalculates existing entries.
- **Weekly totals**: a RANGE spanning several weeks only counts, in each week, its working days within that week.
- **Traceability**: every creation, modification and deletion records who did it and when. Deletion is logical:
  a deleted entry disappears from the journal and from the totals.

---

## Roles and what each one can do

Every account has exactly one role. **MANAGER includes all USER rights.** An ADMIN has no journal: they manage the
application, they do not record activity.

### USER — employee
- Signs in, changes their own password (the old one is required; other sessions are closed), and switches the
  interface between English and French.
- Records, edits and deletes **their own** time entries in **My journal**, and sees their daily and weekly totals.
- Sees only their own entries: never anyone else's.

### MANAGER — team manager
- Everything a USER can do, with their own journal.
- Opens **My team** (`/team`) to consult, **read-only**, the teams they manage:
  - a table with one row per member, one column per day (hours) and the week total;
  - a team selector when they manage several teams (the first in alphabetical order is shown by default);
  - a click on a member opens **that member's journal** for the week (`/team/members/:userId`), with comments,
    and no create, edit or delete button.
- Sees only the **current members** of the teams they manage, including the members' older entries. A member who
  leaves the team is no longer visible. The manager's own entries do not appear in their team's view.
- Can never create, edit or delete a member's entry.

### ADMIN — administrator
- Has no journal: after signing in, lands on **Accounts**.
- **Accounts** (`/admin/accounts`): creates accounts (e-mail, name, role, optional job title, initial password), edits name, role
  and job title,
  deactivates and reactivates them. An ADMIN cannot deactivate their own account or remove their own ADMIN role.
  A deactivated account can no longer sign in, and its sessions are revoked.
- **Teams** (`/admin/teams`): creates, edits and deletes teams, chooses their manager (an active MANAGER) and their
  members (USER or MANAGER accounts). A manager of a team cannot be deactivated or lose the MANAGER role until
  the team gets another manager or is deleted.
- **Activity types** (`/admin/activity-types`) and **projects** (`/admin/projects`): creates, edits, deactivates and
  reactivates them. They are never deleted. An inactive type or project is no longer offered for new entries,
  but existing entries keep displaying it. Once a type is used by an entry, its "project required" flag is frozen.
- **Job titles** (`/admin/job-titles`): Developer, Tech Lead, QA, Scrum Master, Product Owner, Data Analyst at
  installation; creates, renames, deactivates and reactivates them. A job title describes what a USER or MANAGER does
  and never grants rights; an ADMIN has none.
- **Settings** (`/admin/settings`): sets the **standard day** (between 1 h and 24 h, by 15-minute steps).
  The screen shows who changed it last and when. The change applies only to entries created or edited afterwards.

### Permissions at a glance

| Action | USER | MANAGER | ADMIN |
|---|:---:|:---:|:---:|
| Sign in, change own password, switch language | ✅ | ✅ | ✅ |
| Record / edit / delete own entries (**My journal**) | ✅ | ✅ | — |
| See daily and weekly totals of own journal | ✅ | ✅ | — |
| Consult a managed team and its members' journals (read-only) | — | ✅ | — |
| Edit or delete someone else's entry | — | — | — |
| Read active activity types and projects | ✅ | ✅ | ✅ |
| Manage activity types and projects | — | — | ✅ |
| Manage accounts, roles and job titles | — | — | ✅ |
| Manage teams, managers and members | — | — | ✅ |
| Change the standard day | — | — | ✅ |

The server enforces every rule: a hidden menu is a convenience, not the protection. A request without a valid token
gets **401**, and a request with an insufficient role gets **403**.

---

## Architecture

| Layer | Technology |
|---|---|
| Frontend | Angular 22 (standalone components, signals), PrimeNG, Tailwind CSS (`tailwindcss-primeui`) |
| Backend | Java 21, Spring Boot 3 (Web, Data JPA, Security, Validation), Maven |
| Database | PostgreSQL 16, schema migrations with Flyway |
| API contract | OpenAPI (springdoc), from which the Angular TypeScript client is generated |
| Security | Spring Security + JWT: short-lived access token, single-use refresh token stored server-side |
| Tests | JUnit 5, Mockito, Testcontainers (back), Vitest (front), Cypress (end-to-end) |

```
backend/    Spring Boot API (/api/v1), Flyway migrations, tests
frontend/   Angular application, generated API client, Cypress e2e tests
docs/api/   OpenAPI contract (openapi.json), source of the generated client
```

## Getting started

Prerequisites: Docker, Java 21, Node.js 22.22+ or 24.15+ (required by the Angular CLI).

| Action | Command |
|---|---|
| Start the database | `docker compose up -d db` |
| Run the API (port 8080) | `cd backend && ./mvnw spring-boot:run` |
| Run the frontend (port 4200) | `cd frontend && npm start` |
| Backend tests | `cd backend && ./mvnw test` |
| Format backend code | `cd backend && ./mvnw spotless:apply` |
| Frontend tests | `cd frontend && npx ng test --watch=false` |
| Frontend lint | `cd frontend && npx ng lint` |
| End-to-end tests on an isolated, disposable stack | `cd frontend && npm run e2e:stack` |
| Same stack left running for a manual demo | `cd frontend && npm run demo:stack` |
| Export the OpenAPI contract | `cd backend && ./mvnw test -Dtest=OpenApiExportTest` |
| Regenerate the API client | `cd frontend && npm run api:generate` |

Configuration is read from environment variables, among them:
- `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_HOST`, `DB_PORT` for the database;
- `JWT_SECRET` (mandatory, 32 bytes or more), plus `JWT_ACCESS_TTL` and `JWT_REFRESH_TTL` for the tokens;
- `ADMIN_EMAIL`, `ADMIN_PASSWORD`, `ADMIN_FIRST_NAME`, `ADMIN_LAST_NAME` for the **initial ADMIN account**, created
  at first start-up if no ADMIN exists. Use this account to create the other users;
- `DEMO_PASSWORD` (development only, optional) to also create, at start-up, three **demo accounts** sharing that
  password: `manager@apptracer.local` (MANAGER), `dev@apptracer.local` (USER, Developer) and `qa@apptracer.local`
  (USER, QA), plus the team **Demo team** led by the manager with the developer and the QA as members. An account
  whose e-mail already exists is left untouched, and the team is only created once. Never set it in production.

The full list, with defaults, is in [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml).

## Demo deployment

The public demo runs as **one free container**: the [`Dockerfile`](Dockerfile) builds the Angular app and serves it
from the Spring Boot API, so there is one URL and no CORS. It is described for Render in [`render.yaml`](render.yaml),
with a free PostgreSQL database on Neon.

1. **Database**: create a free project on [Neon](https://neon.tech) (region Frankfurt) and note its host, database,
   user and password.
2. **App**: on [Render](https://render.com), choose *New > Blueprint*, select this repository, then fill in the values
   it asks for: the Neon connection, `ADMIN_PASSWORD` (kept private), `DEMO_PASSWORD` (published with the demo
   accounts) and `PRIMEUI_LICENSE`. Render builds the image and deploys it on every push to `main`.
3. **Nightly reset**: in Render, copy the service's *Deploy Hook* URL. In GitHub, add two repository secrets:
   `RENDER_DEPLOY_HOOK_URL` and `DEMO_DATABASE_URL` (the Neon connection string). The
   [`Demo reset`](.github/workflows/demo-reset.yml) workflow then empties the database every night and redeploys;
   it can also be run by hand from the *Actions* tab.

At start-up the app creates the ADMIN, the demo manager, developer and QA, the team **Demo team**, the project
**AppTracer** and the ten previous working days of entries for the developer and the QA. A free Render service
sleeps when idle: the first visit after a quiet period can take up to a minute.

## Documentation

| Document | Content |
|---|---|
| [`docs/api/openapi.json`](docs/api/openapi.json) | REST API contract (OpenAPI 3): every endpoint, request and response; the Angular client is generated from it |
| [`backend/src/main/resources/db/migration/`](backend/src/main/resources/db/migration/) | Database schema, one Flyway migration per change |
| [`backend/src/main/resources/application.yml`](backend/src/main/resources/application.yml) | Backend configuration and the environment variables it reads |

The functional specifications are kept outside the repository. The application was built from nine of them; each
business rule (`RG-xx-yy`) and acceptance criterion (`CA-xx-yy`) is cited in the code and in the test names, so
searching the code for an ID such as `RG-05-04` finds where that rule is implemented and tested.

| Spec | Title |
|---|---|
| SFD-01 | Recording activities (**My journal**) |
| SFD-02 | Reference data (activity types, projects) |
| SFD-03 | Authentication and accounts |
| SFD-04 | Team view (MANAGER) |
| SFD-05 | Teams |
| SFD-06 | Settings (standard day) |
| SFD-07 | Daily total time |
| SFD-08 | Interface language (English / French) |
| SFD-09 | Job titles |
