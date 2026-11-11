# SFD-03 — Authentication and accounts

## 1. Goal
A user signs in with their e-mail and password, stays signed in thanks to a renewable token, and signs out. An ADMIN
creates and manages the accounts and their roles.

## 2. Data
### User account
| Field | Type | Required | Rule |
|---|---|---|---|
| email | text | yes | sign-in identifier, RG-03-01 |
| firstName / lastName | text | yes | ≤ 100 characters each |
| password | text | yes | RG-03-02, RG-03-03 |
| role | enum USER / MANAGER / ADMIN | yes | RG-03-10 |
| active | boolean | yes | true on creation, RG-03-05 |

### Session
| Field | Type | Rule |
|---|---|---|
| accessToken | JWT | RG-03-06 |
| refreshToken | opaque token | RG-03-07, RG-03-08 |

## 3. Business rules
- **RG-03-01**: the e-mail is unique, case-insensitive, and in a valid e-mail format.
- **RG-03-02**: the password is at least 12 characters long and contains at least one letter and one digit.
- **RG-03-03**: the password is never stored nor returned in clear (BCrypt hash).
- **RG-03-04**: for an unknown e-mail or a wrong password, the response is identical (401, message "Invalid
  credentials") so as not to reveal whether an account exists.
- **RG-03-05**: an inactive account can neither sign in nor renew its session; the response is the same as for
  invalid credentials.
- **RG-03-06**: the access token carries the user's identifier and role; its lifetime is `JWT_ACCESS_TTL` (15 minutes
  by default).
- **RG-03-07**: the refresh token has a lifetime of `JWT_REFRESH_TTL` (7 days by default), is stored on the server
  and can only be used once: each renewal issues a new token pair and invalidates the old refresh token.
- **RG-03-08**: signing out revokes the refresh token of the current session.
- **RG-03-09**: any request without a valid access token on a protected resource gets 401; with an insufficient role,
  403.
- **RG-03-10**: an account has exactly one role. MANAGER includes the USER rights.
- **RG-03-11**: only an ADMIN can create an account, edit it (first name, last name, role), deactivate it or
  reactivate it. There is no self sign-up.
- **RG-03-12**: when creating an account, the ADMIN sets an initial password.
- **RG-03-13**: an ADMIN can neither deactivate their own account nor remove their own ADMIN role.
- **RG-03-14**: deactivating an account or changing its role revokes all its refresh tokens.
- **RG-03-15**: a signed-in user can change their password by providing the old one; the change revokes their other
  sessions.
- **RG-03-16**: an initial ADMIN account is created at installation, from the configuration.

## 4. Screens
- **E1 Sign-in** (`/login`): e-mail, password, Sign in button. Generic error message on failure. After signing in,
  redirect to My journal (or to the page initially requested). An ADMIN, who has no journal (RG-01-14), is redirected
  to Accounts.
- **E2 Header**: name of the signed-in user, menu with Change my password and Sign out.
- **E3 Change my password**: old password, new one, confirmation.
- **E4 Accounts** (ADMIN): list of accounts (name, e-mail, role, active), New account button, Edit and Deactivate /
  Reactivate actions.
- **E5 Account dialog** (ADMIN): first name, last name, e-mail (cannot be changed after creation), role, initial
  password (creation only).

## 5. Acceptance criteria
- **CA-03-01** — Given an active account, When the user enters their correct e-mail and password, Then they receive
  an access token and a refresh token and land on My journal.
- **CA-03-02** — Given an active account, When the password is wrong, Then the response is 401 and the screen shows
  "Invalid credentials".
- **CA-03-03** — Given an unknown e-mail, When a sign-in is attempted, Then the response is exactly the same as for
  CA-03-02.
- **CA-03-04** — Given an inactive account, When the user tries to sign in with the right password, Then the response
  is 401 "Invalid credentials".
- **CA-03-05** — Given a user who is not signed in, When they open a protected page, Then they are redirected to
  `/login`.
- **CA-03-06** — Given an expired access token and a valid refresh token, When the user performs an action, Then the
  session is renewed without them noticing and the action succeeds.
- **CA-03-07** — Given a refresh token already used, When it is presented again, Then the response is 401.
- **CA-03-08** — Given a signed-in user, When they sign out, Then their refresh token is refused (401) and they are
  redirected to `/login`.
- **CA-03-09** — Given a USER, When they call the account management API, Then the response is 403; and the Accounts
  menu is not shown.
- **CA-03-10** — Given an ADMIN, When they create an account with an e-mail already in use (whatever the case), Then
  the API answers 409 with the code RG-03-01.
- **CA-03-11** — Given an ADMIN, When they create an account with an 8-character password, Then the field is in error
  and saving is blocked.
- **CA-03-12** — Given an ADMIN, When they try to deactivate their own account, Then the API answers 409 with the code
  RG-03-13.
- **CA-03-13** — Given a signed-in user whose account has just been deactivated, When their access token expires,
  Then the renewal fails (401) and they are redirected to `/login`.
- **CA-03-14** — Given a signed-in user, When they change their password with a wrong old password, Then the field is
  in error and the password is not changed.
- **CA-03-15** — Given an API response describing an account, Then it never contains the password nor its hash.

## 6. Out of scope
- Self sign-up, forgotten password / reset by e-mail, sending e-mails.
- Locking the account after several failures, two-factor authentication, SSO.
- Teams and linking a user to a manager, covered by SFD-05.
- Permanent deletion of an account (only deactivation exists).
