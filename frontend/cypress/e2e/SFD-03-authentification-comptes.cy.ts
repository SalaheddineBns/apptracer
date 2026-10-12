// SFD-03 : authentification et comptes. Un it() par critère d'acceptation (CA-03-01 à CA-03-15).
//
// Isolation : les tests qui modifient l'état d'un compte (désactivation, mot de passe, rotation ou
// révocation de refresh token) utilisent des comptes dédiés créés par l'API, jamais les comptes partagés
// user_USER / user_MANAGER / user_ADMIN (utilisés seulement en lecture, via cy.login ou une connexion).

type Role = 'USER' | 'MANAGER' | 'ADMIN';
interface Credentials {
  email: string;
  password: string;
}
interface Account extends Credentials {
  id: number;
  role: Role;
}
interface Tokens {
  accessToken: string;
  refreshToken: string;
  user: { id: number; role: Role };
}

const ROLES: Role[] = ['USER', 'MANAGER', 'ADMIN'];
const HOME: Record<Role, string> = { USER: '/journal', MANAGER: '/journal', ADMIN: '/admin/comptes' };
const DEDICATED_PASSWORD = 'Dedie-Test-2026';
const NEW_PASSWORD = 'Nouveau-Test-2026';
const INVALID_JWT = 'invalide.jeton.expire';

const runId = Math.random().toString(36).slice(2, 8);
let sequence = 0;

function sharedCredentials(role: Role): Cypress.Chainable<Credentials> {
  return cy.env([`user_${role}`]).then((env) => env[`user_${role}`] as Credentials);
}

function apiLogin(credentials: Credentials): Cypress.Chainable<Cypress.Response<Tokens>> {
  return cy.request({ method: 'POST', url: '/api/v1/auth/login', body: credentials, failOnStatusCode: false });
}

function adminToken(): Cypress.Chainable<string> {
  return sharedCredentials('ADMIN')
    .then((credentials) => apiLogin(credentials))
    .then((response) => {
      expect(response.status).to.eq(200);
      return response.body.accessToken;
    });
}

/** Crée un compte dédié (e-mail unique par exécution et par appel) avec le jeton de l'ADMIN. */
function createAccount(role: Role, body: Record<string, unknown> = {}): Cypress.Chainable<Account> {
  sequence += 1;
  const email = `dedie-${runId}-${sequence}@example.com`;
  return adminToken().then((token) =>
    cy
      .request({
        method: 'POST',
        url: '/api/v1/users',
        headers: { Authorization: `Bearer ${token}` },
        body: { email, firstName: 'Dedie', lastName: `Zz-${role}-${sequence}`, role, initialPassword: DEDICATED_PASSWORD, ...body },
      })
      .then((response) => {
        expect(response.status).to.eq(201);
        return { id: response.body.id as number, email, password: DEDICATED_PASSWORD, role, ...body } as Account;
      }),
  );
}

function deactivate(accountId: number): Cypress.Chainable<number> {
  return adminToken().then((token) =>
    cy
      .request({ method: 'POST', url: `/api/v1/users/${accountId}/deactivation`, headers: { Authorization: `Bearer ${token}` } })
      .its('status')
      .should('eq', 200),
  );
}

/** Ouvre `url` avec une session déjà établie par API pour ce compte ; renvoie les jetons obtenus. */
function visitAs(account: Credentials, url: string): Cypress.Chainable<Tokens> {
  return apiLogin(account).then((response) => {
    expect(response.status).to.eq(200);
    const tokens = response.body;
    cy.visit(url, {
      onBeforeLoad(win) {
        win.localStorage.setItem('accessToken', tokens.accessToken);
        win.localStorage.setItem('refreshToken', tokens.refreshToken);
      },
    });
    return cy.wrap(tokens, { log: false });
  });
}

/** Simule l'expiration de l'access token côté navigateur (voir l'explication en tête de CA-03-06). */
function corruptAccessToken(): void {
  cy.window().then((win) => {
    win.localStorage.setItem('accessToken', INVALID_JWT);
  });
}

function loginThroughUi(credentials: Credentials): void {
  cy.visit('/login');
  cy.getCy('champ-email').type(credentials.email);
  cy.getCy('champ-mot-de-passe').type(credentials.password, { log: false });
  cy.getCy('btn-se-connecter').click();
}

function submitPasswordChange(current: string, next: string): void {
  cy.getCy('champ-ancien-mot-de-passe').type(current, { log: false });
  cy.getCy('champ-nouveau-mot-de-passe').type(next, { log: false });
  cy.getCy('champ-confirmation-mot-de-passe').type(next, { log: false });
  cy.getCy('btn-enregistrer').click();
}

function openCreateDialog(): void {
  cy.getCy('btn-nouveau-compte').click();
  // `dialogue-compte` est posé sur l'hôte <p-dialog> (0 x 0 px, jamais « visible ») : on s'appuie sur `form-compte`.
  cy.getCy('form-compte').should('be.visible');
  // Le dialogue donne le focus au prénom à la fin de son animation d'ouverture (`onShow`) : sans cette
  // attente, ce focus tardif vole la saisie en cours (défaut signalé dans la recette SFD-03).
  cy.getCy('champ-prenom').should('be.focused');
}

/** Parcourt récursivement un JSON et échoue si une clé ressemble à un mot de passe ou à un hachage. */
function assertNoSecret(value: unknown, secrets: string[], path = '$'): void {
  const text = JSON.stringify(value);
  secrets.forEach((secret) => expect(text, `${path} ne contient pas "${secret}"`).not.to.contain(secret));
  const walk = (node: unknown, where: string): void => {
    if (Array.isArray(node)) {
      node.forEach((item, i) => walk(item, `${where}[${i}]`));
    } else if (node && typeof node === 'object') {
      Object.entries(node).forEach(([key, child]) => {
        expect(key, `${where}.${key}`).not.to.match(/password|passwd|hash|secret/i);
        walk(child, `${where}.${key}`);
      });
    }
  };
  walk(value, path);
  expect(text).not.to.match(/\$2[aby]\$\d{2}\$/); // préfixe BCrypt
}

describe('SFD-03 : authentification et comptes', () => {
  it('CA-03-01 : connexion avec des identifiants corrects, jetons reçus et arrivée sur l\'accueil du rôle', () => {
    cy.intercept('POST', '/api/v1/auth/login').as('login');
    ROLES.forEach((role) => {
      cy.clearAllLocalStorage();
      sharedCredentials(role).then((credentials) => {
        loginThroughUi(credentials);
        cy.wait('@login').then(({ response }) => {
          expect(response?.statusCode).to.eq(200);
          expect(response?.body.accessToken).to.be.a('string').and.not.be.empty;
          expect(response?.body.refreshToken).to.be.a('string').and.not.be.empty;
          expect(response?.body.user.role).to.eq(role);
        });
        cy.location('pathname').should('eq', HOME[role]);
        cy.window().then((win) => {
          expect(win.localStorage.getItem('accessToken')).to.be.a('string').and.not.be.empty;
          expect(win.localStorage.getItem('refreshToken')).to.be.a('string').and.not.be.empty;
        });
      });
    });
    cy.getCy('table-comptes').should('be.visible'); // dernier rôle : ADMIN sur « Comptes »
  });

  it('CA-03-02 : mot de passe incorrect, 401 et message « Identifiants invalides »', () => {
    cy.intercept('POST', '/api/v1/auth/login').as('login');
    ROLES.forEach((role) => {
      sharedCredentials(role).then((credentials) => {
        loginThroughUi({ ...credentials, password: 'Mauvais-Mot-2026' });
        cy.wait('@login').its('response.statusCode').should('eq', 401);
        cy.getCy('message-erreur-connexion').should('contain', 'Identifiants invalides');
        cy.location('pathname').should('eq', '/login');
      });
    });
  });

  it('CA-03-03 : e-mail inconnu, réponse strictement identique à celle d\'un mot de passe incorrect', () => {
    cy.intercept('POST', '/api/v1/auth/login').as('login');
    loginThroughUi({ email: `inconnu-${runId}@example.com`, password: 'Mauvais-Mot-2026' });
    cy.wait('@login').its('response.statusCode').should('eq', 401);
    cy.getCy('message-erreur-connexion').should('contain', 'Identifiants invalides');

    // Comparaison des réponses brutes : e-mail inconnu contre mot de passe incorrect (compte existant).
    sharedCredentials('USER').then((credentials) => {
      apiLogin({ email: `inconnu-${runId}@example.com`, password: 'Mauvais-Mot-2026' }).then((unknown) => {
        apiLogin({ ...credentials, password: 'Mauvais-Mot-2026' }).then((wrongPassword) => {
          expect(unknown.status).to.eq(401);
          expect(wrongPassword.status).to.eq(401);
          expect(unknown.body).to.deep.eq(wrongPassword.body);
          expect(unknown.headers['content-type']).to.eq(wrongPassword.headers['content-type']);
        });
      });
    });
  });

  it('CA-03-04 : compte inactif avec le bon mot de passe, 401 « Identifiants invalides »', () => {
    cy.intercept('POST', '/api/v1/auth/login').as('login');
    ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        deactivate(account.id);
        loginThroughUi(account);
        cy.wait('@login').its('response.statusCode').should('eq', 401);
        cy.getCy('message-erreur-connexion').should('contain', 'Identifiants invalides');
        cy.location('pathname').should('eq', '/login');
        // Même réponse que pour un mot de passe incorrect (RG-03-05).
        apiLogin(account).then((inactive) => {
          apiLogin({ ...account, password: 'Mauvais-Mot-2026' }).then((wrong) => {
            expect(inactive.status).to.eq(401);
            expect(inactive.body).to.deep.eq(wrong.body);
          });
        });
      });
    });
  });

  it('CA-03-05 : une page protégée sans session redirige vers /login', () => {
    ['/journal', '/admin/comptes', '/compte/mot-de-passe'].forEach((url) => {
      cy.visit(url);
      cy.location('pathname').should('eq', '/login');
      cy.getCy('form-connexion').should('be.visible');
    });
    // Après connexion, retour à la page initialement demandée.
    cy.visit('/compte/mot-de-passe');
    cy.location('pathname').should('eq', '/login');
    sharedCredentials('USER').then((credentials) => {
      cy.getCy('champ-email').type(credentials.email);
      cy.getCy('champ-mot-de-passe').type(credentials.password, { log: false });
      cy.getCy('btn-se-connecter').click();
    });
    cy.location('pathname').should('eq', '/compte/mot-de-passe');
  });

  // CA-03-06 : l'expiration réelle (JWT_ACCESS_TTL, 15 min par défaut) n'est pas attendue. On remplace
  // l'access token stocké par un jeton invalide : le back répond 401 comme pour un jeton expiré et le front
  // ne distingue pas les deux cas. Ce test prouve donc le comportement du front (renouvellement unique, rejeu
  // de la requête, aucune redirection) et l'acceptation du refresh token par le back ; il ne prouve pas que
  // le back date correctement l'expiration d'un JWT valide (couvert par les tests JUnit).
  it('CA-03-06 : access token expiré et refresh token valide, la session est renouvelée et l\'action aboutit', () => {
    // Interceptions déclarées une seule fois : les redéclarer dans la boucle doublerait le comptage des alias.
    cy.intercept('PUT', '/api/v1/auth/me/password').as('change');
    cy.intercept('POST', '/api/v1/auth/refresh').as('refresh');
    ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        visitAs(account, '/compte/mot-de-passe').then((initial) => {
          cy.getCy('form-mot-de-passe').should('be.visible');
          corruptAccessToken();
          submitPasswordChange(account.password, NEW_PASSWORD);
          cy.wait('@change').its('response.statusCode').should('eq', 401);
          cy.wait('@refresh').its('response.statusCode').should('eq', 200);
          cy.wait('@change').its('response.statusCode').should('eq', 204);
          cy.getCy('toast').should('contain', 'Mot de passe modifié');
          cy.location('pathname').should('eq', '/compte/mot-de-passe');
          cy.window().then((win) => {
            expect(win.localStorage.getItem('accessToken')).not.to.eq(INVALID_JWT);
            expect(win.localStorage.getItem('refreshToken')).not.to.eq(initial.refreshToken); // rotation (RG-03-07)
          });
        });
      });
    });
  });

  it('CA-03-07 : un refresh token déjà utilisé est refusé (401)', () => {
    ROLES.forEach((role) => {
      sharedCredentials(role).then((credentials) => {
        apiLogin(credentials).then(({ body: first }) => {
          cy.request('POST', '/api/v1/auth/refresh', { refreshToken: first.refreshToken }).then((renewed) => {
            expect(renewed.status).to.eq(200);
            expect(renewed.body.refreshToken).not.to.eq(first.refreshToken);
            cy.request({ method: 'POST', url: '/api/v1/auth/refresh', body: { refreshToken: first.refreshToken }, failOnStatusCode: false })
              .its('status')
              .should('eq', 401);
          });
        });
      });
    });
  });

  it('CA-03-08 : la déconnexion révoque le refresh token (401) et redirige vers /login', () => {
    cy.intercept('POST', '/api/v1/auth/logout').as('logout');
    ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        visitAs(account, HOME[role]).then((tokens) => {
          cy.getCy('btn-menu-utilisateur').click();
          cy.getCy('menu-se-deconnecter').click();
          cy.wait('@logout').its('response.statusCode').should('eq', 204);
          cy.location('pathname').should('eq', '/login');
          cy.window().then((win) => {
            expect(win.localStorage.getItem('accessToken')).to.be.null;
            expect(win.localStorage.getItem('refreshToken')).to.be.null;
          });
          cy.request({ method: 'POST', url: '/api/v1/auth/refresh', body: { refreshToken: tokens.refreshToken }, failOnStatusCode: false })
            .its('status')
            .should('eq', 401);
        });
      });
    });
  });

  it('CA-03-09 : un USER (et un MANAGER) reçoit 403 sur la gestion des comptes et ne voit pas le menu « Comptes »', () => {
    (['USER', 'MANAGER'] as Role[]).forEach((role) => {
      cy.login(role);
      cy.visit('/journal');
      cy.getCy('titre-journal').should('be.visible');
      cy.getCy('nav-mon-journal').should('be.visible');
      cy.getCy('nav-comptes').should('not.exist');

      sharedCredentials(role).then((credentials) => {
        apiLogin(credentials).then(({ body }) => {
          const headers = { Authorization: `Bearer ${body.accessToken}` };
          const forbidden = (method: string, url: string, requestBody?: Cypress.RequestBody) =>
            cy.request({ method, url, headers, body: requestBody, failOnStatusCode: false }).its('status').should('eq', 403);
          forbidden('GET', '/api/v1/users');
          forbidden('GET', `/api/v1/users/${body.user.id}`);
          forbidden('POST', '/api/v1/users', {
            email: `interdit-${runId}@example.com`,
            firstName: 'Interdit',
            lastName: 'Compte',
            role: 'USER',
            initialPassword: DEDICATED_PASSWORD,
          });
          forbidden('PUT', `/api/v1/users/${body.user.id}`, { firstName: 'A', lastName: 'B', role: 'ADMIN', version: 0 });
          forbidden('POST', `/api/v1/users/${body.user.id}/deactivation`);
          forbidden('POST', `/api/v1/users/${body.user.id}/reactivation`);
        });
      });
    });
    // Contre-épreuve : l'ADMIN voit le menu.
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    cy.getCy('nav-comptes').should('be.visible');
  });

  it('CA-03-10 : e-mail déjà utilisé (quelle que soit la casse), 409 avec le code RG-03-01', () => {
    cy.intercept('POST', '/api/v1/users').as('create');
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    sharedCredentials('USER').then((existing) => {
      openCreateDialog();
      cy.getCy('champ-prenom').type('Doublon');
      cy.getCy('champ-nom').type('Casse');
      cy.getCy('champ-email-compte').type(existing.email.toUpperCase());
      cy.getCy('champ-mot-de-passe-initial').type(DEDICATED_PASSWORD, { log: false });
      cy.getCy('btn-enregistrer').click();
      cy.wait('@create').then(({ response }) => {
        expect(response?.statusCode).to.eq(409);
        expect(response?.body.code).to.eq('RG-03-01');
      });
      cy.getCy('erreur-champ-email-compte').should('be.visible').and('contain', 'e-mail');
      cy.getCy('form-compte').should('be.visible');
    });
  });

  it('CA-03-11 : mot de passe initial de 8 caractères, champ en erreur et enregistrement bloqué', () => {
    cy.intercept('POST', '/api/v1/users').as('create');
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    openCreateDialog();
    cy.getCy('champ-prenom').type('Court');
    cy.getCy('champ-nom').type('MotDePasse');
    cy.getCy('champ-email-compte').type(`court-${runId}@example.com`);
    cy.getCy('champ-mot-de-passe-initial').type('abcd1234', { log: false });
    cy.getCy('btn-enregistrer').click();
    cy.getCy('erreur-champ-mot-de-passe-initial').should('be.visible').and('contain', '12 caractères');
    cy.getCy('form-compte').should('be.visible');
    cy.get('@create.all').should('have.length', 0);

    // Le back refuse aussi : 400 avec l'erreur sur le champ initialPassword.
    adminToken().then((token) => {
      cy.request({
        method: 'POST',
        url: '/api/v1/users',
        headers: { Authorization: `Bearer ${token}` },
        failOnStatusCode: false,
        body: { email: `court-api-${runId}@example.com`, firstName: 'Court', lastName: 'Api', role: 'USER', initialPassword: 'abcd1234' },
      }).then((response) => {
        expect(response.status).to.eq(400);
        expect(response.body.errors.map((e: { field: string }) => e.field)).to.include('initialPassword');
      });
    });
  });

  it('CA-03-12 : un ADMIN ne peut pas désactiver son propre compte (409, RG-03-13)', () => {
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    cy.getCy('indicateur-vous').should('be.visible');
    cy.getCy('table-comptes').find('[data-cy^="btn-desactiver-"]').should('exist'); // les autres comptes restent désactivables

    sharedCredentials('ADMIN').then((credentials) => {
      apiLogin(credentials).then(({ body }) => {
        cy.getCy(`ligne-compte-${body.user.id}`).should('be.visible');
        cy.getCy(`btn-desactiver-${body.user.id}`).should('not.exist'); // pas d'action sur son propre compte
        cy.request({
          method: 'POST',
          url: `/api/v1/users/${body.user.id}/deactivation`,
          headers: { Authorization: `Bearer ${body.accessToken}` },
          failOnStatusCode: false,
        }).then((response) => {
          expect(response.status).to.eq(409);
          expect(response.body.code).to.eq('RG-03-13');
        });
      });
    });
  });

  // CA-03-13 : même principe que CA-03-06 (jeton remplacé par un jeton invalide pour forcer le renouvellement).
  // Ici le renouvellement est réellement refusé par le back (compte désactivé, RG-03-05 et RG-03-14) : le 401
  // du refresh et la redirection sont donc authentiques ; seule l'horloge de l'expiration est simulée.
  it('CA-03-13 : compte désactivé puis access token expiré, le renouvellement échoue (401) et redirection vers /login', () => {
    // Interceptions déclarées une seule fois : les redéclarer dans la boucle doublerait le comptage des alias.
    cy.intercept('PUT', '/api/v1/auth/me/password').as('change');
    cy.intercept('POST', '/api/v1/auth/refresh').as('refresh');
    ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        visitAs(account, '/compte/mot-de-passe');
        cy.getCy('form-mot-de-passe').should('be.visible');
        deactivate(account.id);
        corruptAccessToken();
        submitPasswordChange(account.password, NEW_PASSWORD);
        cy.wait('@change').its('response.statusCode').should('eq', 401);
        cy.wait('@refresh').its('response.statusCode').should('eq', 401);
        cy.location('pathname').should('eq', '/login');
        cy.window().then((win) => {
          expect(win.localStorage.getItem('accessToken')).to.be.null;
          expect(win.localStorage.getItem('refreshToken')).to.be.null;
        });
      });
    });
  });

  it('CA-03-14 : ancien mot de passe incorrect, champ en erreur et mot de passe inchangé', () => {
    cy.intercept('PUT', '/api/v1/auth/me/password').as('change');
    ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        visitAs(account, '/compte/mot-de-passe');
        submitPasswordChange('Mauvais-Ancien-2026', NEW_PASSWORD);
        cy.wait('@change').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-03-15');
        });
        cy.getCy('erreur-champ-ancien-mot-de-passe').should('be.visible').and('contain', 'ancien mot de passe est incorrect');
        cy.location('pathname').should('eq', '/compte/mot-de-passe');
        // Mot de passe inchangé : l'ancien fonctionne toujours, le nouveau non.
        apiLogin(account).its('status').should('eq', 200);
        apiLogin({ email: account.email, password: NEW_PASSWORD }).its('status').should('eq', 401);
      });
    });
  });

  it('CA-03-15 : aucune réponse décrivant un compte ne contient le mot de passe ni son hachage', () => {
    const secrets = [DEDICATED_PASSWORD, NEW_PASSWORD];
    adminToken().then((token) => {
      const headers = { Authorization: `Bearer ${token}` };
      createAccount('USER').then((account) => {
        // Création (le mot de passe initial est dans la requête, jamais dans la réponse).
        sequence += 1;
        cy.request({
          method: 'POST',
          url: '/api/v1/users',
          headers,
          body: {
            email: `cree-${runId}-${sequence}@example.com`,
            firstName: 'Cree',
            lastName: 'Zz-Cree',
            role: 'USER',
            initialPassword: DEDICATED_PASSWORD,
          },
        }).then((created) => {
          expect(created.status).to.eq(201);
          assertNoSecret(created.body, secrets, 'POST /users');
        });
        // Lecture d'un compte, liste, modification, désactivation, réactivation.
        cy.request({ url: `/api/v1/users/${account.id}`, headers }).then((read) => {
          assertNoSecret(read.body, secrets, 'GET /users/{id}');
          cy.request({
            method: 'PUT',
            url: `/api/v1/users/${account.id}`,
            headers,
            body: { firstName: 'Modifie', lastName: read.body.lastName, role: 'MANAGER', version: read.body.version },
          }).then((updated) => assertNoSecret(updated.body, secrets, 'PUT /users/{id}'));
        });
        cy.request({ url: '/api/v1/users?size=100', headers }).then((list) => {
          expect(list.body.content.length).to.be.greaterThan(0);
          assertNoSecret(list.body, secrets, 'GET /users');
        });
        cy.request({ method: 'POST', url: `/api/v1/users/${account.id}/deactivation`, headers }).then((r) =>
          assertNoSecret(r.body, secrets, 'POST deactivation'),
        );
        cy.request({ method: 'POST', url: `/api/v1/users/${account.id}/reactivation`, headers }).then((r) =>
          assertNoSecret(r.body, secrets, 'POST reactivation'),
        );
      });
      // Réponses d'authentification décrivant l'utilisateur courant, pour chaque rôle.
      ROLES.forEach((role) => {
        sharedCredentials(role).then((credentials) => {
          apiLogin(credentials).then((login) => {
            assertNoSecret(login.body.user, [credentials.password], `login ${role} user`);
            cy.request({ url: '/api/v1/auth/me', headers: { Authorization: `Bearer ${login.body.accessToken}` } }).then((me) =>
              assertNoSecret(me.body, [credentials.password], `GET /auth/me ${role}`),
            );
          });
        });
      });
    });
  });
});
