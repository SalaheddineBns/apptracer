// SFD-07 : temps total de la journée. Un it() par critère d'acceptation (USER, et MANAGER pour CA-07-07, RG-07-08).
//
// Isolation : chaque test crée par l'API son compte (e-mail unique) et ses saisies. Semaine du lundi 05/10/2026.
// État partagé : la journée standard vaut 480 à l'installation (RG-06-01) ; CA-07-06 la modifie par l'API ADMIN, `afterEach` la rétablit à 480.

// Module : évite les collisions de types globaux avec les autres fichiers de test.
export {};

type EntryRole = 'USER' | 'MANAGER';
interface Credentials {
  email: string;
  password: string;
}
interface Entry {
  id: number;
}
interface Settings {
  standardDayMinutes: number;
  version: number;
}

const ENTRIES_URL = '/api/v1/time-entries';
const SETTINGS_URL = '/api/v1/app-settings';
const WEEK = '2026-10-05';
const DEFAULT_MINUTES = 480;
const PASSWORD = 'Total-Test-2026';
const DAYS = ['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11'];
const MORNING = {
  granularity: 'HOUR',
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityTypeCode: 'REUNION',
};
const AFTERNOON = { ...MORNING, startTime: '14:00', endTime: '16:00' };

const runId = Math.random().toString(36).slice(2, 8);
let sequence = 0;

function unique(): string {
  sequence += 1;
  return `${runId}${sequence}`;
}

function bearer(token: string): { Authorization: string } {
  return { Authorization: `Bearer ${token}` };
}

function tokenOf(credentials: Credentials): Cypress.Chainable<string> {
  return cy
    .request('POST', '/api/v1/auth/login', { email: credentials.email, password: credentials.password })
    .then((response) => response.body.accessToken as string);
}

function adminToken(): Cypress.Chainable<string> {
  return cy.env(['user_ADMIN']).then((env) => tokenOf(env['user_ADMIN'] as Credentials));
}

/** Compte neuf, propre au test : ses saisies ne chevauchent celles d'aucun autre test (RG-01-06). */
function createAccount(role: EntryRole): Cypress.Chainable<Credentials> {
  const email = `total.${role.toLowerCase()}.${unique()}@example.com`;
  const body = { email, firstName: 'Tara', lastName: `E2E-${role}`, role, initialPassword: PASSWORD };
  return adminToken().then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/users', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return { email, password: PASSWORD };
    }),
  );
}

/** Session ouverte par API pour un compte créé par le test (mêmes clés de localStorage que `cy.login`). */
function loginAs(account: Credentials): void {
  cy.session(account.email, () => {
    cy.request('POST', '/api/v1/auth/login', { email: account.email, password: account.password }).then(
      ({ body }) => {
        window.localStorage.setItem('accessToken', body.accessToken);
        window.localStorage.setItem('refreshToken', body.refreshToken);
      },
    );
  });
}

function createEntry(account: Credentials, body: object): Cypress.Chainable<Entry> {
  return tokenOf(account).then((token) =>
    cy.request({ method: 'POST', url: ENTRIES_URL, headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body as Entry;
    }),
  );
}

function setStandardDay(minutes: number): void {
  adminToken().then((token) =>
    cy.request({ url: SETTINGS_URL, headers: bearer(token) }).then((current) => {
      const settings = current.body as Settings;
      cy.request({
        method: 'PUT',
        url: SETTINGS_URL,
        headers: bearer(token),
        body: { standardDayMinutes: minutes, version: settings.version },
      })
        .its('status')
        .should('eq', 200);
    }),
  );
}

/** Ouvre « Mon journal » sur la semaine du 05/10/2026 et attend ses saisies. */
function openJournal(account: Credentials): void {
  loginAs(account);
  cy.intercept('GET', `${ENTRIES_URL}?*`).as('week');
  cy.visit(`/journal?semaine=${WEEK}`);
  cy.wait('@week').its('response.statusCode').should('eq', 200);
  cy.getCy('titre-journal').should('be.visible');
}

function dayTotal(day: string): Cypress.Chainable<JQuery<HTMLElement>> {
  return cy.getCy(`total-jour-${day}`);
}

/** Vérifie les totaux des sept jours ; un jour absent de `expected` doit afficher « – ». */
function expectDayTotals(expected: Record<string, string>): void {
  DAYS.forEach((day) => dayTotal(day).should('have.text', expected[day] ?? '–'));
}

describe('SFD-07 : temps total de la journée', () => {
  afterEach(() => {
    setStandardDay(DEFAULT_MINUTES);
  });

  it('CA-07-01 : un USER ayant saisi 09:00–10:30 et 14:00–16:00 le lundi 05/10/2026 voit 3,5 h ce jour-là', () => {
    createAccount('USER').then((account) => {
      createEntry(account, MORNING);
      createEntry(account, AFTERNOON);
      openJournal(account);
      expectDayTotals({ '2026-10-05': '3,5 h' });
      cy.getCy('jour-2026-10-05').find('.sr-only').should('contain', 'Total du lundi : 3,5 h');
    });
  });

  it('CA-07-02 : une PLAGE du jeudi 08/10 au mardi 13/10/2026 affiche 8 h le jeudi et le vendredi, « – » le week-end', () => {
    createAccount('USER').then((account) => {
      createEntry(account, {
        granularity: 'RANGE',
        startDate: '2026-10-08',
        endDate: '2026-10-13',
        activityTypeCode: 'FORMATION',
      });
      openJournal(account);
      expectDayTotals({ '2026-10-08': '8 h', '2026-10-09': '8 h' });
      cy.getCy('total-semaine').should('have.text', '16 h');
    });
  });

  it('CA-07-03 : une JOURNEE le samedi 10/10/2026 affiche 8 h le samedi', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { granularity: 'DAY', startDate: '2026-10-10', activityTypeCode: 'FORMATION' });
      openJournal(account);
      expectDayTotals({ '2026-10-10': '8 h' });
    });
  });

  it('CA-07-04 : supprimer la saisie 09:00–10:30 d’un lundi à 3,5 h ramène le lundi et la semaine à 2 h, sans rechargement', () => {
    createAccount('USER').then((account) => {
      createEntry(account, MORNING).then((entry) => {
        createEntry(account, AFTERNOON);
        openJournal(account);
        dayTotal('2026-10-05').should('have.text', '3,5 h');
        cy.getCy('total-semaine').should('have.text', '3,5 h');
        // Marqueur posé sur window : il disparaîtrait si la page était rechargée.
        cy.window().then((win) => {
          (win as unknown as { noReload: boolean }).noReload = true;
        });
        cy.intercept('DELETE', `${ENTRIES_URL}/${entry.id}`).as('remove');
        cy.getCy(`btn-supprimer-saisie-${entry.id}`).find('button').click();
        cy.getCy('btn-confirmer').click();
        cy.wait('@remove').its('response.statusCode').should('eq', 204);
        dayTotal('2026-10-05').should('have.text', '2 h');
        cy.getCy('total-semaine').should('have.text', '2 h');
        cy.window().its('noReload').should('eq', true);
      });
    });
  });

  it('CA-07-05 : 90 min le lundi et une JOURNEE le mardi donnent 1,5 h, 8 h, « – » ailleurs et 9,5 h pour la semaine', () => {
    createAccount('USER').then((account) => {
      createEntry(account, MORNING);
      createEntry(account, { granularity: 'DAY', startDate: '2026-10-06', activityTypeCode: 'FORMATION' });
      openJournal(account);
      expectDayTotals({ '2026-10-05': '1,5 h', '2026-10-06': '8 h' });
      cy.getCy('total-semaine').should('have.text', '9,5 h');
    });
  });

  it('CA-07-06 : une JOURNEE enregistrée à 480 min affiche toujours 8 h après le passage de la journée standard à 450', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { granularity: 'DAY', startDate: '2026-10-06', activityTypeCode: 'FORMATION' });
      setStandardDay(450);
      openJournal(account);
      expectDayTotals({ '2026-10-06': '8 h' });
      cy.getCy('total-semaine').should('have.text', '8 h');
    });
  });

  it('CA-07-07 : un MANAGER voit le temps total de chacun de ses jours dans son propre journal', () => {
    createAccount('MANAGER').then((account) => {
      createEntry(account, MORNING);
      createEntry(account, { granularity: 'DAY', startDate: '2026-10-07', activityTypeCode: 'FORMATION' });
      openJournal(account);
      expectDayTotals({ '2026-10-05': '1,5 h', '2026-10-07': '8 h' });
      cy.getCy('jour-2026-10-07').find('.sr-only').should('contain', 'Total du mercredi : 8 h');
    });
  });

  it('CA-07-08 : une saisie de 09:00 à 09:15 le lundi affiche 0,25 h', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { ...MORNING, endTime: '09:15' });
      openJournal(account);
      expectDayTotals({ '2026-10-05': '0,25 h' });
    });
  });
});
