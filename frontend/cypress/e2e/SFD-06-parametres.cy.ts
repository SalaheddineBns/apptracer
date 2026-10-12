// SFD-06 : paramètres (journée standard). Un it() par critère d'acceptation couvert ici.
// CA-06-03 et CA-06-04 exigent des saisies d'activité (SFD-01) : reportés à T-01-17, rien n'est simulé.
//
// État partagé : la journée standard est UNE valeur pour toute l'application, et la base de la pile est commune à
// tous les fichiers de test. Tout test qui la modifie la rétablit à 420 dans `afterEach`, par l'API avec le jeton
// ADMIN (version courante lue d'abord). `afterEach` s'exécute aussi après un échec. Restent non rétablissables :
// la version et la trace de dernière modification (auteur, date), que la RG-06-05 conserve.
//
// CA-06-01 (« installation neuve → 420 ») ne dépend pas de l'ordre : il est écrit pour être vrai dans une base
// neuve comme dans une base où un autre test a modifié puis rétabli la valeur. Il prouve que la lecture renvoie 420
// pour les trois rôles (donc que la valeur par défaut posée par la migration V4, ou rétablie à 420, est bien servie) ;
// il ne peut pas prouver, dans une base déjà modifiée, que 420 vient de la migration. Quand la trace est vide
// (cas d'une pile neuve, car ce test est le premier du fichier), il vérifie en plus que la valeur n'a jamais été
// touchée et que l'écran affiche « Jamais modifiée depuis l'installation. ».

// Module : évite les collisions de types globaux avec les autres fichiers de test.
export {};

type Role = 'USER' | 'MANAGER' | 'ADMIN';
interface Credentials {
  email: string;
  password: string;
}
interface Settings {
  standardDayMinutes: number;
  version: number;
  lastModifiedAt?: string | null;
  lastModifiedBy?: { id: number; firstName: string; lastName: string } | null;
}

const SETTINGS_URL = '/api/v1/app-settings';
const DEFAULT_MINUTES = 420;
const ADMIN_NAME = 'Ada E2E-ADMIN'; // ADMIN_FIRST_NAME / ADMIN_LAST_NAME de scripts/e2e-stack.mjs

function credentials(role: Role): Cypress.Chainable<Credentials> {
  return cy.env([`user_${role}`]).then((env) => env[`user_${role}`] as Credentials);
}

function tokenFor(role: Role): Cypress.Chainable<string> {
  return credentials(role).then((c) =>
    cy.request('POST', '/api/v1/auth/login', c).then((response) => response.body.accessToken as string),
  );
}

function bearer(token: string): { Authorization: string } {
  return { Authorization: `Bearer ${token}` };
}

function readSettings(token: string): Cypress.Chainable<Settings> {
  return cy.request({ url: SETTINGS_URL, headers: bearer(token) }).then((response) => {
    expect(response.status).to.eq(200);
    return response.body as Settings;
  });
}

function writeSettings(token: string, standardDayMinutes: number, version: number): Cypress.Chainable<Cypress.Response<Settings>> {
  return cy.request({
    method: 'PUT',
    url: SETTINGS_URL,
    headers: bearer(token),
    body: { standardDayMinutes, version },
    failOnStatusCode: false,
  });
}

/** Rétablit 420 (idempotent : une valeur identique est sans effet, RG-06-06). */
function restoreDefault(): void {
  tokenFor('ADMIN').then((token) =>
    readSettings(token).then((current) => {
      writeSettings(token, DEFAULT_MINUTES, current.version).its('status').should('eq', 200);
    }),
  );
}

/** Heure de Paris, au format de l'écran (formatParisDateTime) : « 12/10/2026 à 14:05 ». */
function parisDateTime(iso: string): string {
  const parts = new Intl.DateTimeFormat('fr-FR', {
    timeZone: 'Europe/Paris',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
  }).formatToParts(new Date(iso));
  const get = (type: string) => parts.find((p) => p.type === type)?.value;
  return `${get('day')}/${get('month')}/${get('year')} à ${get('hour')}:${get('minute')}`;
}

function openSettingsAsAdmin(): void {
  cy.intercept('GET', SETTINGS_URL).as('load');
  cy.login('ADMIN');
  cy.visit('/admin/parametres');
  cy.wait('@load').its('response.statusCode').should('eq', 200);
  cy.getCy('form-parametres').should('be.visible');
}

/** Saisit les minutes dans le p-inputnumber (vider, taper, quitter le champ pour valider la valeur). */
function typeMinutes(value: string): void {
  cy.getCy('champ-minutes').clear();
  cy.getCy('champ-minutes').type(value);
  cy.getCy('champ-minutes').blur();
}

function clickSave(): void {
  cy.getCy('btn-enregistrer').find('button').click();
}

describe('SFD-06 : paramètres (journée standard)', () => {
  afterEach(() => {
    restoreDefault();
  });

  it('CA-06-01 : une installation neuve renvoie 420 minutes à un utilisateur connecté (USER, MANAGER, ADMIN)', () => {
    (['USER', 'MANAGER', 'ADMIN'] as Role[]).forEach((role) => {
      tokenFor(role).then((token) =>
        readSettings(token).then((settings) => {
          expect(settings.standardDayMinutes, `journée standard lue par ${role}`).to.eq(420);
        }),
      );
    });
    // Trace vide = base jamais modifiée (installation neuve) : l'écran ADMIN le dit.
    tokenFor('ADMIN').then((token) =>
      readSettings(token).then((settings) => {
        cy.intercept('GET', SETTINGS_URL).as('load');
        cy.login('ADMIN');
        cy.visit('/admin/parametres');
        cy.wait('@load');
        cy.getCy('form-parametres').should('be.visible');
        cy.getCy('champ-heures').should('have.value', '7 h');
        cy.getCy('champ-minutes').should('have.value', '0 min');
        if (!settings.lastModifiedAt) {
          expect(settings.lastModifiedBy ?? null).to.eq(null);
          expect(settings.version).to.eq(0);
          cy.getCy('derniere-modification').should('contain', 'Jamais modifiée depuis l’installation.');
        } else {
          cy.getCy('derniere-modification').should('contain', 'Dernière modification');
        }
      }),
    );
  });

  it('CA-06-02 : l\'ADMIN enregistre 7 h 30, la journée standard vaut 450 minutes et l\'écran affiche son nom et la date', () => {
    openSettingsAsAdmin();
    cy.intercept('PUT', SETTINGS_URL).as('save');
    cy.getCy('nav-parametres').should('be.visible');
    cy.getCy('aide-parametres').should('contain', 'ne sont pas recalculées');
    // Valeur identique à celle du back : bouton inactif (RG-06-06).
    cy.getCy('btn-enregistrer').find('button').should('be.disabled');

    typeMinutes('30');
    cy.getCy('champ-heures').should('have.value', '7 h');
    cy.getCy('champ-minutes').should('have.value', '30 min');
    cy.getCy('btn-enregistrer').find('button').should('be.enabled');
    clickSave();

    cy.wait('@save').then(({ request, response }) => {
      expect(request.body.standardDayMinutes).to.eq(450);
      expect(response?.statusCode).to.eq(200);
      expect(response?.body.standardDayMinutes).to.eq(450);
      expect(response?.body.lastModifiedBy.firstName).to.eq('Ada');
      const savedAt = response?.body.lastModifiedAt as string;
      // La date tracée est celle du jour (à quelques minutes près), jamais une date de la requête.
      expect(Math.abs(Date.now() - Date.parse(savedAt))).to.be.lessThan(5 * 60 * 1000);
      cy.getCy('derniere-modification').should('contain', `Dernière modification : ${ADMIN_NAME}, le ${parisDateTime(savedAt)}`);
    });
    cy.getCy('toast').should('contain', 'Paramètres enregistrés');
    cy.getCy('btn-enregistrer').find('button').should('be.disabled');

    // Persistance : la valeur est 450 pour tous les rôles, la trace n'est visible que de l'ADMIN.
    (['USER', 'MANAGER'] as Role[]).forEach((role) => {
      tokenFor(role).then((token) =>
        readSettings(token).then((settings) => {
          expect(settings.standardDayMinutes).to.eq(450);
          expect(settings.lastModifiedAt ?? null).to.eq(null);
          expect(settings.lastModifiedBy ?? null).to.eq(null);
        }),
      );
    });
    cy.reload();
    cy.getCy('champ-minutes').should('have.value', '30 min');
    cy.getCy('derniere-modification').should('contain', ADMIN_NAME);
  });

  it('CA-06-05 : 7 h 10 met le champ en erreur et bloque l\'enregistrement, aucune requête PUT', () => {
    openSettingsAsAdmin();
    cy.intercept('PUT', SETTINGS_URL).as('save');
    typeMinutes('10');
    cy.getCy('champ-minutes').should('have.value', '10 min');
    clickSave();
    cy.getCy('erreur-duree').should('be.visible').and('contain', 'pas de 15 minutes');
    cy.getCy('champ-minutes').should('have.attr', 'aria-invalid', 'true');
    cy.getCy('form-parametres').should('be.visible');
    cy.get('@save.all').should('have.length', 0);
    // Rien n'a été écrit côté back.
    tokenFor('ADMIN').then((token) => readSettings(token).its('standardDayMinutes').should('eq', 420));
  });

  it('CA-06-06 : 30 minutes envoyées par l\'API donnent 400 avec le champ en erreur', () => {
    tokenFor('ADMIN').then((token) =>
      readSettings(token).then((current) => {
        writeSettings(token, 30, current.version).then((response) => {
          expect(response.status).to.eq(400);
          const fields = (response.body as unknown as { errors: { field: string }[] }).errors.map((e) => e.field);
          expect(fields).to.include('standardDayMinutes');
        });
        readSettings(token).its('standardDayMinutes').should('eq', 420);
      }),
    );
  });

  it('CA-06-07 : USER et MANAGER reçoivent 403 sur la modification et ne voient pas le menu « Paramètres »', () => {
    (['USER', 'MANAGER'] as Role[]).forEach((role) => {
      tokenFor(role).then((token) =>
        readSettings(token).then((current) => {
          writeSettings(token, 450, current.version).its('status').should('eq', 403);
          readSettings(token).its('standardDayMinutes').should('eq', 420); // rien modifié, la lecture reste permise
        }),
      );
      cy.login(role);
      cy.visit('/journal');
      cy.getCy('titre-journal').should('be.visible');
      cy.getCy('nav-mon-journal').should('be.visible');
      cy.getCy('nav-parametres').should('not.exist');
    });
    // Contre-épreuve : l'ADMIN voit le menu.
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    cy.getCy('nav-parametres').should('be.visible');
  });

  it('CA-06-08 : sans jeton, l\'API des paramètres répond 401 en lecture et en écriture', () => {
    cy.request({ url: SETTINGS_URL, failOnStatusCode: false }).its('status').should('eq', 401);
    cy.request({ method: 'PUT', url: SETTINGS_URL, body: { standardDayMinutes: 450, version: 0 }, failOnStatusCode: false })
      .its('status')
      .should('eq', 401);
    cy.request({ url: SETTINGS_URL, headers: bearer('invalide.jeton.expire'), failOnStatusCode: false }).its('status').should('eq', 401);
  });

  // Hors critères d'acceptation : preuves e2e des comportements décrits dans la matrice (RG-06-06, conflit).
  it('RG-06-06 : une valeur identique est sans effet et non tracée ; version périmée : 409', () => {
    tokenFor('ADMIN').then((token) =>
      readSettings(token).then((before) => {
        writeSettings(token, before.standardDayMinutes, before.version).then((same) => {
          expect(same.status).to.eq(200);
          expect(same.body.version).to.eq(before.version);
          expect(same.body.lastModifiedAt ?? null).to.eq(before.lastModifiedAt ?? null);
          expect(same.body.lastModifiedBy?.id ?? null).to.eq(before.lastModifiedBy?.id ?? null);
        });
        // Valeur identique mais version périmée : le conflit prime.
        writeSettings(token, before.standardDayMinutes, before.version + 100).its('status').should('eq', 409);
      }),
    );
  });

  it('Conflit : un autre ADMIN a modifié la valeur entre-temps, 409 et message de conflit', () => {
    openSettingsAsAdmin();
    cy.intercept('PUT', SETTINGS_URL).as('save');
    // Modification concurrente par l'API, après le chargement de l'écran (version désormais périmée côté écran).
    tokenFor('ADMIN').then((token) =>
      readSettings(token).then((current) => writeSettings(token, 480, current.version).its('status').should('eq', 200)),
    );
    typeMinutes('30');
    clickSave();
    cy.wait('@save').its('response.statusCode').should('eq', 409);
    cy.getCy('message-conflit').should('be.visible').and('contain', 'Un autre administrateur');
    cy.getCy('champ-heures').should('have.value', '8 h');
    cy.getCy('champ-minutes').should('have.value', '0 min');
  });
});
