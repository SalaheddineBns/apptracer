# Changelog

All notable changes to AppTracer are listed here. The format follows [Keep a Changelog](https://keepachangelog.com/),
and versions follow [Semantic Versioning](https://semver.org/). Each feature refers to its functional specification in
[`docs/sfd/`](docs/sfd/00-vue-ensemble.md).

## [Unreleased]

## [1.0.0] — 2026-11-11

First complete version: employees log their time, managers follow their team, administrators run the application.

### Added
- **Authentication and accounts** ([SFD-03](docs/sfd/SFD-03-authentification-comptes.md)): sign-in with e-mail and
  password, short-lived JWT access token with single-use refresh token rotation, sign-out, password change that
  revokes the other sessions. The ADMIN creates, edits, deactivates and reactivates accounts; an initial ADMIN account
  is created from the configuration.
- **Reference data** ([SFD-02](docs/sfd/SFD-02-referentiels.md)): activity types (with colour and project required
  flag) and projects, managed by the ADMIN, never deleted, only deactivated.
- **Settings** ([SFD-06](docs/sfd/SFD-06-parametres.md)): the ADMIN sets the length of the standard day, 8 h by
  default; durations already recorded are never recalculated.
- **My journal** ([SFD-01](docs/sfd/SFD-01-saisie-activities.md)): record, edit and delete time entries by the hour
  (15-minute steps), the day or a range of up to 31 days; live duration, no overlapping entries, no future start, week
  view with a + button on each day.
- **Teams** ([SFD-05](docs/sfd/SFD-05-equipes.md)): the ADMIN builds teams with one manager and their members; an
  account belongs to at most one team.
- **Team view** ([SFD-04](docs/sfd/SFD-04-vue-equipe.md)): a MANAGER sees, read-only, the week of each member of the
  teams they manage, with per-day durations and totals, and each member's journal.
- **Daily totals** ([SFD-07](docs/sfd/SFD-07-total-time.md)): the total time of each day next to its name in My
  journal.
- **English and French interface** ([SFD-08](docs/sfd/SFD-08-langue-interface.md)): a language selector on the
  sign-in page and in the user menu, server messages in the chosen language, English screen addresses.
- **Job titles** ([SFD-09](docs/sfd/SFD-09-fonctions.md)): Developer, Tech Lead, QA, Scrum Master, Product Owner, Data
  Analyst and any other job title the ADMIN creates; descriptive only, rights still come from the role.
- **Projects per team** ([SFD-10](docs/sfd/SFD-10-projets-par-equipe.md)): each project can be assigned to a team;
  employees are only offered their team's projects and the shared ones, and see them in a new My projects page.
- **Live demo**: a single Docker image (Spring Boot serving the Angular app) deployed on Render with PostgreSQL on
  Neon, demo accounts, a team and two weeks of sample entries, reset every night.
- **Open-source project files**: MIT license, contributing guide, security policy, issue and pull request templates,
  and a CI workflow running the backend and frontend checks on every pull request.

### Quality
- 600+ backend tests (JUnit 5, Mockito, Testcontainers on a real PostgreSQL) and 500+ frontend tests (Vitest).
- Each business rule (`RG-xx-yy`) and acceptance criterion (`CA-xx-yy`) of the specifications is cited in the code
  and in the test names.
- Screens reviewed for accessibility (keyboard, screen readers, AA contrast) and usable from 375 px wide.

[Unreleased]: https://github.com/SalaheddineBns/apptracer/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/SalaheddineBns/apptracer/releases/tag/v1.0.0
