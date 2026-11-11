# Contributing to AppTracer

Thanks for your interest in AppTracer. This guide explains how to set up the project, how changes are organised and
what a pull request needs before it can be merged.

## Set up the project

Follow [Getting started](README.md#getting-started) in the README: Docker for the database, Java 21 for the API and
Node.js 22.22+ or 24.15+ for the Angular app. Create your own `.env` file at the root of the repository with the
variables listed in the README; it stays on your machine and is never committed.

To try the app with data, set `DEMO_PASSWORD` in your `.env`: at start-up the API creates a manager, a developer and a
QA account, their team and two weeks of sample entries.

## Understand the rules

The functional specifications are in [`docs/sfd/`](docs/sfd/00-vue-ensemble.md): start with the overview. Each business
rule (`RG-xx-yy`) and acceptance criterion (`CA-xx-yy`) cited in the code or in a test name is defined there, so you can
look up why the code behaves as it does before changing it. A change of behaviour starts with a change of the
specification.

## Report a bug or suggest a feature

Open an issue and include:

- what you did, what you expected and what happened instead;
- the role you were signed in with (USER, MANAGER or ADMIN) and the screen's address;
- the error message or a screenshot, without any password or token.

For a new feature, describe the need first: rules are agreed before any code is written.

## Workflow

1. **Branch** from `main`, one branch per feature: `feat/<short-name>` (for example `feat/projects-per-team`), or
   `fix/<short-name>` for a bug.
2. **Commit** one task per commit, with a one-line message in English:
   `type(scope): summary`, for example `feat(projects): team column in the projects list`. Types: `feat`, `fix`,
   `docs`, `test`, `chore`, `refactor`.
3. **Open a pull request** to `main`; its template lists what to describe and check. For a screen change, add a
   screenshot in light and dark mode. The CI runs the backend and frontend checks on every pull request.

New to the project? Look for issues labelled **good first issue**.

## Coding conventions

### General

- Code, identifiers, comments and commit messages are in **English**. This includes the description of a business
  rule next to its ID (`RG-10-04: a new project must belong to one of the author's teams`). Parts of the existing code
  still describe rules in French: write new comments in English, and translate the ones you touch.
- Every text shown to users exists in **French and English**: nothing is hard-coded. Front-end texts go in
  `frontend/src/app/core/i18n/fr.ts` and `en.ts`; back-end messages go in `messages_fr.properties` and
  `messages_en.properties`.
- Document classes and methods with **Javadoc** (back end) or **JSDoc** (front end) rather than line comments. When
  code implements a business rule, its documentation cites the rule ID and describes the rule in English, for example
  `RG-10-04`.
- A new Maven or npm dependency is explained in the commit that adds it.

### Back end (Spring Boot)

- REST endpoints live under `/api/v1`, with plural, kebab-case resources (`/api/v1/time-entries`).
- Every endpoint declares its `operationId` and its error responses for OpenAPI.
- A violated business rule returns **409** with the rule ID in `code`; the server checks every right and every rule,
  whatever the screen allows.
- **Never edit a committed Flyway migration**: add a new `V{n+1}__description.sql`.
- Format the code before committing: `cd backend && ./mvnw spotless:apply`.

### Front end (Angular)

- Standalone components with `OnPush`, signals and `inject()`.
- Pages go through their feature's store, never through the generated API client directly.
- Use PrimeNG components and `tailwindcss-primeui` colour tokens only: no raw colours, no custom CSS.
- Every screen handles its four states: loading, empty, error (with a retry button) and loaded.
- Add a `data-cy` attribute in kebab-case to every element a test refers to.

### API contract

The front-end client is generated from the OpenAPI contract and is **never edited by hand**
(`frontend/src/app/core/api/generated/`). After changing an endpoint or a DTO:

```bash
cd backend && ./mvnw test -Dtest=OpenApiExportTest   # updates docs/api/openapi.json
cd frontend && npm run api:generate                 # regenerates the client
```

Check the contract diff, then commit `docs/api/openapi.json` and the generated client together. Removing or renaming
a field is a breaking change: say so in the commit message.

## Tests

Each acceptance criterion has at least one test whose name cites its ID, for example
`@DisplayName("CA-10-03 : …")` in Java or `it('CA-10-03 : …')` in Angular.

Before opening a pull request, all of these must pass:

| Check | Command |
|---|---|
| Backend tests | `cd backend && ./mvnw test` |
| Frontend tests | `cd frontend && npx ng test --watch=false` |
| Frontend lint | `cd frontend && npx ng lint` |
| Frontend build | `cd frontend && npx ng build` |

The backend tests start a real PostgreSQL with Testcontainers, so Docker must be running.

## Security

Never commit a secret: passwords, tokens, the PrimeUI licence key or a `.env` file. If you find a security issue,
do not open a public issue: follow [`SECURITY.md`](SECURITY.md) and write to benkhanoussalaheddine@gmail.com.

## License

By contributing, you agree that your contributions are released under the [MIT License](LICENSE) of the project.
