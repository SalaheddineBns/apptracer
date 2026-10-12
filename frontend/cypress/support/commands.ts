type TestRole = 'USER' | 'MANAGER' | 'ADMIN';

declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Sélectionne un élément par son attribut `data-cy`. */
      getCy(selector: string): Chainable<JQuery<HTMLElement>>;
      /** Ouvre une session par API pour le rôle donné (identifiants `user_<ROLE>` de cypress.env.json). */
      login(role: TestRole): Chainable<void>;
    }
  }
}

Cypress.Commands.add('getCy', (selector: string) => cy.get(`[data-cy="${selector}"]`));

// Clés de localStorage identiques à celles de AuthStore (core/auth/auth.store.ts).
Cypress.Commands.add('login', (role: TestRole) => {
  cy.session(role, () => {
    // Cypress 16 a supprimé Cypress.env() : les identifiants (sensibles) se lisent par cy.env().
    cy.env([`user_${role}`]).then((env) => {
      cy.request('POST', '/api/v1/auth/login', env[`user_${role}`]).then(({ body }) => {
        window.localStorage.setItem('accessToken', body.accessToken);
        window.localStorage.setItem('refreshToken', body.refreshToken);
      });
    });
  });
});

export {};
