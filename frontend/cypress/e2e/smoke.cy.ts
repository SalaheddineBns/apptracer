describe('Socle e2e', () => {
  it("charge l'application", () => {
    cy.visit('/');
    cy.get('app-root').should('exist');
  });

  it('USER : se connecte par API et arrive sur le journal', () => {
    cy.login('USER');
    cy.visit('/journal');
    cy.getCy('titre-journal').should('be.visible');
  });

  it('MANAGER : se connecte par API et arrive sur le journal', () => {
    cy.login('MANAGER');
    cy.visit('/journal');
    cy.getCy('titre-journal').should('be.visible');
  });

  it('ADMIN : se connecte par API et arrive sur la liste des comptes', () => {
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    cy.getCy('table-comptes').should('be.visible');
  });
});
