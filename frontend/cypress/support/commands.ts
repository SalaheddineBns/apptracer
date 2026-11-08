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

// Langue de l'interface (RG-08-01) : sans choix explicite, les tests s'exécutent en français (clé `lang`
// de localStorage, posée avant le chargement de l'application). Le navigateur de Cypress est en anglais ;
// le fichier SFD-08 désactive ce défaut avec `setDefaultLanguage(null)` pour tester le choix de langue lui-même.
const defaults = globalThis as unknown as { __e2eDefaultLang?: string | null };
defaults.__e2eDefaultLang = 'fr';

/** Fixe la langue posée par défaut avant chaque chargement de page (`null` : ne rien poser). */
export function setDefaultLanguage(lang: 'fr' | 'en' | null): void {
  defaults.__e2eDefaultLang = lang;
}

Cypress.on('window:before:load', (win) => {
  const lang = defaults.__e2eDefaultLang;
  if (lang && !win.localStorage.getItem('lang')) {
    win.localStorage.setItem('lang', lang);
  }
});

export {};
