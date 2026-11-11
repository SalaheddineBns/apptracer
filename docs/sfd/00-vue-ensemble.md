# AppTracer — Overview

## Goal
Let each employee record what they worked on over a period (one hour, one day or several days) by choosing an
activity type, and let managers consult their team's activity.

## Actors and roles
| Role | Description |
|---|---|
| USER | Employee: records and consults their own activities |
| MANAGER | Team manager: USER rights + consultation of their team's activities |
| ADMIN | Administrator: manages users, teams, projects and activity types |

## Glossary
- **Time entry**: an activity declared over a period.
- **Granularity**: HOUR (a time slot within a day), DAY (a full day), RANGE (several days).
- **Activity type**: the nature of the work. Initial values:
  TRAVAIL_PROJET, REUNION, LECTURE_SFD, REVUE_MR, FORMATION, AUTRE (project work, meeting, spec reading, merge request
  review, training, other).
- **Project**: the project an activity is about (required for some types).

## Cross-cutting rules
- RG-00-01: the business time zone is Europe/Paris.
- RG-00-02: a standard working day lasts 8 h (480 min), adjustable by the ADMIN (SFD-06).
- RG-00-03: a USER only accesses their own time entries; a MANAGER has read access to those of their team's members.
- RG-00-04: every creation, update or deletion is recorded (who, when).

## Specifications
| ID | Title | Priority |
|---|---|---|
| SFD-01 | Recording activities | P1 |
| SFD-02 | Reference data (activity types, projects) | P1 |
| SFD-03 | Authentication and accounts | P1 |
| SFD-04 | Team view (MANAGER) | P2 |
| SFD-05 | Teams | P2 |
| SFD-06 | Settings (standard day) | P1 |
| SFD-07 | Daily total time | P2 |
| SFD-08 | Interface language (French, English) | P2 |
| SFD-09 | Job titles | P2 |
| SFD-10 | Projects per team | P2 |

Rule IDs (`RG-xx-yy`) and acceptance criterion IDs (`CA-xx-yy`) are cited in the code and in the test names: searching
the code for an ID finds where that rule is implemented and tested.
