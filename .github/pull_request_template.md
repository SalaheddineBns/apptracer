## What and why

<!-- What does this pull request change, and why? Link the issue: "Closes #123". -->

## How it was tested

<!-- Tests added or updated, and how you checked the change by hand. -->

## Screenshots

<!-- For a screen change: light and dark mode. Delete this section otherwise. -->

## Checklist

- [ ] Backend tests pass (`cd backend && ./mvnw test`) and the code is formatted (`./mvnw spotless:apply`)
- [ ] Frontend tests, lint and build pass (`npx ng test --watch=false`, `npx ng lint`, `npx ng build`)
- [ ] Every new user-facing text exists in French and English
- [ ] Comments are Javadoc / JSDoc in English, citing the rule ID when relevant
- [ ] After an API change: contract exported and client regenerated, committed together
- [ ] No committed Flyway migration was edited
