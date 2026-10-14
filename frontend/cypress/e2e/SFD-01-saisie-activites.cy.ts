// SFD-01 : saisie des activités. Un it() par critère d'acceptation, pour chaque rôle concerné (USER et MANAGER, RG-01-14).
// Ce fichier porte aussi les scénarios reportés des SFD livrées avant les saisies : CA-02-07 et CA-02-10 (SFD-02),
// CA-06-03 et CA-06-04 (SFD-06).
//
// Isolation : la base est partagée par tous les fichiers de test et rien n'y est supprimé physiquement. Deux tests
// qui saisiraient sur le même compte se gêneraient par chevauchement (RG-01-06) : chaque test crée donc par l'API
// son ou ses comptes (e-mail unique), ses types et ses projets. Dates fixes et passées : semaine du lundi 05/10/2026.
//
// État partagé : la journée standard est une valeur unique pour toute l'application. Tout test la retrouve à 420
// grâce au `afterEach`, qui la rétablit par l'API (y compris après un échec).

// Module : évite les collisions de types globaux avec les autres fichiers de test.
export {};

type EntryRole = 'USER' | 'MANAGER';
interface Credentials {
  email: string;
  password: string;
}
interface Account extends Credentials {
  id: number;
  role: EntryRole;
}
interface Entry {
  id: number;
  granularity: 'HOUR' | 'DAY' | 'RANGE';
  startDate: string;
  durationMinutes: number;
  version: number;
}
interface ActivityType {
  id: number;
  code: string;
  label: string;
  projectRequired: boolean;
  color: string;
  version: number;
}
interface Settings {
  standardDayMinutes: number;
  version: number;
}

const ENTRY_ROLES: EntryRole[] = ['USER', 'MANAGER'];
const ENTRIES_URL = '/api/v1/time-entries';
const SETTINGS_URL = '/api/v1/app-settings';
const WEEK = '2026-10-05';
const DEFAULT_MINUTES = 420;
const PASSWORD = 'Saisie-Test-2026';
const MEETING = {
  granularity: 'HOUR',
  startDate: '2026-10-05',
  startTime: '09:00',
  endTime: '10:30',
  activityTypeCode: 'REUNION',
};
const FULL_DAY = { granularity: 'DAY', startDate: '2026-10-06', activityTypeCode: 'FORMATION' };

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
function createAccount(role: EntryRole): Cypress.Chainable<Account> {
  const email = `saisie.${role.toLowerCase()}.${unique()}@example.com`;
  const body = { email, firstName: 'Sasha', lastName: `E2E-${role}`, role, initialPassword: PASSWORD };
  return adminToken().then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/users', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return { id: response.body.id as number, email, password: PASSWORD, role };
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

function postEntry(account: Credentials, body: object): Cypress.Chainable<Cypress.Response<Entry>> {
  return tokenOf(account).then((token) =>
    cy.request({ method: 'POST', url: ENTRIES_URL, headers: bearer(token), body, failOnStatusCode: false }),
  );
}

function createEntry(account: Credentials, body: object): Cypress.Chainable<Entry> {
  return postEntry(account, body).then((response) => {
    expect(response.status).to.eq(201);
    return response.body;
  });
}

function createType(overrides: Partial<ActivityType> = {}): Cypress.Chainable<ActivityType> {
  const body = {
    code: `E2E_S_${unique().toUpperCase()}`,
    label: 'Atelier e2e',
    projectRequired: false,
    color: 'SUCCESS',
    ...overrides,
  };
  return adminToken().then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/activity-types', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body as ActivityType;
    }),
  );
}

function createProject(): Cypress.Chainable<{ code: string; name: string }> {
  const code = `E2E_SP_${unique().toUpperCase()}`;
  const body = { code, name: `Projet ${code}` };
  return adminToken().then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/projects', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return body;
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
function openJournal(account: Credentials, week = WEEK): void {
  loginAs(account);
  cy.intercept('GET', `${ENTRIES_URL}?*`).as('week');
  cy.visit(`/journal?semaine=${week}`);
  cy.wait('@week').its('response.statusCode').should('eq', 200);
  cy.getCy('titre-journal').should('be.visible');
}

/** Ouvre le dialogue de saisie et attend les listes et la journée standard qu'il relit à chaque ouverture. */
function openNewEntry(button = 'btn-nouvelle-saisie'): void {
  cy.intercept('GET', '/api/v1/activity-types').as('types');
  cy.intercept('GET', SETTINGS_URL).as('settings');
  cy.intercept('POST', ENTRIES_URL).as('create');
  cy.getCy(button).find('button').click();
  cy.getCy('dialogue-saisie').should('be.visible');
  cy.wait('@types');
  cy.wait('@settings');
}

/** Tape dans un champ de date ou d'heure, puis le quitte au clavier (ce qui referme son panneau). */
function typeIn(field: string, value: string): void {
  cy.getCy(field).find('input').clear();
  cy.getCy(field).find('input').type(value);
  cy.press(Cypress.Keyboard.Keys.TAB);
}

function chooseGranularity(label: 'Heure' | 'Journée' | 'Plage'): void {
  cy.getCy('select-granularite').contains(label).click();
}

function chooseType(code: string): void {
  cy.getCy('select-type-activite').click();
  cy.getCy(`option-${code}`).click();
}

function save(): void {
  cy.getCy('btn-enregistrer').find('button').click();
}

describe('SFD-01 : saisie des activités', () => {
  afterEach(() => {
    setStandardDay(DEFAULT_MINUTES);
  });

  it('CA-01-01 : un USER ou un MANAGER saisit le 05/10/2026 de 09:00 à 10:30 en REUNION, 90 min, visible dans son journal', () => {
    ENTRY_ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        openJournal(account);
        cy.getCy('etat-vide').should('contain', 'Aucune activité cette semaine.');
        openNewEntry();
        chooseGranularity('Heure');
        typeIn('champ-date-debut', '05/10/2026');
        typeIn('champ-heure-debut', '09:00');
        typeIn('champ-heure-fin', '10:30');
        chooseType('REUNION');
        cy.getCy('duree-calculee').should('have.text', '1 h 30');
        save();
        cy.wait('@create').then(({ request, response }) => {
          expect(request.body).to.deep.eq(MEETING);
          expect(response?.statusCode).to.eq(201);
          expect(response?.body.durationMinutes).to.eq(90);
        });
        cy.getCy('toast').should('contain', 'Saisie enregistrée');
        cy.getCy('dialogue-saisie').should('not.exist');
        cy.getCy('jour-2026-10-05').within(() => {
          cy.getCy('activity-type-tag').should('contain', 'Réunion');
          cy.getCy('saisie-periode').should('contain', '09:00 – 10:30');
          // La carte affiche la description à la place de la durée ; la durée se lit dans le total.
          cy.getCy('saisie-duree').should('not.exist');
        });
        cy.getCy('total-semaine').should('have.text', '1,5 h');
      });
    });
  });

  it('CA-01-02 : une heure de fin antérieure à l’heure de début met le champ en erreur et bloque l’enregistrement', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      typeIn('champ-date-debut', '05/10/2026');
      typeIn('champ-heure-debut', '10:30');
      typeIn('champ-heure-fin', '09:00');
      chooseType('REUNION');
      save();
      cy.getCy('erreur-champ-heure-fin').should('be.visible').and('contain', 'après l’heure de début');
      cy.getCy('dialogue-saisie').should('be.visible');
      cy.get('@create.all').should('have.length', 0);
    });
  });

  it('CA-01-03 : une PLAGE du lundi 05/10/2026 au vendredi 16/10/2026 en FORMATION vaut 10 jours ouvrés, 4 200 min', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      chooseGranularity('Plage');
      typeIn('champ-date-debut', '05/10/2026');
      typeIn('champ-date-fin', '16/10/2026');
      chooseType('FORMATION');
      cy.getCy('duree-calculee').should('have.text', '70 h 00 (10 jours ouvrés)');
      save();
      cy.wait('@create').then(({ request, response }) => {
        expect(request.body).to.deep.eq({
          granularity: 'RANGE',
          startDate: '2026-10-05',
          endDate: '2026-10-16',
          activityTypeCode: 'FORMATION',
        });
        expect(response?.statusCode).to.eq(201);
        expect(response?.body.durationMinutes).to.eq(4200);
      });
      // RG-01-13 : la plage est affichée sur chaque jour couvert ; le total ne compte que les 5 jours ouvrés de la semaine.
      ['2026-10-05', '2026-10-09', '2026-10-10', '2026-10-11'].forEach((day) => {
        cy.getCy(`jour-${day}`).find('[data-cy="saisie-periode"]').should('contain', 'Du 05/10 au 16/10');
      });
      cy.getCy('total-semaine').should('have.text', '35 h');
    });
  });

  it('CA-01-04 : avec une saisie 09:00–10:00 le 05/10/2026, saisir 09:30–10:30 le même jour donne 409 RG-01-06 et un message de chevauchement', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { ...MEETING, endTime: '10:00' });
      openJournal(account);
      openNewEntry();
      typeIn('champ-date-debut', '05/10/2026');
      typeIn('champ-heure-debut', '09:30');
      typeIn('champ-heure-fin', '10:30');
      chooseType('REUNION');
      save();
      cy.wait('@create').then(({ response }) => {
        expect(response?.statusCode).to.eq(409);
        expect(response?.body.code).to.eq('RG-01-06');
      });
      cy.getCy('toast').should('contain', 'chevauche');
      cy.getCy('dialogue-saisie').should('be.visible');
      // RG-01-06 : un créneau exclut son heure de fin, 10:00–11:00 ne chevauche pas 09:00–10:00.
      postEntry(account, { ...MEETING, startTime: '10:00', endTime: '11:00' }).its('status').should('eq', 201);
    });
  });

  it('CA-01-05 : avec le type TRAVAIL_PROJET, enregistrer sans projet met le champ projet en erreur', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      typeIn('champ-date-debut', '05/10/2026');
      typeIn('champ-heure-debut', '09:00');
      typeIn('champ-heure-fin', '10:30');
      chooseType('TRAVAIL_PROJET');
      cy.getCy('select-projet').should('be.visible');
      save();
      cy.getCy('erreur-select-projet').should('be.visible').and('contain', 'Le projet est obligatoire');
      cy.get('@create.all').should('have.length', 0);
      // Le back applique la même règle : 409 RG-01-05 sur le champ projet.
      postEntry(account, { ...MEETING, activityTypeCode: 'TRAVAIL_PROJET' }).then((response) => {
        expect(response.status).to.eq(409);
        expect((response.body as unknown as { code: string }).code).to.eq('RG-01-05');
      });
    });
  });

  it('CA-01-06 : avec le type REUNION, le champ projet n’est pas affiché', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      chooseType('REUNION');
      cy.getCy('select-type-activite').should('contain', 'Réunion');
      cy.getCy('select-projet').should('not.exist');
      // Contre-épreuve : il apparaît pour un type qui l'exige.
      chooseType('TRAVAIL_PROJET');
      cy.getCy('select-projet').should('be.visible');
    });
  });

  it('CA-01-07 : une date de début dans le futur est refusée, à l’écran et par l’API', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      typeIn('champ-date-debut', '04/01/2099');
      typeIn('champ-heure-debut', '09:00');
      typeIn('champ-heure-fin', '10:30');
      chooseType('REUNION');
      save();
      cy.getCy('erreur-champ-date-debut').should('be.visible').and('contain', 'ne peut pas être dans le futur');
      cy.get('@create.all').should('have.length', 0);
      postEntry(account, { ...MEETING, startDate: '2099-01-04' }).then((response) => {
        expect(response.status).to.eq(409);
        expect((response.body as unknown as { code: string }).code).to.eq('RG-01-07');
      });
    });
  });

  it('CA-01-08 : modifier ou supprimer par l’API la saisie d’un autre USER répond 403, pour un USER comme pour un MANAGER', () => {
    createAccount('USER').then((owner) => {
      createEntry(owner, MEETING).then((entry) => {
        ENTRY_ROLES.forEach((role) => {
          createAccount(role).then((other) => {
            tokenOf(other).then((token) => {
              cy.request({
                method: 'PUT',
                url: `${ENTRIES_URL}/${entry.id}`,
                headers: bearer(token),
                body: { ...MEETING, endTime: '11:00', version: entry.version },
                failOnStatusCode: false,
              }).then((response) => {
                expect(response.status, `PUT par un autre ${role}`).to.eq(403);
                expect(response.body.code).to.eq('RG-01-10');
              });
              cy.request({
                method: 'DELETE',
                url: `${ENTRIES_URL}/${entry.id}`,
                headers: bearer(token),
                failOnStatusCode: false,
              })
                .its('status')
                .should('eq', 403);
              cy.request({ url: `${ENTRIES_URL}/${entry.id}`, headers: bearer(token), failOnStatusCode: false })
                .its('status')
                .should('eq', 403);
            });
          });
        });
        // La saisie du propriétaire est intacte.
        tokenOf(owner).then((token) =>
          cy
            .request({ url: `${ENTRIES_URL}/${entry.id}`, headers: bearer(token) })
            .its('body.durationMinutes')
            .should('eq', 90),
        );
      });
    });
  });

  it('CA-01-09 : avec des saisies de 90 min et 7 h dans la semaine, le total affiché est 8,5 h', () => {
    ENTRY_ROLES.forEach((role) => {
      createAccount(role).then((account) => {
        createEntry(account, MEETING);
        createEntry(account, FULL_DAY);
        openJournal(account);
        cy.getCy('jour-2026-10-05').find('[data-cy="saisie-periode"]').should('contain', '09:00 – 10:30');
        cy.getCy('jour-2026-10-06').find('[data-cy="saisie-periode"]').should('contain', 'Journée entière');
        cy.getCy('total-semaine').should('have.text', '8,5 h');
      });
    });
  });

  it('CA-02-07 : une saisie dont le type a été désactivé reste affichée dans le journal avec le libellé et la couleur du type', () => {
    createType({ label: 'Atelier clos', color: 'SUCCESS' }).then((type) => {
      createAccount('USER').then((account) => {
        createEntry(account, { ...MEETING, activityTypeCode: type.code });
        adminToken().then((token) =>
          cy
            .request({ method: 'POST', url: `/api/v1/activity-types/${type.id}/deactivation`, headers: bearer(token) })
            .its('status')
            .should('eq', 200),
        );
        openJournal(account);
        cy.getCy('jour-2026-10-05').within(() => {
          cy.getCy('activity-type-tag').should('contain', 'Atelier clos');
        });
        tokenOf(account).then((token) =>
          cy.request({ url: `${ENTRIES_URL}?weekStart=${WEEK}`, headers: bearer(token) }).then((response) => {
            const [entry] = response.body.entries;
            expect(entry.activityType).to.deep.eq({
              code: type.code,
              label: 'Atelier clos',
              color: 'SUCCESS',
              active: false,
            });
          }),
        );
        // RG-02-06 : le type désactivé n'est plus proposé pour une nouvelle saisie.
        openNewEntry();
        cy.getCy('select-type-activite').click();
        cy.getCy('option-REUNION').should('be.visible');
        cy.getCy(`option-${type.code}`).should('not.exist');
      });
    });
  });

  it('CA-02-10 : changer « projet requis » d’un type utilisé par une saisie répond 409 RG-02-07, même si la saisie est supprimée', () => {
    createType().then((type) => {
      const update = (projectRequired: boolean, version: number) =>
        adminToken().then((token) =>
          cy.request({
            method: 'PUT',
            url: `/api/v1/activity-types/${type.id}`,
            headers: bearer(token),
            body: { label: type.label, color: type.color, projectRequired, version },
            failOnStatusCode: false,
          }),
        );
      createAccount('USER').then((account) => {
        createEntry(account, { ...MEETING, activityTypeCode: type.code }).then((entry) => {
          update(true, type.version).then((response) => {
            expect(response.status).to.eq(409);
            expect(response.body.code).to.eq('RG-02-07');
          });
          tokenOf(account).then((token) =>
            cy
              .request({ method: 'DELETE', url: `${ENTRIES_URL}/${entry.id}`, headers: bearer(token) })
              .its('status')
              .should('eq', 204),
          );
          update(true, type.version).its('status').should('eq', 409);
          // Les autres champs restent modifiables.
          update(false, type.version).its('status').should('eq', 200);
        });
      });
    });
  });

  it('CA-06-03 : avec une journée standard de 450 minutes, la JOURNEE saisie par un USER dure 450 minutes', () => {
    setStandardDay(450);
    createAccount('USER').then((account) => {
      openJournal(account);
      openNewEntry();
      chooseGranularity('Journée');
      typeIn('champ-date-debut', '06/10/2026');
      chooseType('FORMATION');
      cy.getCy('duree-calculee').should('have.text', '7 h 30');
      save();
      cy.wait('@create').then(({ response }) => {
        expect(response?.statusCode).to.eq(201);
        expect(response?.body.durationMinutes).to.eq(450);
      });
      cy.getCy('jour-2026-10-06').find('[data-cy="saisie-periode"]').should('contain', 'Journée entière');
      cy.getCy('total-semaine').should('have.text', '7,5 h');
    });
  });

  it('CA-06-04 : une JOURNEE enregistrée à 420 minutes reste à 420 quand l’ADMIN passe la journée standard à 450', () => {
    createAccount('USER').then((account) => {
      createEntry(account, FULL_DAY).then((entry) => {
        expect(entry.durationMinutes).to.eq(420);
        setStandardDay(450);
        tokenOf(account).then((token) =>
          cy
            .request({ url: `${ENTRIES_URL}/${entry.id}`, headers: bearer(token) })
            .its('body.durationMinutes')
            .should('eq', 420),
        );
        openJournal(account);
        cy.getCy('jour-2026-10-06').find('[data-cy="saisie-periode"]').should('contain', 'Journée entière');
        cy.getCy('total-semaine').should('have.text', '7 h');
      });
    });
  });

  // Hors critères d'acceptation : preuves e2e des règles et écrans sans CA dédié.
  it('RG-01-12 : un USER modifie sa saisie depuis le journal, granularité et projet compris', () => {
    createProject().then((project) => {
      createAccount('USER').then((account) => {
        createEntry(account, MEETING).then((entry) => {
          openJournal(account);
          cy.intercept('GET', '/api/v1/projects').as('projects');
          cy.intercept('PUT', `${ENTRIES_URL}/${entry.id}`).as('update');
          cy.getCy(`btn-modifier-saisie-${entry.id}`).find('button').click();
          cy.getCy('dialogue-saisie').should('be.visible');
          cy.wait('@projects');
          cy.getCy('champ-heure-debut').find('input').should('have.value', '09:00');
          cy.getCy('select-type-activite').should('contain', 'Réunion');
          chooseGranularity('Journée');
          chooseType('TRAVAIL_PROJET');
          cy.getCy('select-projet').click();
          cy.getCy(`option-${project.code}`).click();
          cy.getCy('duree-calculee').should('have.text', '7 h 00');
          save();
          cy.wait('@update').then(({ request, response }) => {
            expect(request.body).to.deep.eq({
              granularity: 'DAY',
              startDate: '2026-10-05',
              activityTypeCode: 'TRAVAIL_PROJET',
              projectCode: project.code,
              version: entry.version,
            });
            expect(response?.statusCode).to.eq(200);
            expect(response?.body.durationMinutes).to.eq(420);
          });
          cy.getCy('toast').should('contain', 'Saisie modifiée');
          cy.getCy('jour-2026-10-05').within(() => {
            cy.getCy('saisie-projet').should('contain', project.name);
            cy.getCy('saisie-periode').should('contain', 'Journée entière');
          });
          cy.getCy('total-semaine').should('have.text', '7 h');
        });
      });
    });
  });

  it('E3, RG-01-11 : la suppression demande une confirmation explicite, puis la saisie sort du journal, du total et du contrôle de chevauchement', () => {
    createAccount('USER').then((account) => {
      createEntry(account, FULL_DAY);
      createEntry(account, MEETING).then((entry) => {
        openJournal(account);
        cy.intercept('DELETE', `${ENTRIES_URL}/${entry.id}`).as('remove');
        cy.getCy('total-semaine').should('have.text', '8,5 h');
        cy.getCy(`btn-supprimer-saisie-${entry.id}`).find('button').click();
        cy.contains('Supprimer la saisie « Réunion » du 05/10 (1 h 30) ?').should('be.visible');
        // Annuler ne supprime rien.
        cy.getCy('btn-annuler').click();
        cy.getCy(`saisie-${entry.id}`).should('be.visible');
        cy.get('@remove.all').should('have.length', 0);
        cy.getCy(`btn-supprimer-saisie-${entry.id}`).find('button').click();
        cy.getCy('btn-confirmer').click();
        cy.wait('@remove').its('response.statusCode').should('eq', 204);
        cy.getCy('toast').should('contain', 'Saisie supprimée');
        cy.getCy(`saisie-${entry.id}`).should('not.exist');
        cy.getCy('total-semaine').should('have.text', '7 h');
        // La saisie supprimée n'est plus lisible et ne bloque plus le même créneau.
        tokenOf(account).then((token) =>
          cy
            .request({ url: `${ENTRIES_URL}/${entry.id}`, headers: bearer(token), failOnStatusCode: false })
            .its('status')
            .should('eq', 404),
        );
        postEntry(account, MEETING).its('status').should('eq', 201);
      });
    });
  });

  it('RG-01-14 : la saisie est fermée à l’ADMIN (403, pas de menu « Mon journal ») et à un appel sans jeton (401)', () => {
    adminToken().then((token) => {
      cy.request({ method: 'POST', url: ENTRIES_URL, headers: bearer(token), body: MEETING, failOnStatusCode: false })
        .its('status')
        .should('eq', 403);
      cy.request({ url: `${ENTRIES_URL}?weekStart=${WEEK}`, headers: bearer(token), failOnStatusCode: false })
        .its('status')
        .should('eq', 403);
    });
    cy.request({ url: `${ENTRIES_URL}?weekStart=${WEEK}`, failOnStatusCode: false }).its('status').should('eq', 401);
    cy.login('ADMIN');
    cy.visit('/journal');
    cy.getCy('nav-comptes').should('be.visible');
    cy.getCy('nav-mon-journal').should('not.exist');
    cy.getCy('titre-journal').should('not.exist');
  });

  it('SFD-01 E1 : le « + » d’un jour ouvre la saisie à la date de ce jour, même dans une semaine sans saisie', () => {
    createAccount('USER').then((account) => {
      openJournal(account);
      cy.getCy('etat-vide').should('be.visible');
      openNewEntry('btn-ajouter-saisie-2026-10-07');
      cy.getCy('champ-date-debut').find('input').should('have.value', '07/10/2026');
      typeIn('champ-heure-debut', '14:00');
      typeIn('champ-heure-fin', '15:00');
      chooseType('REUNION');
      save();
      cy.wait('@create').then(({ request, response }) => {
        expect(request.body.startDate).to.eq('2026-10-07');
        expect(response?.statusCode).to.eq(201);
      });
      cy.getCy('jour-2026-10-07').find('[data-cy="saisie-periode"]').should('contain', '14:00 – 15:00');
      cy.getCy('etat-vide').should('not.exist');
    });
  });

  it('SFD-01 E1 : la carte d’une saisie affiche sa description à la place de sa durée', () => {
    createAccount('USER').then((account) => {
      createEntry(account, { ...MEETING, comment: 'Point hebdo avec l’équipe' });
      openJournal(account);
      cy.getCy('jour-2026-10-05').within(() => {
        cy.getCy('saisie-commentaire').should('have.text', 'Point hebdo avec l’équipe');
        cy.getCy('saisie-duree').should('not.exist');
      });
      cy.getCy('total-semaine').should('have.text', '1,5 h');
    });
  });

  it('SFD-01 E1 : « Semaine suivante » et « Semaine précédente » changent la semaine affichée et celle de l’URL', () => {
    createAccount('USER').then((account) => {
      createEntry(account, MEETING);
      openJournal(account);
      cy.getCy('libelle-semaine').should('contain', 'Semaine du 05/10/2026 au 11/10/2026');
      cy.getCy('btn-semaine-precedente').find('button').click();
      cy.wait('@week');
      cy.location('search').should('eq', '?semaine=2026-09-28');
      cy.getCy('libelle-semaine').should('contain', 'Semaine du 28/09/2026 au 04/10/2026');
      cy.getCy('etat-vide').should('be.visible');
      cy.getCy('btn-semaine-suivante').find('button').click();
      cy.wait('@week');
      cy.location('search').should('eq', '?semaine=2026-10-05');
      cy.getCy('jour-2026-10-05').find('[data-cy="saisie-periode"]').should('contain', '09:00 – 10:30');
    });
  });
});
