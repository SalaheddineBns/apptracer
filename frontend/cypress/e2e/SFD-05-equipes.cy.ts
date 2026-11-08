// SFD-05 : équipes. Un it() par critère d'acceptation, plus quelques règles qui n'ont pas de CA propre (RG-05-12, RG-05-13...).
//
// Isolation : la base est partagée par tous les fichiers de test et rien n'y est supprimé physiquement. Chaque test crée donc par
// l'API ses comptes (e-mail unique), ses équipes (nom unique par exécution) et n'utilise jamais les comptes partagés
// `user.e2e` / `manager.e2e` comme manager ou membre : RG-05-10 et RG-05-04 les bloqueraient pour les autres fichiers.
// Seul le compte ADMIN partagé sert (jeton, session) : il n'est jamais modifié.
//
// CA-05-12 : ce fichier vérifie le changement de manager (écran et API) ; la consultation par l'ancien et le nouveau manager
// est jouée dans SFD-04-vue-equipe.cy.ts (T-04-9).

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
  version: number;
}
interface TeamAccount {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  role: AccountRole;
  active: boolean;
}
interface Team {
  id: number;
  name: string;
  manager: TeamAccount;
  members: TeamAccount[];
  memberCount: number;
  version: number;
}
interface Candidate extends TeamAccount {
  currentTeam: { id: number; name: string } | null;
}
interface Problem {
  status: number;
  code?: string;
  detail?: string;
  errors?: { field: string; message: string }[];
}

const TEAMS_URL = '/api/v1/teams';
const USERS_URL = '/api/v1/users';
const PASSWORD = 'Equipes-Test-2026';

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

/**
 * Compte neuf. `lastName` commence par un chiffre, décroissant avec le temps : dans « Comptes » (tri par nom, 20 par page),
 * le dernier compte créé arrive en tête de liste, quel que soit le nombre de comptes laissés par les autres tests.
 */
function createAccount(role: AccountRole, firstName = 'Sasha', surname?: string): Cypress.Chainable<Account> {
  const email = `equipe.${role.toLowerCase()}.${unique()}@example.com`;
  const lastName = surname ?? `0${(1e13 - Date.now()).toString().padStart(14, '0')}`;
  const body = { email, firstName, lastName, role, initialPassword: PASSWORD };
  return adminToken().then((token) =>
    call<Account>('POST', USERS_URL, token, body).then((response) => {
      expect(response.status).to.eq(201);
      return { id: response.body.id, email, password: PASSWORD, role, firstName, lastName, version: response.body.version };
    }),
  );
}

function createTeam(name: string, managerId: number, memberIds: number[] = []): Cypress.Chainable<Team> {
  return adminToken().then((token) =>
    call<Team>('POST', TEAMS_URL, token, { name, managerId, memberIds }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body;
    }),
  );
}

function teamName(label = 'Produit'): string {
  return `${label} ${unique()}`;
}

function fullName(account: { firstName: string; lastName: string }): string {
  return `${account.firstName} ${account.lastName}`;
}

/** Session ouverte par API pour un compte créé par le test (mêmes clés de localStorage que `cy.login`). */
function loginAs(account: Credentials): void {
  cy.session(account.email, () => {
    cy.request('POST', '/api/v1/auth/login', { email: account.email, password: account.password }).then(({ body }) => {
      window.localStorage.setItem('accessToken', body.accessToken);
      window.localStorage.setItem('refreshToken', body.refreshToken);
    });
  });
}

/** Ouvre « Équipes » en ADMIN et attend la liste. */
function openTeams(): void {
  cy.login('ADMIN');
  cy.intercept('GET', TEAMS_URL).as('list');
  cy.visit('/admin/teams');
  cy.wait('@list').its('response.statusCode').should('eq', 200);
  cy.getCy('btn-nouvelle-equipe').should('be.visible'); // la liste peut être vide : pas de table
}

/** Clique le bouton interne d'un `p-button` (le data-cy est posé sur l'hôte). */
function press(selector: string): void {
  cy.getCy(selector).find('button').scrollIntoView({ offset: { top: -250, left: 0 } }).click(); // hors de la bannière de licence fixée en bas à droite
}

/** Attend les deux listes de candidats que le dialogue relit à chaque ouverture. */
function awaitCandidates(): void {
  cy.wait('@managers').its('response.statusCode').should('eq', 200);
  cy.wait('@members').its('response.statusCode').should('eq', 200);
  cy.getCy('form-equipe').should('be.visible');
}

function watchCandidates(): void {
  cy.intercept('GET', `${TEAMS_URL}/manager-candidates`).as('managers');
  cy.intercept('GET', `${TEAMS_URL}/member-candidates`).as('members');
}

function openCreateDialog(): void {
  watchCandidates();
  press('btn-nouvelle-equipe');
  awaitCandidates();
}

function openEditDialog(team: Team): void {
  watchCandidates();
  press(`btn-modifier-equipe-${team.id}`);
  awaitCandidates();
}

function chooseManager(account: { id: number }): void {
  cy.getCy('champ-manager').click();
  cy.getCy(`option-manager-${account.id}`).click();
}

/** Ouvre la liste des membres par son bouton (un clic au centre du champ pourrait tomber sur la croix d'une pastille). */
function openMembers(): void {
  cy.getCy('btn-ouvrir-membres').click();
}

/**
 * Enregistre par la touche Entrée dans le champ nom (SFD-05 E2). Sous Cypress la liste des membres ne se referme ni par un clic
 * ailleurs, ni par son bouton, ni par Échap, et elle recouvre « Enregistrer » : le bouton n'est donc pas cliquable ensuite.
 */
function submitByEnter(): void {
  cy.getCy('champ-nom-equipe').type('{enter}');
}

function chooseMember(account: { id: number }): void {
  cy.getCy(`option-membre-${account.id}`).click();
}

function save(): void {
  press('btn-enregistrer');
}

describe('SFD-05 : équipes', () => {
  // Fenêtre haute : la liste n'est pas paginée et grossit avec les équipes laissées par les tests ; une fenêtre basse mettrait
  // les dernières lignes sous la bannière « Invalid PrimeUI License » fixée en bas à droite.
  beforeEach(() => {
    cy.viewport(1280, 1800);
  });

  it('CA-05-01 : un ADMIN crée une équipe avec un manager et deux membres, elle apparaît avec son manager et « 2 membres »', () => {
    createAccount('MANAGER', 'Maya').then((manager) => {
      createAccount('USER', 'Ugo').then((user) => {
        createAccount('MANAGER', 'Marc').then((peer) => {
          const name = teamName('Produit');
          openTeams();
          cy.intercept('POST', TEAMS_URL).as('create');
          openCreateDialog();
          cy.getCy('champ-nom-equipe').type(name);
          chooseManager(manager);
          openMembers();
          chooseMember(user);
          chooseMember(peer); // un MANAGER peut être membre d'une autre équipe (RG-05-05)
          submitByEnter();
          cy.wait('@create').then(({ request, response }) => {
            expect(request.body).to.deep.eq({ name, managerId: manager.id, memberIds: [user.id, peer.id] });
            expect(response?.statusCode).to.eq(201);
            const team = response?.body as Team;
            expect(team.memberCount).to.eq(2);
            cy.getCy('toast').should('contain', 'Équipe créée');
            cy.getCy('form-equipe').should('not.exist');
            cy.getCy(`ligne-equipe-${team.id}`).within(() => {
              cy.getCy(`nom-equipe-${team.id}`).should('have.text', name);
              cy.getCy(`manager-equipe-${team.id}`).should('have.text', fullName(manager));
              cy.getCy(`membres-equipe-${team.id}`).should('have.text', '2 membres');
            });
            adminToken().then((token) =>
              call<Team>('GET', `${TEAMS_URL}/${team.id}`, token).then((read) => {
                expect(read.status).to.eq(200);
                expect(read.body.members.map((m) => m.id)).to.have.members([user.id, peer.id]);
                expect(read.body.manager.id).to.eq(manager.id);
              }),
            );
          });
        });
      });
    });
  });

  it('CA-05-02 : une équipe « PRODUIT » après « Produit » est refusée (409, RG-05-01) et le champ nom est en erreur', () => {
    createAccount('MANAGER').then((manager) => {
      const name = teamName('Produit');
      createTeam(name, manager.id).then(() => {
        // API : même nom, autre casse
        adminToken().then((token) =>
          call<Problem>('POST', TEAMS_URL, token, { name: name.toUpperCase(), managerId: manager.id, memberIds: [] }).then((response) => {
            expect(response.status).to.eq(409);
            expect(response.body.code).to.eq('RG-05-01');
            expect(response.body.errors?.map((e) => e.field)).to.include('name');
          }),
        );
        // Écran
        openTeams();
        cy.intercept('POST', TEAMS_URL).as('create');
        openCreateDialog();
        cy.getCy('champ-nom-equipe').type(name.toUpperCase());
        chooseManager(manager);
        save();
        cy.wait('@create').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-05-01');
        });
        cy.getCy('erreur-champ-nom-equipe').should('be.visible');
        cy.getCy('form-equipe').should('be.visible');
      });
    });
  });

  it('CA-05-03 : un compte USER désigné manager par l’API est refusé (409, RG-05-02) et n’est pas proposé dans les managers', () => {
    createAccount('USER').then((user) => {
      createAccount('MANAGER').then((manager) => {
        adminToken().then((token) =>
          call<Problem>('POST', TEAMS_URL, token, { name: teamName(), managerId: user.id, memberIds: [] }).then((response) => {
            expect(response.status).to.eq(409);
            expect(response.body.code).to.eq('RG-05-02');
            expect(response.body.errors?.map((e) => e.field)).to.include('managerId');
          }),
        );
        openTeams();
        openCreateDialog();
        cy.getCy('champ-manager').click();
        cy.getCy(`option-manager-${manager.id}`).should('exist'); // la liste est ouverte et contient un MANAGER actif
        cy.getCy(`option-manager-${user.id}`).should('not.exist');
      });
    });
  });

  it('CA-05-04 : un compte déjà membre de l’équipe A, ajouté à l’équipe B, est refusé (409, RG-05-04) avec le nom de A', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER').then((member) => {
        const nameA = teamName('Equipe A');
        const nameB = teamName('Equipe B');
        createTeam(nameA, manager.id, [member.id]).then(() => {
          createTeam(nameB, manager.id).then((teamB) => {
            // API
            adminToken().then((token) =>
              call<Problem>('PUT', `${TEAMS_URL}/${teamB.id}`, token, {
                name: nameB,
                managerId: manager.id,
                memberIds: [member.id],
                version: teamB.version,
              }).then((response) => {
                expect(response.status).to.eq(409);
                expect(response.body.code).to.eq('RG-05-04');
                expect(response.body.detail).to.contain(nameA);
              }),
            );
            // Écran : le compte est signalé dans la liste, puis le refus s'affiche sous le champ membres
            openTeams();
            cy.intercept('PUT', `${TEAMS_URL}/${teamB.id}`).as('update');
            openEditDialog(teamB);
            openMembers();
            cy.getCy(`option-membre-${member.id}`).should('contain', `Déjà dans l’équipe ${nameA}`);
            chooseMember(member);
            submitByEnter();
            cy.wait('@update').then(({ response }) => {
              expect(response?.statusCode).to.eq(409);
              expect(response?.body.code).to.eq('RG-05-04');
            });
            cy.getCy('erreur-champ-membres').should('contain', nameA);
            cy.getCy('form-equipe').should('be.visible');
          });
        });
      });
    });
  });

  it('CA-05-05 : un compte ADMIN ajouté comme membre par l’API est refusé (409, RG-05-03) et n’est pas proposé dans les membres', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('ADMIN').then((admin) => {
        createAccount('USER').then((user) => {
          adminToken().then((token) =>
            call<Problem>('POST', TEAMS_URL, token, { name: teamName(), managerId: manager.id, memberIds: [admin.id] }).then((response) => {
              expect(response.status).to.eq(409);
              expect(response.body.code).to.eq('RG-05-03');
              expect(response.body.errors?.map((e) => e.field)).to.include('memberIds');
            }),
          );
          openTeams();
          openCreateDialog();
          openMembers();
          cy.getCy(`option-membre-${user.id}`).should('exist'); // la liste est ouverte et contient un USER
          cy.getCy(`option-membre-${admin.id}`).should('not.exist');
        });
      });
    });
  });

  it('CA-05-06 : le manager de l’équipe A ajouté comme membre de A est refusé (409, RG-05-05)', () => {
    createAccount('MANAGER').then((manager) => {
      const name = teamName();
      createTeam(name, manager.id).then((team) => {
        // API
        adminToken().then((token) =>
          call<Problem>('PUT', `${TEAMS_URL}/${team.id}`, token, {
            name,
            managerId: manager.id,
            memberIds: [manager.id],
            version: team.version,
          }).then((response) => {
            expect(response.status).to.eq(409);
            expect(response.body.code).to.eq('RG-05-05');
            expect(response.body.errors?.map((e) => e.field)).to.include('memberIds');
          }),
        );
        // Écran : le manager de l'équipe n'est pas proposé parmi les membres, l'écran ne peut donc pas produire ce refus
        createAccount('USER').then((user) => {
          openTeams();
          openEditDialog(team);
          openMembers();
          cy.getCy(`option-membre-${user.id}`).should('exist'); // la liste est ouverte et contient un USER
          cy.getCy(`option-membre-${manager.id}`).should('not.exist');
        });
      });
    });
  });

  it('Hors CA (RG-05-05) : choisir comme manager un compte déjà coché comme membre le décoche et en informe', () => {
    createAccount('MANAGER', 'Mina').then((peer) => {
      openTeams();
      openCreateDialog();
      openMembers();
      chooseMember(peer);
      chooseManager(peer);
      cy.getCy('info-membre-retire').should('contain', fullName(peer));
    });
  });

  it('CA-05-07 : désactiver le manager d’une équipe est refusé (409, RG-05-10), le toast nomme l’équipe', () => {
    createAccount('MANAGER').then((manager) => {
      const name = teamName('Direction');
      createTeam(name, manager.id).then(() => {
        cy.login('ADMIN');
        cy.intercept('GET', `${USERS_URL}?*`).as('users');
        cy.intercept('POST', `${USERS_URL}/${manager.id}/deactivation`).as('deactivate');
        cy.visit('/admin/accounts');
        cy.wait('@users');
        cy.getCy(`statut-compte-${manager.id}`).should('contain', 'Actif');
        press(`btn-desactiver-${manager.id}`);
        cy.getCy('btn-confirmer').click();
        cy.wait('@deactivate').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-05-10');
          expect(response?.body.detail).to.contain(`« ${name} »`);
        });
        cy.getCy('toast').should('contain', `« ${name} »`);
        cy.getCy(`statut-compte-${manager.id}`).should('contain', 'Actif');
      });
    });
  });

  it('CA-05-08 : retirer le rôle MANAGER au manager d’une équipe est refusé (409, RG-05-10), le refus s’affiche sous le champ rôle', () => {
    createAccount('MANAGER').then((manager) => {
      const name = teamName('Direction');
      createTeam(name, manager.id).then(() => {
        cy.login('ADMIN');
        cy.intercept('GET', `${USERS_URL}?*`).as('users');
        cy.intercept('PUT', `${USERS_URL}/${manager.id}`).as('update');
        cy.visit('/admin/accounts');
        cy.wait('@users');
        press(`btn-modifier-${manager.id}`);
        cy.getCy('form-compte').should('be.visible');
        cy.getCy('champ-role').click();
        cy.contains('[role="option"]', 'Utilisateur').click();
        save();
        cy.wait('@update').then(({ request, response }) => {
          expect(request.body.role).to.eq('USER');
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-05-10');
          expect(response?.body.detail).to.contain(`« ${name} »`);
        });
        cy.getCy('erreur-champ-role').should('contain', 'MANAGER');
        cy.getCy('form-compte').should('be.visible');
        // Le compte est resté MANAGER
        adminToken().then((token) =>
          call<Account>('GET', `${USERS_URL}/${manager.id}`, token).then((read) => {
            expect(read.body.role).to.eq('MANAGER');
          }),
        );
      });
    });
  });

  it('CA-05-09 : supprimer une équipe de trois membres après confirmation la retire de la liste, les trois comptes existent toujours sans équipe', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER').then((a) => {
        createAccount('USER').then((b) => {
          createAccount('MANAGER').then((c) => {
            const name = teamName('A supprimer');
            createTeam(name, manager.id, [a.id, b.id, c.id]).then((team) => {
              openTeams();
              cy.getCy(`membres-equipe-${team.id}`).should('have.text', '3 membres');
              cy.intercept('DELETE', `${TEAMS_URL}/${team.id}`).as('remove');
              press(`btn-supprimer-equipe-${team.id}`);
              cy.get('[role="alertdialog"]').should('contain', `« ${name} »`).and('contain', '3 membres');
              cy.getCy('btn-confirmer').click();
              cy.wait('@remove').its('response.statusCode').should('eq', 204);
              cy.getCy('toast').should('contain', 'Équipe supprimée');
              cy.getCy(`ligne-equipe-${team.id}`).should('not.exist');
              adminToken().then((token) => {
                call('GET', `${TEAMS_URL}/${team.id}`, token).its('status').should('eq', 404);
                [a, b, c].forEach((member) => {
                  call<Account & { active: boolean }>('GET', `${USERS_URL}/${member.id}`, token).then((read) => {
                    expect(read.status).to.eq(200);
                    expect(read.body.active).to.eq(true);
                  });
                });
                call<Candidate[]>('GET', `${TEAMS_URL}/member-candidates`, token).then((read) => {
                  [a, b, c].forEach((member) => {
                    const candidate = read.body.find((x) => x.id === member.id);
                    expect(candidate, `compte ${member.id}`).to.exist;
                    expect(candidate?.currentTeam).to.eq(null);
                  });
                });
                // RG-05-09 / H8 : le nom redevient disponible
                call('POST', TEAMS_URL, token, { name, managerId: manager.id, memberIds: [] })
                  .its('status')
                  .should('eq', 201);
              });
            });
          });
        });
      });
    });
  });

  it('CA-05-10 : le membre dont le compte est désactivé reste dans l’équipe, signalé comme inactif', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER').then((member) => {
        createAccount('USER').then((other) => {
          createTeam(teamName(), manager.id, [member.id, other.id]).then((team) => {
            adminToken().then((token) =>
              call('POST', `${USERS_URL}/${member.id}/deactivation`, token).then((response) => {
                expect(response.status).to.eq(200);
                call<Team>('GET', `${TEAMS_URL}/${team.id}`, token).then((read) => {
                  expect(read.body.memberCount).to.eq(2);
                  expect(read.body.members.find((m) => m.id === member.id)?.active).to.eq(false);
                  expect(read.body.members.find((m) => m.id === other.id)?.active).to.eq(true);
                });
              }),
            );
            openTeams();
            cy.getCy(`membres-equipe-${team.id}`).should('have.text', '2 membres');
            openEditDialog(team);
            openMembers();
            cy.getCy(`option-membre-${member.id}`).should('contain', 'Inactif');
            cy.getCy(`option-membre-${other.id}`).should('not.contain', 'Inactif');
          });
        });
      });
    });
  });

  it('CA-05-11 : un USER et un MANAGER reçoivent 403 sur toute l’API des équipes et ne voient pas le menu « Équipes »', () => {
    createAccount('MANAGER').then((teamManager) => {
      createTeam(teamName(), teamManager.id).then((team) => {
        (['USER', 'MANAGER'] as AccountRole[]).forEach((role) => {
          createAccount(role).then((account) => {
            tokenOf(account).then((token) => {
              const forbidden = (method: string, url: string, body?: object): void => {
                call(method, url, token, body).its('status').should('eq', 403);
              };
              const payload = { name: teamName(), managerId: teamManager.id, memberIds: [] };
              forbidden('GET', TEAMS_URL);
              forbidden('POST', TEAMS_URL, payload);
              forbidden('GET', `${TEAMS_URL}/${team.id}`);
              forbidden('PUT', `${TEAMS_URL}/${team.id}`, { ...payload, version: team.version });
              forbidden('DELETE', `${TEAMS_URL}/${team.id}`);
              forbidden('GET', `${TEAMS_URL}/manager-candidates`);
              forbidden('GET', `${TEAMS_URL}/member-candidates`);
            });
            loginAs(account);
            cy.visit('/journal');
            cy.getCy('titre-journal').should('be.visible');
            cy.getCy('nav-mon-journal').should('be.visible');
            cy.getCy('nav-equipes').should('not.exist');
            cy.visit('/admin/teams');
            cy.location('pathname').should('eq', '/journal'); // roleGuard : retour à l'accueil du rôle
            cy.getCy('table-equipes').should('not.exist');
          });
        });
        // L'équipe n'a pas été touchée par les tentatives
        adminToken().then((token) =>
          call<Team>('GET', `${TEAMS_URL}/${team.id}`, token).then((read) => {
            expect(read.status).to.eq(200);
            expect(read.body.version).to.eq(team.version);
          }),
        );
        // Contre-épreuve : l'ADMIN voit le menu
        openTeams();
        cy.getCy('nav-equipes').should('be.visible');
      });
    });
  });

  it('CA-05-11 (non connecté) : l’API des équipes répond 401 sans jeton ou avec un jeton invalide', () => {
    call('GET', TEAMS_URL, null).its('status').should('eq', 401);
    call('POST', TEAMS_URL, null, { name: 'Sans jeton', managerId: 1, memberIds: [] }).its('status').should('eq', 401);
    call('DELETE', `${TEAMS_URL}/1`, null).its('status').should('eq', 401);
    call('GET', `${TEAMS_URL}/member-candidates`, 'jeton-invalide').its('status').should('eq', 401);
  });

  it('CA-05-12 : un ADMIN change le manager d’une équipe, le changement est enregistré (consultation par les managers : reportée à T-04-9)', () => {
    createAccount('MANAGER', 'Ancien').then((oldManager) => {
      createAccount('MANAGER', 'Nouveau').then((newManager) => {
        createAccount('USER').then((member) => {
          createTeam(teamName(), oldManager.id, [member.id]).then((team) => {
            openTeams();
            cy.getCy(`manager-equipe-${team.id}`).should('have.text', fullName(oldManager));
            cy.intercept('PUT', `${TEAMS_URL}/${team.id}`).as('update');
            openEditDialog(team);
            chooseManager(newManager);
            save();
            cy.wait('@update').then(({ request, response }) => {
              expect(request.body.managerId).to.eq(newManager.id);
              expect(request.body.version).to.eq(team.version);
              expect(response?.statusCode).to.eq(200);
            });
            cy.getCy('toast').should('contain', 'Équipe mise à jour');
            cy.getCy(`manager-equipe-${team.id}`).should('have.text', fullName(newManager));
            cy.getCy(`membres-equipe-${team.id}`).should('have.text', '1 membre');
            adminToken().then((token) =>
              call<Team>('GET', `${TEAMS_URL}/${team.id}`, token).then((read) => {
                expect(read.body.manager.id).to.eq(newManager.id);
                expect(read.body.members.map((m) => m.id)).to.deep.eq([member.id]);
                expect(read.body.version).to.be.greaterThan(team.version);
              }),
            );
            // L'ancien manager n'est plus manager d'aucune équipe : RG-05-10 ne le bloque plus
            adminToken().then((token) => call('POST', `${USERS_URL}/${oldManager.id}/deactivation`, token).its('status').should('eq', 200));
          });
        });
      });
    });
  });

  it('RG-05-12 : un membre dont le rôle devient ADMIN est retiré de son équipe', () => {
    createAccount('MANAGER').then((manager) => {
      createAccount('USER').then((promoted) => {
        createAccount('USER').then((stays) => {
          createTeam(teamName(), manager.id, [promoted.id, stays.id]).then((team) => {
            adminToken().then((token) =>
              call('PUT', `${USERS_URL}/${promoted.id}`, token, {
                firstName: promoted.firstName,
                lastName: promoted.lastName,
                role: 'ADMIN',
                version: promoted.version,
              }).then((response) => {
                expect(response.status).to.eq(200);
                call<Team>('GET', `${TEAMS_URL}/${team.id}`, token).then((read) => {
                  expect(read.body.members.map((m) => m.id)).to.deep.eq([stays.id]);
                  expect(read.body.memberCount).to.eq(1);
                });
              }),
            );
            openTeams();
            cy.getCy(`membres-equipe-${team.id}`).should('have.text', '1 membre');
          });
        });
      });
    });
  });


  it('RG-05-01, RG-05-06, RG-05-07, RG-05-13 : nom de 2 à 100 caractères, un manager sur plusieurs équipes, équipe sans membre, listes triées', () => {
    createAccount('MANAGER').then((manager) => {
      const suffix = unique();
      createAccount('USER', 'Zoe', `Dupont${suffix}`).then((zoe) => {
        createAccount('USER', 'Anna', `Dupont${suffix}`).then((anna) => {
          createAccount('USER', 'Bob', `Abel${suffix}`).then((bob) => {
            const prefix = `Tri${unique()}`;
            adminToken().then((token) => {
              // RG-05-01 : longueur (1 et 101 refusés, 100 accepté)
              const base = { managerId: manager.id, memberIds: [] };
              call<Problem>('POST', TEAMS_URL, token, { ...base, name: 'A' }).then((r) => {
                expect(r.status).to.eq(400);
                expect(r.body.errors?.map((e) => e.field)).to.include('name');
              });
              call('POST', TEAMS_URL, token, { ...base, name: 'x'.repeat(101) }).its('status').should('eq', 400);
              call<Team>('POST', TEAMS_URL, token, { ...base, name: unique().padEnd(100, 'q') }).then((r) => {
                expect(r.status).to.eq(201);
                expect(r.body.name).to.have.length(100);
              });
              // RG-05-06 : trois équipes pour le même manager ; RG-05-07 : « a » n'a aucun membre
              createTeam(`${prefix} b`, manager.id, [zoe.id, anna.id, bob.id]).then((teamB) => {
                createTeam(`${prefix} C`, manager.id).then(() => {
                  createTeam(`${prefix} a`, manager.id).then((teamA) => {
                    expect(teamA.memberCount).to.eq(0);
                    call<Team[]>('GET', TEAMS_URL, token).then((list) => {
                      const ours = list.body.filter((t) => t.name.startsWith(prefix));
                      // RG-05-13 : tri par nom sans tenir compte de la casse
                      expect(ours.map((t) => t.name)).to.deep.eq([`${prefix} a`, `${prefix} b`, `${prefix} C`]);
                      expect(ours.every((t) => t.manager.id === manager.id)).to.eq(true);
                      // RG-05-13 : membres par nom puis prénom
                      const members = list.body.find((t) => t.id === teamB.id)?.members ?? [];
                      expect(members.map((m) => m.id)).to.deep.eq([bob.id, anna.id, zoe.id]);
                    });
                  });
                });
              });
            });
          });
        });
      });
    });
  });

  it('Conflit : une équipe modifiée entre-temps renvoie 409 sans code et l’écran recharge la liste', () => {
    createAccount('MANAGER').then((manager) => {
      const name = teamName();
      createTeam(name, manager.id).then((team) => {
        openTeams();
        openEditDialog(team);
        // Une autre session modifie l'équipe : la version du dialogue est périmée
        adminToken().then((token) =>
          call('PUT', `${TEAMS_URL}/${team.id}`, token, { name: `${name} bis`, managerId: manager.id, memberIds: [], version: team.version })
            .its('status')
            .should('eq', 200),
        );
        cy.intercept('PUT', `${TEAMS_URL}/${team.id}`).as('update');
        cy.getCy('champ-nom-equipe').clear();
        cy.getCy('champ-nom-equipe').type(`${name} ter`);
        save();
        cy.wait('@update').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code ?? null).to.eq(null);
          cy.getCy('toast').should('contain', response?.body.detail as string);
        });
        cy.getCy('form-equipe').should('not.exist');
        cy.getCy(`nom-equipe-${team.id}`).should('have.text', `${name} bis`);
      });
    });
  });
});
