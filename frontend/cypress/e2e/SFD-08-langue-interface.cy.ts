// SFD-08 : langue de l'interface. Un it() par critère d'acceptation (CA-08-01 à CA-08-08).
//
// Les autres fichiers e2e posent `lang = fr` avant chaque chargement (support/commands.ts) ; ici ce défaut est
// retiré pour que la langue vienne du navigateur (CA-08-01) ou du choix explicite de l'utilisateur.
// Isolation : chaque test crée par l'API ses comptes et ses saisies (e-mails uniques). Semaine du lundi 05/10/2026.

import { setDefaultLanguage } from '../support/commands';

type Lang = 'fr' | 'en';
type Role = 'USER' | 'MANAGER' | 'ADMIN';
interface Credentials {
  email: string;
  password: string;
}
interface Account extends Credentials {
  id: number;
}

const ENTRIES_URL = '/api/v1/time-entries';
const WEEK = '2026-10-05';
const PASSWORD = 'Langue-Test-2026';
const DAYS_EN = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const DAYS_FR = ['lundi', 'mardi', 'mercredi', 'jeudi', 'vendredi', 'samedi', 'dimanche'];
const WEEK_DAYS = ['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11'];
const MEETING = { granularity: 'HOUR', startDate: '2026-10-05', startTime: '09:00', endTime: '10:00', activityTypeCode: 'REUNION' };

// Mots d'interface propres à chaque langue (jamais des données saisies : « Réunion » est volontairement absent).
const FRENCH_WORDS =
  /\b(Mon journal|Mon équipe|Nouvelle saisie|Comptes|Équipes|Projets|Paramètres|Enregistrer|Annuler|Se déconnecter|Connexion|Changer mon mot de passe|Types d.activité|Actif|Inactif|Modifier|Créer|Rôle|Chargement)\b/;
const ENGLISH_WORDS =
  /\b(My journal|My team|New time entry|Accounts|Teams|Projects|Settings|Save|Cancel|Sign out|Sign in|Change my password|Activity types|Active|Inactive|Edit|Create|Role|Loading)\b/;

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

function sharedCredentials(role: Role): Cypress.Chainable<Credentials> {
  return cy.env([`user_${role}`]).then((env) => env[`user_${role}`] as Credentials);
}

function createAccount(role: 'USER' | 'MANAGER'): Cypress.Chainable<Account> {
  const email = `langue.${role.toLowerCase()}.${unique()}@example.com`;
  const body = { email, firstName: 'Lena', lastName: `E2E-${role}`, role, initialPassword: PASSWORD };
  return adminToken().then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/users', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return { id: response.body.id as number, email, password: PASSWORD };
    }),
  );
}

function createEntry(account: Credentials, body: object): void {
  tokenOf(account).then((token) => {
    cy.request({ method: 'POST', url: ENTRIES_URL, headers: bearer(token), body }).its('status').should('eq', 201);
  });
}

/** Session API pour un compte créé par le test. */
function loginAs(account: Credentials): void {
  cy.session(account.email, () => {
    cy.request('POST', '/api/v1/auth/login', { email: account.email, password: account.password }).then(({ body }) => {
      window.localStorage.setItem('accessToken', body.accessToken);
      window.localStorage.setItem('refreshToken', body.refreshToken);
    });
  });
}

/** Visite une page avec une langue de navigateur donnée (premier accès : aucun choix en mémoire). */
function visitWithBrowserLanguage(url: string, languages: string[]): void {
  cy.visit(url, {
    onBeforeLoad(win) {
      Object.defineProperty(win.navigator, 'languages', { value: languages, configurable: true });
      Object.defineProperty(win.navigator, 'language', { value: languages[0], configurable: true });
    },
  });
}

/** Visite une page avec une langue déjà choisie. */
function visitIn(url: string, lang: Lang): void {
  cy.visit(url, {
    onBeforeLoad(win) {
      win.localStorage.setItem('lang', lang);
    },
  });
}

/** Marque la fenêtre : si la page était rechargée, le marqueur disparaîtrait. */
function markWindow(): void {
  cy.window().then((win) => {
    (win as unknown as { __noReload: boolean }).__noReload = true;
  });
}

function expectNoReload(): void {
  cy.window().its('__noReload').should('eq', true);
}

function loginThroughUi(credentials: Credentials): void {
  cy.getCy('champ-email').type(credentials.email);
  cy.getCy('champ-mot-de-passe').type(credentials.password, { log: false });
  cy.getCy('btn-se-connecter').click();
}

function typeIn(field: string, value: string): void {
  cy.getCy(field).find('input').clear();
  cy.getCy(field).find('input').type(value);
  cy.press(Cypress.Keyboard.Keys.TAB);
}

/** Saisie 09:30–10:30 le 05/10/2026 dans l'interface ouverte, au-dessus de 09:00–10:00 : 409 attendu. */
function submitOverlappingEntry(): void {
  cy.intercept('GET', '/api/v1/activity-types').as('types');
  cy.intercept('POST', ENTRIES_URL).as('create');
  cy.getCy('btn-nouvelle-saisie').find('button').click();
  cy.getCy('dialogue-saisie').should('be.visible');
  cy.wait('@types');
  typeIn('champ-date-debut', '05/10/2026');
  typeIn('champ-heure-debut', '09:30');
  typeIn('champ-heure-fin', '10:30');
  cy.getCy('select-type-activite').click();
  cy.getCy('option-REUNION').click();
  cy.getCy('btn-enregistrer').find('button').click();
}

function expectLanguage(lang: Lang): void {
  cy.get('h1').should('have.length.at.least', 1);
  cy.get('body').should(($body) => {
    const text = $body.text();
    expect(text).not.to.match(lang === 'en' ? FRENCH_WORDS : ENGLISH_WORDS);
  });
}

describe("SFD-08 : langue de l'interface", () => {
  before(() => {
    setDefaultLanguage(null);
  });

  after(() => {
    setDefaultLanguage('fr');
  });

  it('CA-08-01 : au premier accès, un navigateur français donne une connexion en français, un navigateur anglais ou allemand en anglais', () => {
    visitWithBrowserLanguage('/login', ['fr-FR', 'fr']);
    cy.get('h1').should('have.text', 'Connexion');
    cy.getCy('btn-se-connecter').should('contain', 'Se connecter');
    cy.get('html').should('have.attr', 'lang', 'fr');

    (
      [
        ['en-US', 'en'],
        ['de-DE', 'de'],
      ] as const
    ).forEach(([language, short]) => {
      cy.clearAllLocalStorage();
      visitWithBrowserLanguage('/login', [language, short]);
      cy.get('h1').should('have.text', 'Sign in');
      cy.getCy('btn-se-connecter').should('contain', 'Sign in');
      cy.get('html').should('have.attr', 'lang', 'en');
    });
  });

  it('CA-08-02 : sur la page de connexion en français, choisir English passe toute la page en anglais sans rechargement', () => {
    visitIn('/login', 'fr');
    cy.get('h1').should('have.text', 'Connexion');
    expectLanguage('fr');
    markWindow();
    cy.getCy('choix-langue-en').click();
    cy.get('h1').should('have.text', 'Sign in');
    cy.getCy('btn-se-connecter').should('contain', 'Sign in');
    expectLanguage('en');
    cy.get('html').should('have.attr', 'lang', 'en');
    expectNoReload();
  });

  it('CA-08-03 : un USER sur « Mon journal » en français choisit English dans le menu utilisateur : My journal et Monday à Sunday, sans rechargement', () => {
    createAccount('USER').then((account) => {
      loginAs(account);
      visitIn(`/journal?week=${WEEK}`, 'fr');
      cy.getCy('titre-journal').should('have.text', 'Mon journal');
      WEEK_DAYS.forEach((day, index) => cy.getCy(`jour-${day}`).should('contain', DAYS_FR[index]));
      markWindow();
      cy.getCy('btn-menu-utilisateur').click();
      cy.getCy('choix-langue-en').click();
      cy.getCy('titre-journal').should('have.text', 'My journal');
      WEEK_DAYS.forEach((day, index) => cy.getCy(`jour-${day}`).should('contain', DAYS_EN[index]));
      expectNoReload();
    });
  });

  it('CA-08-04 : la langue choisie survit au rechargement, à la déconnexion et à la reconnexion', () => {
    sharedCredentials('USER').then((credentials) => {
      visitIn('/login', 'fr');
      cy.getCy('choix-langue-en').click();
      cy.get('h1').should('have.text', 'Sign in');
      cy.reload();
      cy.get('h1').should('have.text', 'Sign in');

      loginThroughUi(credentials);
      cy.getCy('titre-journal').should('have.text', 'My journal');
      cy.reload();
      cy.getCy('titre-journal').should('have.text', 'My journal');

      cy.getCy('btn-menu-utilisateur').click();
      cy.getCy('menu-se-deconnecter').click();
      cy.location('pathname').should('eq', '/login');
      cy.get('h1').should('have.text', 'Sign in');

      loginThroughUi(credentials);
      cy.getCy('titre-journal').should('have.text', 'My journal');
    });
  });

  it('CA-08-05 : un chevauchement renvoie 409 RG-01-06 et le toast est en anglais, puis en français', () => {
    const expected: Record<Lang, RegExp> = { en: /overlaps one already declared/, fr: /chevauche une activité déjà déclarée/ };
    (['en', 'fr'] as const).forEach((lang) => {
      createAccount('USER').then((account) => {
        createEntry(account, MEETING);
        loginAs(account);
        visitIn(`/journal?week=${WEEK}`, lang);
        cy.getCy('titre-journal').should('have.text', lang === 'en' ? 'My journal' : 'Mon journal');
        submitOverlappingEntry();
        cy.wait('@create').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-01-06');
        });
        cy.getCy('toast').invoke('text').should('match', expected[lang]);
        cy.getCy('toast').invoke('text').should('not.match', lang === 'en' ? /chevauche/ : /overlaps/);
      });
    });
  });

  it('CA-08-06 : 90 min le lundi et une JOURNEE le mardi donnent 9.5 h en anglais et 9,5 h en français', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { ...MEETING, endTime: '10:30' });
      createEntry(account, { granularity: 'DAY', startDate: '2026-10-06', activityTypeCode: 'FORMATION' });
      loginAs(account);
      visitIn(`/journal?week=${WEEK}`, 'en');
      cy.getCy('total-semaine').should('have.text', '9.5 h');
      cy.getCy('total-jour-2026-10-05').should('have.text', '1.5 h');
      visitIn(`/journal?week=${WEEK}`, 'fr');
      cy.getCy('total-semaine').should('have.text', '9,5 h');
      cy.getCy('total-jour-2026-10-05').should('have.text', '1,5 h');
    });
  });

  it("CA-08-07 : le type d'activité « Réunion » reste « Réunion » en anglais", () => {
    createAccount('USER').then((account) => {
      createEntry(account, MEETING);
      loginAs(account);
      visitIn(`/journal?week=${WEEK}`, 'en');
      cy.getCy('titre-journal').should('have.text', 'My journal');
      cy.getCy('jour-2026-10-05').find('[data-cy="activity-type-tag"]').should('contain', 'Réunion');
    });
  });

  it('CA-08-08 : les écrans des trois rôles ne mélangent pas les langues, en anglais puis en français', () => {
    // Un manager, un membre et une équipe créés pour atteindre « Mon équipe » et le journal d'un membre.
    createAccount('MANAGER').then((manager) => {
      createAccount('USER').then((member) => {
        adminToken().then((token) => {
          cy.request({
            method: 'POST',
            url: '/api/v1/teams',
            headers: bearer(token),
            body: { name: `Equipe langue ${unique()}`, managerId: manager.id, memberIds: [member.id] },
          }).then((response) => {
            expect(response.status).to.eq(201);
            const teamId = response.body.id as number;

            const tours: { login: () => void; screens: { url: string; ready: string; title: Record<Lang, string> }[] }[] = [
              {
                login: () => loginAs(member),
                screens: [
                  { url: `/journal?week=${WEEK}`, ready: 'titre-journal', title: { en: 'My journal', fr: 'Mon journal' } },
                  { url: '/account/password', ready: 'form-mot-de-passe', title: { en: 'Change my password', fr: 'Changer mon mot de passe' } },
                ],
              },
              {
                login: () => loginAs(manager),
                screens: [
                  { url: `/journal?week=${WEEK}`, ready: 'titre-journal', title: { en: 'My journal', fr: 'Mon journal' } },
                  { url: `/team?team=${teamId}&week=${WEEK}`, ready: 'titre-equipe', title: { en: 'My team', fr: 'Mon équipe' } },
                  {
                    url: `/team/members/${member.id}?team=${teamId}&week=${WEEK}`,
                    ready: 'titre-journal-membre',
                    title: { en: 'Journal: Lena E2E-USER', fr: 'Journal : Lena E2E-USER' },
                  },
                ],
              },
              {
                login: () => cy.login('ADMIN'),
                screens: [
                  { url: '/admin/accounts', ready: 'table-comptes', title: { en: 'Accounts', fr: 'Comptes' } },
                  { url: '/admin/teams', ready: 'table-equipes', title: { en: 'Teams', fr: 'Équipes' } },
                  { url: '/admin/activity-types', ready: 'table-types-activite', title: { en: 'Activity types', fr: "Types d'activité" } },
                  { url: '/admin/projects', ready: 'nav-projets', title: { en: 'Projects', fr: 'Projets' } },
                  { url: '/admin/settings', ready: 'form-parametres', title: { en: 'Settings', fr: 'Paramètres' } },
                ],
              },
            ];

            (['en', 'fr'] as const).forEach((lang) => {
              tours.forEach((tour) => {
                tour.login();
                tour.screens.forEach((screen) => {
                  visitIn(screen.url, lang);
                  cy.getCy(screen.ready).should('be.visible');
                  cy.get('h1').should('contain', screen.title[lang]);
                  expectLanguage(lang);
                });
              });
            });
          });
        });
      });
    });
  });
});
