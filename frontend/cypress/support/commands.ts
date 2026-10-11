declare global {
  // eslint-disable-next-line @typescript-eslint/no-namespace
  namespace Cypress {
    interface Chainable {
      /** Sélectionne un élément par son attribut `data-cy`. */
      getCy(selector: string): Chainable<JQuery<HTMLElement>>;
    }
  }
}

Cypress.Commands.add('getCy', (selector: string) => cy.get(`[data-cy="${selector}"]`));

export {};
