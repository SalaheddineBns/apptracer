describe('Squelette technique', () => {
  it("charge l'application", () => {
    cy.visit('/');
    cy.get('app-root').should('exist');
  });
});
