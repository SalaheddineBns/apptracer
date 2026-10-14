// SFD-04 : vue équipe (MANAGER). Un it() par critère d'acceptation ; CA-05-12 (SFD-05) y est joué, car il exige la vue équipe.
// Quelques règles sans CA propre ont leur it() : RG-04-06, RG-04-09, RG-04-10 (voir le dernier test).
//
// Isolation : la base est partagée par tous les fichiers de test et rien n'y est supprimé physiquement. Chaque test crée donc par
// l'API ses managers, ses membres, ses équipes (noms uniques) et leurs saisies, et n'utilise jamais `user.e2e` / `manager.e2e`
// comme manager ou membre (RG-05-04, RG-05-10). Dates fixes et passées : semaine du lundi 05/10/2026 ; PLAGE du 08/10 au 13/10.
// La journée standard (420 min) n'est jamais modifiée par ce fichier.

// Module : évite les collisions de types globaux avec les autres fichiers de test.
export {};

type AccountRole = 'USER' | 'MANAGER' | 'ADMIN';
interface Credentials {
  email: string;
  password: string;
}
interface Account extends Credentials {
  id: number;
  role: AccountRole;
  firstName: string;
  lastName: string;
}
interface Team {
  id: number;
  name: string;
  version: number;
}
interface Entry {
  id: number;
  durationMinutes: number;
  version: number;
}
interface Problem {
  status: number;
  code?: string;
  detail?: string;
}
interface ManagedTeam {
  id: number;
  name: string;
}
interface MemberWeek {
  member: { id: number; active: boolean };
  days: { date: string; minutes: number }[];
  totalMinutes: number;
}
interface TeamWeek {
  team: ManagedTeam;
  weekStart: string;
  members: MemberWeek[];
}

const TEAMS_URL = '/api/v1/teams';
const USERS_URL = '/api/v1/users';
const ENTRIES_URL = '/api/v1/time-entries';
const VIEW_URL = '/api/v1/team-view';
const PASSWORD = 'Equipe-Vue-2026';
const WEEK = '2026-10-05';
const NEXT_WEEK = '2026-10-12';
const MEETING = { granularity: 'HOUR', startDate: '2026-10-05', startTime: '09:00', endTime: '10:30', activityTypeCode: 'REUNION' };
const FULL_DAY = { granularity: 'DAY', startDate: '2026-10-06', activityTypeCode: 'FORMATION' };
const RANGE = { granularity: 'RANGE', startDate: '2026-10-08', endDate: '2026-10-13', activityTypeCode: 'FORMATION' };
const WEEK_DAYS = ['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-08', '2026-10-09', '2026-10-10', '2026-10-11'];

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

/** Appel d'API qui ne fait pas échouer le test sur un statut d'erreur : le test contrôle le code HTTP. */
function call<T = unknown>(method: string, url: string, token: string | null, body?: object): Cypress.Chainable<Cypress.Response<T>> {
  return cy.request({ method, url, headers: token ? bearer(token) : {}, body, failOnStatusCode: false });
}

function createAccount(role: AccountRole, firstName = 'Sasha', lastName = 'Vue'): Cypress.Chainable<Account> {
  const email = `vue.${role.toLowerCase()}.${unique()}@example.com`;
  const body = { email, firstName, lastName, role, initialPassword: PASSWORD };
  return adminToken().then((token) =>
    call<{ id: number }>('POST', USERS_URL, token, body).then((response) => {
      expect(response.status).to.eq(201);
      return { id: response.body.id, email, password: PASSWORD, role, firstName, lastName };
    }),
  );
}

function createTeam(name: string, manager: Account, members: Account[] = []): Cypress.Chainable<Team> {
  return adminToken().then((token) =>
    call<Team>('POST', TEAMS_URL, token, { name, managerId: manager.id, memberIds: members.map((m) => m.id) }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body;
    }),
  );
}

/** PUT d'une équipe par l'ADMIN (nouveau manager et/ou nouveaux membres), avec la version lue. */
function updateTeam(team: Team, name: string, manager: Account, members: Account[]): Cypress.Chainable<Team> {
  return adminToken().then((token) =>
    call<Team>('PUT', `${TEAMS_URL}/${team.id}`, token, {
      name,
      managerId: manager.id,
      memberIds: members.map((m) => m.id),
      version: team.version,
    }).then((response) => {
      expect(response.status).to.eq(200);
      return response.body;
    }),
  );
}

function teamName(label: string): string {
  return `${label} ${unique()}`;
}

function createEntry(account: Credentials, body: object): Cypress.Chainable<Entry> {
  return tokenOf(account).then((token) =>
    call<Entry>('POST', ENTRIES_URL, token, body).then((response) => {
      expect(response.status).to.eq(201);
      return response.body;
    }),
  );
}

function loginAs(account: Credentials): void {
  cy.session(account.email, () => {
    cy.request('POST', '/api/v1/auth/login', { email: account.email, password: account.password }).then(({ body }) => {
      window.localStorage.setItem('accessToken', body.accessToken);
      window.localStorage.setItem('refreshToken', body.refreshToken);
    });
  });
}

function watchTeamView(): void {
  cy.intercept('GET', `${VIEW_URL}/teams`).as('teams');
  cy.intercept('GET', /\/api\/v1\/team-view\/teams\/\d+\/week\?/).as('summary');
  cy.intercept('GET', /\/api\/v1\/team-view\/members\/\d+\/time-entries\?/).as('memberEntries');
}

/** Ouvre « Mon équipe » (E1) en tant que MANAGER sur la semaine donnée et attend les équipes puis le tableau. */
function openTeamView(manager: Credentials, query = `semaine=${WEEK}`): void {
  loginAs(manager);
  watchTeamView();
  cy.visit(`/equipe?${query}`);
  cy.wait('@teams').its('response.statusCode').should('eq', 200);
  cy.wait('@summary').its('response.statusCode').should('eq', 200);
  cy.getCy('table-equipe-semaine').should('be.visible');
}

function openMemberJournal(manager: Credentials, member: { id: number }, query = `semaine=${WEEK}`): void {
  loginAs(manager);
  watchTeamView();
  cy.visit(`/equipe/membres/${member.id}?${query}`);
}

function text(selector: string): Cypress.Chainable<string> {
  return cy.getCy(selector).invoke('text').then((value) => value.trim());
}

function dayMinutes(week: TeamWeek, memberId: number, date: string): number | undefined {
  return week.members.find((m) => m.member.id === memberId)?.days.find((d) => d.date === date)?.minutes;
}

describe('SFD-04 : vue équipe', () => {
  beforeEach(() => {
    cy.viewport(1280, 1000);
  });

  it('CA-04-01 : un MANAGER d’une équipe de deux membres (90 min et 7 h) voit les deux membres avec 1,5 h et 7 h', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((anna) => {
        createAccount('USER', 'Bob', 'Berg').then((bob) => {
          createEntry(anna, MEETING);
          createEntry(bob, FULL_DAY);
          createTeam(teamName('Produit'), manager, [anna, bob]).then((team) => {
            openTeamView(manager);
            cy.getCy('nav-mon-equipe').should('be.visible');
            cy.getCy('titre-equipe').should('contain', 'Mon équipe');
            cy.getCy('select-equipe').should('not.exist'); // une seule équipe (RG-04-08)
            cy.getCy('libelle-semaine').should('contain', 'Semaine du 05/10/2026 au 11/10/2026');
            cy.get('@summary.all').should('have.length', 1);
            cy.getCy('table-equipe-semaine').within(() => {
              cy.get('[data-cy^="ligne-membre-"]').should('have.length', 2);
            });
            text(`total-membre-${anna.id}`).should('eq', '1,5 h');
            text(`total-membre-${bob.id}`).should('eq', '7 h');
            text(`duree-membre-${anna.id}-2026-10-05`).should('eq', '1,5 h');
            text(`duree-membre-${bob.id}-2026-10-06`).should('eq', '7 h');
            cy.getCy(`duree-membre-${anna.id}-2026-10-06`).should('contain', '–');
            cy.getCy(`lien-membre-${anna.id}`).should('have.text', 'Anna Abel').and('have.attr', 'href').and('contain', `equipe=${team.id}`);
            tokenOf(manager).then((token) =>
              call<TeamWeek>('GET', `${VIEW_URL}/teams/${team.id}/week?weekStart=${WEEK}`, token).then((response) => {
                expect(response.status).to.eq(200);
                expect(response.body.members.map((m) => [m.member.id, m.totalMinutes])).to.deep.eq([
                  [anna.id, 90],
                  [bob.id, 420],
                ]);
              }),
            );
          });
        });
      });
    });
  });

  it('CA-04-02 : un MANAGER ouvre le journal d’un membre, voit ses saisies de la semaine, sans « Nouvelle saisie », « Modifier » ni « Supprimer »', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((member) => {
        createEntry(member, { ...MEETING, comment: 'Point hebdo e2e' }).then((entry) => {
          createTeam(teamName('Produit'), manager, [member]).then((team) => {
            openTeamView(manager);
            cy.getCy(`lien-membre-${member.id}`).click();
            cy.wait('@memberEntries').its('response.statusCode').should('eq', 200);
            cy.location('pathname').should('eq', `/equipe/membres/${member.id}`);
            cy.location('search').should('contain', `semaine=${WEEK}`);
            cy.getCy('titre-journal-membre').should('contain', 'Journal de Anna Abel');
            cy.getCy('journal-semaine').should('be.visible');
            cy.getCy('jour-2026-10-05').within(() => {
              cy.getCy(`saisie-${entry.id}`).should('be.visible');
              cy.getCy('saisie-duree').should('have.text', '1 h 30');
              cy.getCy('saisie-commentaire').should('contain', 'Point hebdo e2e');
            });
            text('total-semaine').should('eq', '1,5 h');
            cy.getCy('btn-nouvelle-saisie').should('not.exist');
            cy.get('[data-cy^="btn-modifier-saisie-"]').should('not.exist');
            cy.get('[data-cy^="btn-supprimer-saisie-"]').should('not.exist');
            cy.get('[data-cy^="btn-ajouter-saisie-"]').should('not.exist');
            // Retour vers E1 en conservant la semaine et l'équipe
            cy.getCy('btn-retour-equipe').find('button').click();
            cy.location('pathname').should('eq', '/equipe');
            cy.location('search').should('contain', `semaine=${WEEK}`).and('contain', `equipe=${team.id}`);
            cy.getCy('table-equipe-semaine').should('be.visible');
          });
        });
      });
    });
  });

  it('CA-04-03 : un MANAGER de l’équipe A qui demande les saisies d’un membre de l’équipe B reçoit 403, à l’écran comme par l’API', () => {
    createAccount('MANAGER', 'Alice', 'Managera').then((managerA) => {
      createAccount('MANAGER', 'Bruno', 'Managerb').then((managerB) => {
        createAccount('USER', 'Mia', 'Autre').then((memberB) => {
          createAccount('USER', 'Mon', 'Equipea').then((memberA) => {
            createEntry(memberB, MEETING);
            createTeam(teamName('Equipe A'), managerA, [memberA]).then(() => {
              createTeam(teamName('Equipe B'), managerB, [memberB]).then((teamB) => {
                tokenOf(managerA).then((token) => {
                  call<Problem>('GET', `${VIEW_URL}/members/${memberB.id}/time-entries?weekStart=${WEEK}`, token).then((response) => {
                    expect(response.status).to.eq(403);
                    expect(response.body.code).to.eq('RG-04-02');
                  });
                  call<Problem>('GET', `${VIEW_URL}/teams/${teamB.id}/week?weekStart=${WEEK}`, token).then((response) => {
                    expect(response.status).to.eq(403);
                    expect(response.body.code).to.eq('RG-04-02');
                  });
                });
                // Écran : journal du membre de B
                openMemberJournal(managerA, memberB);
                cy.wait('@memberEntries').its('response.statusCode').should('eq', 403);
                cy.getCy('message-membre-interdit').should('contain', 'Ce compte ne fait pas partie de vos équipes.');
                cy.getCy('journal-semaine').should('not.exist');
                cy.getCy('navigation-semaine').should('not.exist');
                // Écran : équipe B demandée dans l'URL
                watchTeamView();
                cy.visit(`/equipe?equipe=${teamB.id}&semaine=${WEEK}`);
                cy.wait('@summary').its('response.statusCode').should('eq', 403);
                cy.getCy('message-equipe-interdite').should('contain', 'ne fait pas partie de celles dont vous êtes manager');
                cy.getCy('table-equipe-semaine').should('not.exist');
                cy.getCy('navigation-semaine').should('not.exist');
              });
            });
          });
        });
      });
    });
  });

  it('CA-04-04 : un MANAGER qui modifie ou supprime par l’API la saisie d’un membre de son équipe reçoit 403, la saisie reste intacte', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((member) => {
        createEntry(member, MEETING).then((entry) => {
          createTeam(teamName('Produit'), manager, [member]).then(() => {
            tokenOf(manager).then((token) => {
              call<Problem>('PUT', `${ENTRIES_URL}/${entry.id}`, token, { ...MEETING, endTime: '11:00', version: entry.version }).then((response) => {
                expect(response.status, 'PUT').to.eq(403);
                expect(response.body.code).to.eq('RG-01-10');
              });
              call('DELETE', `${ENTRIES_URL}/${entry.id}`, token).its('status').should('eq', 403);
              // La vue équipe n'expose aucune écriture
              call('POST', `${VIEW_URL}/members/${member.id}/time-entries`, token, MEETING).its('status').should('be.oneOf', [403, 405]);
            });
            tokenOf(member).then((token) =>
              call<Entry>('GET', `${ENTRIES_URL}/${entry.id}`, token).then((response) => {
                expect(response.status).to.eq(200);
                expect(response.body.durationMinutes).to.eq(90);
                expect(response.body.version).to.eq(entry.version);
              }),
            );
            // Écran : le journal du membre n'offre aucune action, la saisie est toujours affichée
            openMemberJournal(manager, member);
            cy.wait('@memberEntries').its('response.statusCode').should('eq', 200);
            cy.getCy(`saisie-${entry.id}`).should('be.visible');
            cy.get('[data-cy^="btn-modifier-saisie-"]').should('not.exist');
            cy.get('[data-cy^="btn-supprimer-saisie-"]').should('not.exist');
          });
        });
      });
    });
  });

  it('CA-04-05 : un USER reçoit 403 sur l’API de la vue équipe, le menu « Mon équipe » est absent et /equipe le renvoie à son journal', () => {
    createAccount('USER').then((user) => {
      tokenOf(user).then((token) => {
        call('GET', `${VIEW_URL}/teams`, token).its('status').should('eq', 403);
        call('GET', `${VIEW_URL}/teams/1/week?weekStart=${WEEK}`, token).its('status').should('eq', 403);
        call('GET', `${VIEW_URL}/members/1/time-entries?weekStart=${WEEK}`, token).its('status').should('eq', 403);
      });
      loginAs(user);
      cy.visit('/journal');
      cy.getCy('nav-mon-journal').should('be.visible');
      cy.getCy('nav-mon-equipe').should('not.exist');
      cy.visit('/equipe');
      cy.location('pathname').should('eq', '/journal');
      cy.getCy('titre-equipe').should('not.exist');
    });
  });

  it('CA-04-06 : un ADMIN reçoit 403 sur l’API de la vue équipe et le menu « Mon équipe » est absent', () => {
    adminToken().then((token) => {
      call('GET', `${VIEW_URL}/teams`, token).its('status').should('eq', 403);
      call('GET', `${VIEW_URL}/teams/1/week?weekStart=${WEEK}`, token).its('status').should('eq', 403);
      call('GET', `${VIEW_URL}/members/1/time-entries?weekStart=${WEEK}`, token).its('status').should('eq', 403);
    });
    cy.login('ADMIN');
    cy.visit('/admin/comptes');
    cy.getCy('nav-comptes').should('be.visible');
    cy.getCy('nav-mon-equipe').should('not.exist');
    cy.visit('/equipe');
    cy.location('pathname').should('eq', '/admin/comptes');
    cy.getCy('titre-equipe').should('not.exist');
  });

  it('CA-04-07 : une PLAGE du jeudi 08/10 au mardi 13/10/2026 donne 14 h pour la semaine du 05/10 (jeudi et vendredi)', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((member) => {
        createEntry(member, RANGE);
        createTeam(teamName('Produit'), manager, [member]).then(() => {
          openTeamView(manager);
          text(`total-membre-${member.id}`).should('eq', '14 h');
          text(`duree-membre-${member.id}-2026-10-08`).should('eq', '7 h');
          text(`duree-membre-${member.id}-2026-10-09`).should('eq', '7 h');
          ['2026-10-05', '2026-10-06', '2026-10-07', '2026-10-10', '2026-10-11'].forEach((day) => {
            cy.getCy(`duree-membre-${member.id}-${day}`).should('contain', '–');
          });
          cy.get('@summary').its('response.body').then((body) => {
            const week = body as TeamWeek;
            expect(week.members[0].totalMinutes).to.eq(840);
            expect(dayMinutes(week, member.id, '2026-10-08')).to.eq(420);
            expect(dayMinutes(week, member.id, '2026-10-10')).to.eq(0);
          });
          // Semaine suivante : lundi 12 et mardi 13 = 2 jours ouvrés
          cy.getCy('btn-semaine-suivante').find('button').click();
          cy.wait('@summary').its('response.statusCode').should('eq', 200);
          cy.location('search').should('contain', `semaine=${NEXT_WEEK}`);
          cy.getCy('libelle-semaine').should('contain', 'Semaine du 12/10/2026 au 18/10/2026');
          text(`total-membre-${member.id}`).should('eq', '14 h');
          text(`duree-membre-${member.id}-2026-10-12`).should('eq', '7 h');
          cy.getCy(`duree-membre-${member.id}-2026-10-14`).should('contain', '–');
        });
      });
    });
  });

  it('CA-04-08 : une saisie supprimée par son membre n’apparaît pas dans la vue équipe et ne compte pas dans son total', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((member) => {
        createEntry(member, MEETING).then((kept) => {
          createEntry(member, FULL_DAY).then((removed) => {
            createTeam(teamName('Produit'), manager, [member]).then(() => {
              tokenOf(member).then((token) => call('DELETE', `${ENTRIES_URL}/${removed.id}`, token).its('status').should('eq', 204));
              openTeamView(manager);
              text(`total-membre-${member.id}`).should('eq', '1,5 h');
              cy.getCy(`duree-membre-${member.id}-2026-10-06`).should('contain', '–');
              cy.get('@summary').its('response.body').then((body) => {
                expect(dayMinutes(body as TeamWeek, member.id, '2026-10-06')).to.eq(0);
              });
              cy.getCy(`lien-membre-${member.id}`).click();
              cy.wait('@memberEntries').its('response.body.week.entries').then((entries) => {
                expect((entries as { id: number }[]).map((e) => e.id)).to.deep.eq([kept.id]);
              });
              cy.getCy(`saisie-${kept.id}`).should('be.visible');
              cy.getCy(`saisie-${removed.id}`).should('not.exist');
              text('total-semaine').should('eq', '1,5 h');
            });
          });
        });
      });
    });
  });

  it('CA-04-09 : un MANAGER sans équipe ouvre « Mon équipe » et lit qu’aucune équipe ne lui est rattachée', () => {
    createAccount('MANAGER').then((manager) => {
      loginAs(manager);
      watchTeamView();
      cy.visit('/journal');
      cy.getCy('nav-mon-equipe').should('be.visible').click();
      cy.wait('@teams').then(({ response }) => {
        expect(response?.statusCode).to.eq(200);
        expect(response?.body).to.deep.eq([]);
      });
      cy.location('pathname').should('eq', '/equipe');
      cy.getCy('message-aucune-equipe').should('contain', 'Aucune équipe ne vous est rattachée.');
      cy.getCy('select-equipe').should('not.exist');
      cy.getCy('table-equipe-semaine').should('not.exist');
      cy.getCy('etat-erreur').should('not.exist');
      cy.get('@summary.all').should('have.length', 0);
    });
  });

  it('CA-04-10 : un MANAGER de deux équipes change d’équipe dans le sélecteur, membres et totaux sont ceux de l’équipe choisie, même semaine', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((anna) => {
        createAccount('USER', 'Bob', 'Berg').then((bob) => {
          createAccount('USER', 'Cleo', 'Carre').then((cleo) => {
            createEntry(anna, MEETING);
            createEntry(bob, FULL_DAY);
            createEntry(cleo, RANGE);
            // Créée dans l'ordre inverse : la première par ordre alphabétique est « Alpha ».
            createTeam(teamName('Bravo'), manager, [bob, cleo]).then((bravo) => {
              createTeam(teamName('Alpha'), manager, [anna]).then((alpha) => {
                openTeamView(manager);
                cy.get('@summary').its('request.url').should('contain', `/teams/${alpha.id}/week`);
                cy.getCy('select-equipe').should('be.visible');
                cy.getCy(`ligne-membre-${anna.id}`).should('be.visible');
                cy.getCy(`ligne-membre-${bob.id}`).should('not.exist');
                text(`total-membre-${anna.id}`).should('eq', '1,5 h');
                cy.getCy('select-equipe').click();
                cy.getCy(`option-equipe-${bravo.id}`).click();
                cy.wait('@summary').then(({ request, response }) => {
                  expect(request.url).to.contain(`/teams/${bravo.id}/week`);
                  expect(request.url).to.contain(`weekStart=${WEEK}`);
                  expect(response?.statusCode).to.eq(200);
                });
                cy.location('search').should('contain', `equipe=${bravo.id}`).and('contain', `semaine=${WEEK}`);
                cy.getCy('libelle-semaine').should('contain', 'Semaine du 05/10/2026 au 11/10/2026');
                cy.getCy(`ligne-membre-${anna.id}`).should('not.exist');
                text(`total-membre-${bob.id}`).should('eq', '7 h');
                text(`total-membre-${cleo.id}`).should('eq', '14 h');
              });
            });
          });
        });
      });
    });
  });

  it('CA-04-11 : un membre retiré de l’équipe n’est plus vu du MANAGER, et l’API répond 403 pour ses saisies', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((leaver) => {
        createAccount('USER', 'Bob', 'Berg').then((stays) => {
          createEntry(leaver, MEETING);
          createEntry(stays, FULL_DAY);
          const name = teamName('Produit');
          createTeam(name, manager, [leaver, stays]).then((team) => {
            openTeamView(manager);
            cy.getCy(`ligne-membre-${leaver.id}`).should('be.visible');
            updateTeam(team, name, manager, [stays]);
            cy.reload();
            cy.wait('@summary').its('response.statusCode').should('eq', 200);
            cy.getCy(`ligne-membre-${stays.id}`).should('be.visible');
            cy.getCy(`ligne-membre-${leaver.id}`).should('not.exist');
            cy.getCy(`lien-membre-${leaver.id}`).should('not.exist');
            tokenOf(manager).then((token) => {
              call<Problem>('GET', `${VIEW_URL}/members/${leaver.id}/time-entries?weekStart=${WEEK}`, token).then((response) => {
                expect(response.status).to.eq(403);
                expect(response.body.code).to.eq('RG-04-02');
              });
              call<TeamWeek>('GET', `${VIEW_URL}/teams/${team.id}/week?weekStart=${WEEK}`, token).then((response) => {
                expect(response.status).to.eq(200);
                expect(response.body.members.map((m) => m.member.id)).to.deep.eq([stays.id]);
              });
              call('GET', `${VIEW_URL}/members/${stays.id}/time-entries?weekStart=${WEEK}`, token).its('status').should('eq', 200);
            });
            // Écran : le journal de l'ancien membre est refusé
            watchTeamView();
            cy.visit(`/equipe/membres/${leaver.id}?semaine=${WEEK}&equipe=${team.id}`);
            cy.wait('@memberEntries').its('response.statusCode').should('eq', 403);
            cy.getCy('message-membre-interdit').should('be.visible');
            cy.getCy('journal-semaine').should('not.exist');
          });
        });
      });
    });
  });

  it('CA-04-12 : un membre dont le compte est désactivé reste affiché avec « Inactif » et ses saisies restent consultables', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((inactive) => {
        createAccount('USER', 'Bob', 'Berg').then((active) => {
          createEntry(inactive, MEETING).then((entry) => {
            createEntry(active, FULL_DAY);
            createTeam(teamName('Produit'), manager, [inactive, active]).then(() => {
              adminToken().then((token) => call('POST', `${USERS_URL}/${inactive.id}/deactivation`, token).its('status').should('eq', 200));
              openTeamView(manager);
              cy.getCy(`ligne-membre-${inactive.id}`).should('be.visible');
              cy.getCy(`tag-inactif-${inactive.id}`).should('have.text', 'Inactif');
              cy.getCy(`tag-inactif-${active.id}`).should('not.exist');
              text(`total-membre-${inactive.id}`).should('eq', '1,5 h');
              cy.get('@summary').its('response.body.members').then((members) => {
                const flags = (members as MemberWeek[]).map((m) => [m.member.id, m.member.active]);
                expect(flags).to.deep.eq([
                  [inactive.id, false],
                  [active.id, true],
                ]);
              });
              cy.getCy(`lien-membre-${inactive.id}`).click();
              cy.wait('@memberEntries').its('response.statusCode').should('eq', 200);
              cy.getCy('titre-journal-membre').should('contain', 'Journal de Anna Abel');
              cy.getCy(`tag-inactif-${inactive.id}`).should('have.text', 'Inactif');
              cy.getCy(`saisie-${entry.id}`).should('be.visible');
              text('total-semaine').should('eq', '1,5 h');
            });
          });
        });
      });
    });
  });

  it('CA-05-12 : après un changement de manager par l’ADMIN, l’ancien manager reçoit 403 et ne voit plus l’équipe, le nouveau la voit', () => {
    createAccount('MANAGER', 'Ancien', 'Chef').then((oldManager) => {
      createAccount('MANAGER', 'Nouveau', 'Chef').then((newManager) => {
        createAccount('USER', 'Anna', 'Abel').then((member) => {
          createEntry(member, MEETING);
          const name = teamName('Produit');
          createTeam(name, oldManager, [member]).then((team) => {
            const weekUrl = `${VIEW_URL}/teams/${team.id}/week?weekStart=${WEEK}`;
            const memberUrl = `${VIEW_URL}/members/${member.id}/time-entries?weekStart=${WEEK}`;
            // Avant : seul l'ancien manager consulte
            tokenOf(oldManager).then((token) => {
              call('GET', weekUrl, token).its('status').should('eq', 200);
              call('GET', memberUrl, token).its('status').should('eq', 200);
            });
            tokenOf(newManager).then((token) => {
              call('GET', weekUrl, token).its('status').should('eq', 403);
              call('GET', memberUrl, token).its('status').should('eq', 403);
            });
            // Changement de manager par l'ADMIN
            updateTeam(team, name, newManager, [member]);
            // Après : API
            tokenOf(oldManager).then((token) => {
              call<Problem>('GET', weekUrl, token).then((response) => {
                expect(response.status).to.eq(403);
                expect(response.body.code).to.eq('RG-04-02');
              });
              call<Problem>('GET', memberUrl, token).then((response) => {
                expect(response.status).to.eq(403);
                expect(response.body.code).to.eq('RG-04-02');
              });
              call<ManagedTeam[]>('GET', `${VIEW_URL}/teams`, token).then((response) => {
                expect(response.status).to.eq(200);
                expect(response.body).to.deep.eq([]);
              });
            });
            tokenOf(newManager).then((token) => {
              call<TeamWeek>('GET', weekUrl, token).then((response) => {
                expect(response.status).to.eq(200);
                expect(response.body.members.map((m) => [m.member.id, m.totalMinutes])).to.deep.eq([[member.id, 90]]);
              });
              call('GET', memberUrl, token).its('status').should('eq', 200);
              call<ManagedTeam[]>('GET', `${VIEW_URL}/teams`, token).its('body').should('have.length', 1);
            });
            // Après : écran de l'ancien manager (il n'a plus d'équipe, un lien vers l'ancienne est refusé, le journal du membre aussi)
            loginAs(oldManager);
            watchTeamView();
            cy.visit(`/equipe?semaine=${WEEK}`);
            cy.wait('@teams').its('response.body').should('deep.eq', []);
            cy.getCy('message-aucune-equipe').should('be.visible');
            cy.getCy(`ligne-membre-${member.id}`).should('not.exist');
            cy.visit(`/equipe?equipe=${team.id}&semaine=${WEEK}`);
            cy.wait('@summary').its('response.statusCode').should('eq', 403);
            // Sans équipe, l'écran affiche « aucune équipe » (et non le message de refus) : l'ancienne équipe n'est pas consultable
            cy.getCy('message-aucune-equipe').should('be.visible');
            cy.getCy('table-equipe-semaine').should('not.exist');
            cy.visit(`/equipe/membres/${member.id}?semaine=${WEEK}`);
            cy.wait('@memberEntries').its('response.statusCode').should('eq', 403);
            cy.getCy('message-membre-interdit').should('be.visible');
            // Après : écran du nouveau manager
            openTeamView(newManager);
            cy.getCy('select-equipe').should('not.exist');
            text(`total-membre-${member.id}`).should('eq', '1,5 h');
            cy.getCy(`lien-membre-${member.id}`).click();
            cy.wait('@memberEntries').its('response.statusCode').should('eq', 200);
            cy.getCy('journal-semaine').should('be.visible');
          });
        });
      });
    });
  });

  it('RG-04-06, RG-04-09, RG-04-10 : membres triés par nom puis prénom, saisies anciennes d’un membre actuel visibles, saisies du manager absentes', () => {
    createAccount('MANAGER', 'Marc', 'Chef').then((manager) => {
      createAccount('USER', 'Zoe', 'Dupont').then((zoe) => {
        createAccount('USER', 'Anna', 'Dupont').then((anna) => {
          createAccount('USER', 'Bob', 'Abel').then((bob) => {
            createEntry(manager, MEETING); // saisie du manager : jamais dans la vue de son équipe
            createEntry(zoe, MEETING);
            createEntry(anna, { ...MEETING, startDate: '2026-03-02' }); // saisie antérieure à l'arrivée dans l'équipe
            createTeam(teamName('Produit'), manager, [zoe, anna, bob]).then((team) => {
              openTeamView(manager);
              cy.get('[data-cy^="ligne-membre-"]')
                .then((rows) => rows.toArray().map((row) => row.getAttribute('data-cy')))
                .should('deep.eq', [bob, anna, zoe].map((m) => `ligne-membre-${m.id}`)); // Abel Bob, Dupont Anna, Dupont Zoe
              cy.getCy(`ligne-membre-${manager.id}`).should('not.exist');
              cy.get('@summary').its('response.body.members').then((members) => {
                expect((members as MemberWeek[]).map((m) => m.member.id)).to.not.include(manager.id);
              });
              text(`total-membre-${zoe.id}`).should('eq', '1,5 h');
              text(`total-membre-${anna.id}`).should('eq', '0 h'); // sa saisie date de mars
              // Semaine ancienne (02/03/2026) : la saisie d'Anna, antérieure à son rattachement, est visible
              cy.visit(`/equipe?equipe=${team.id}&semaine=2026-03-02`);
              cy.wait('@summary').its('response.statusCode').should('eq', 200);
              text(`total-membre-${anna.id}`).should('eq', '1,5 h');
              cy.getCy(`duree-membre-${anna.id}-2026-03-02`).should('contain', '1,5 h');
            });
          });
        });
      });
    });
  });

  it('RG-04-04 : la navigation de semaine de E1 met la semaine dans l’URL et recharge les totaux', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER', 'Anna', 'Abel').then((member) => {
        createEntry(member, MEETING);
        createTeam(teamName('Produit'), manager, [member]).then(() => {
          openTeamView(manager);
          cy.getCy('btn-semaine-precedente').find('button').click();
          cy.wait('@summary').its('request.url').should('contain', 'weekStart=2026-09-28');
          cy.location('search').should('contain', 'semaine=2026-09-28');
          cy.getCy('libelle-semaine').should('contain', 'Semaine du 28/09/2026 au 04/10/2026');
          cy.getCy(`duree-membre-${member.id}-2026-09-28`).should('contain', '–');
          cy.getCy('btn-semaine-suivante').find('button').click();
          cy.wait('@summary').its('request.url').should('contain', 'weekStart=2026-10-05');
          text(`total-membre-${member.id}`).should('eq', '1,5 h');
          WEEK_DAYS.forEach((day) => cy.getCy(`colonne-jour-${day}`).should('exist'));
        });
      });
    });
  });
});
