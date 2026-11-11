# SFD-08 — Interface language

> Affects every delivered specification (SFD-01 to SFD-07): displayed texts, formats and screen addresses.

## 1. Goal
Each user chooses the interface language, French or English, and can change it at any time without losing what they
are doing. The application becomes usable by employees who do not read French.

## 2. Data
No new data in the database.

| Data | Source | Rule |
|---|---|---|
| Interface language (`fr` or `en`) | the user's choice, kept in the browser | RG-08-02, RG-08-03 |

## 3. Business rules
- **RG-08-01**: the interface exists in two languages, French and English.
- **RG-08-02**: on the first visit from a browser, the language is the browser's: French if the browser's preferred
  language is French, English for any other language.
- **RG-08-03**: the chosen language is kept in the browser: it survives a page reload and signing out. It does not
  follow the user to another browser or device.
- **RG-08-04**: a language change applies immediately to the whole interface, without reloading the page. The
  selector cannot be reached while a dialog is open: the language is not changed in the middle of an entry. A server
  message already shown is cleared when the language changes; it comes back, translated, on the next action.
- **RG-08-05**: all displayed text follows the chosen language: menus, titles, buttons, labels, help texts, error
  messages under the fields, toasts, confirmations, empty and error states, labels read by screen readers, the
  components' calendar and buttons, the tab title and the page's declared language.
- **RG-08-06**: the messages returned by the server and shown on screen (error message, field errors) are written in
  the interface language.
- **RG-08-07**: the following are not translated: entered data (activity type labels, project names, comments,
  account and team names), rule codes (`RG-01-06`…) and screen addresses.
- **RG-08-08**: formats follow the language:
  - decimal separator: comma in French ("8,5 h"), point in English ("8.5 h");
  - duration of a time entry: "1 h 30" in both languages;
  - day and month names in the chosen language ("lundi" / "Monday"); the week starts on Monday;
  - dates in day/month/year format in both languages ("05/10/2026").
- **RG-08-09**: screen addresses are in English and identical in both languages:

  | Screen | Address |
  |---|---|
  | My journal (SFD-01) | `/journal?week=YYYY-MM-DD` |
  | My team (SFD-04) | `/team?team=<id>&week=YYYY-MM-DD` |
  | A member's journal (SFD-04) | `/team/members/:userId?team=<id>&week=YYYY-MM-DD` |
  | Accounts (SFD-03) | `/admin/accounts` |
  | Teams (SFD-05) | `/admin/teams` |
  | Activity types (SFD-02) | `/admin/activity-types` |
  | Projects (SFD-02) | `/admin/projects` |
  | Settings (SFD-06) | `/admin/settings` |
  | Change my password (SFD-03) | `/account/password` |

- **RG-08-10**: choosing the language is open to everyone, including before signing in (sign-in page).

## 4. Screens
- **E1 Language selector**: two options, "Français" and "English", each written in its own language, with the
  language in force shown as selected. It appears:
  - on the sign-in page (SFD-03, E1);
  - in the header's user menu (SFD-03, E2), next to Change my password.

## 5. Acceptance criteria
- **CA-08-01** — Given a first visit with a browser in French, Then the sign-in page is in French; Given a first
  visit with a browser in English or German, Then it is in English.
- **CA-08-02** — Given the sign-in page in French, When the user chooses "English", Then the whole page switches to
  English, without reloading.
- **CA-08-03** — Given a USER signed in on My journal in French, When they choose "English" in the user menu, Then
  the title becomes "My journal" and the days become "Monday" to "Sunday", without reloading the page.
- **CA-08-04** — Given a user who chose "English", When they reload the page, then sign out and sign in again on the
  same browser, Then the interface is still in English.
- **CA-08-05** — Given a time entry from 09:00 to 10:00 on Monday 05/10/2026, When the USER, in English, records
  09:30–10:30 on the same day, Then the API answers 409 with the code `RG-01-06` and the toast shows a message in
  English; in French, the same case shows a message in French.
- **CA-08-06** — Given a 90-min time entry on Monday and a DAY on Tuesday of the week of 05/10/2026, Then the week
  total shows "9.5 h" in English and "9,5 h" in French.
- **CA-08-07** — Given an activity type "Réunion", When the interface is in English, Then the type is still shown as
  "Réunion".
- **CA-08-08** — Given the three roles (USER, MANAGER, ADMIN), Then every text of the interface exists in French and
  in English: no screen mixes both languages.

## 6. Out of scope
- Any language other than French and English.
- A language preference saved in the account (following the user from one device to another).
- Translation of entered data (activity types, projects, comments).
- Screen addresses that differ by language; redirection of the former French addresses.
