// SFD-02 : référentiels (types d'activité, projets). Un it() par critère d'acceptation couvert ici.
//
// Reportés à T-01-17 (ils exigent des saisies d'activité, qui n'existent pas avant SFD-01) : CA-02-07 et le
// scénario complet de CA-02-10 (409 RG-02-07 sur un type utilisé).
//
// Isolation : la base est partagée par tous les fichiers de test et les six types initiaux doivent rester vrais
// pour CA-02-01 et CA-02-02. Aucun test ne modifie, ne désactive ni ne réactive un type initial : chaque test
// crée ses propres types et projets par l'API, avec un code unique par exécution (RG-02-01 : [A-Z0-9_]{2,30}).

// Fichier traité comme un module : ses déclarations ne se mélangent pas à celles des autres fichiers e2e.
export {};

type Role = 'USER' | 'MANAGER' | 'ADMIN';
interface Credentials {
  email: string;
  password: string;
}
interface ActivityType {
  id: number;
  code: string;
  label: string;
  projectRequired: boolean;
  color: string;
  active: boolean;
  version: number;
}
interface Project {
  id: number;
  code: string;
  name: string;
  active: boolean;
  version: number;
}

const ROLES: Role[] = ['USER', 'MANAGER', 'ADMIN'];
const NON_ADMIN: Role[] = ['USER', 'MANAGER'];
const INITIAL_TYPES: Record<string, { label: string; projectRequired: boolean }> = {
  TRAVAIL_PROJET: { label: 'Travail projet', projectRequired: true },
  REUNION: { label: 'Réunion', projectRequired: false },
  LECTURE_SFD: { label: 'Lecture de SFD', projectRequired: false },
  REVUE_MR: { label: 'Revue de MR', projectRequired: true },
  FORMATION: { label: 'Formation', projectRequired: false },
  AUTRE: { label: 'Autre', projectRequired: false },
};

const runId = Math.random().toString(36).slice(2, 8).toUpperCase();
let sequence = 0;

/** Code valide (RG-02-01) et unique pour cette exécution et cet appel. */
function uniqueCode(prefix: string): string {
  sequence += 1;
  return `E2E_${prefix}_${runId}_${sequence}`;
}

function sharedCredentials(role: Role): Cypress.Chainable<Credentials> {
  return cy.env([`user_${role}`]).then((env) => env[`user_${role}`] as Credentials);
}

/** Jeton d'accès d'un compte de test partagé (connexion par API, lecture seule sur le compte). */
function tokenFor(role: Role): Cypress.Chainable<string> {
  return sharedCredentials(role).then((credentials) =>
    cy.request('POST', '/api/v1/auth/login', credentials).then((response) => response.body.accessToken as string),
  );
}

function bearer(token: string): { Authorization: string } {
  return { Authorization: `Bearer ${token}` };
}

function createType(overrides: Partial<ActivityType> = {}): Cypress.Chainable<ActivityType> {
  const body = { code: uniqueCode('T'), label: 'Type e2e', projectRequired: false, color: 'INFO', ...overrides };
  return tokenFor('ADMIN').then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/activity-types', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body as ActivityType;
    }),
  );
}

function createProject(overrides: Partial<Project> = {}): Cypress.Chainable<Project> {
  const body = { code: uniqueCode('P'), name: 'Projet e2e', ...overrides };
  return tokenFor('ADMIN').then((token) =>
    cy.request({ method: 'POST', url: '/api/v1/projects', headers: bearer(token), body }).then((response) => {
      expect(response.status).to.eq(201);
      return response.body as Project;
    }),
  );
}

function setProjectActive(id: number, active: boolean): Cypress.Chainable<number> {
  return tokenFor('ADMIN').then((token) =>
    cy
      .request({ method: 'POST', url: `/api/v1/projects/${id}/${active ? 'reactivation' : 'deactivation'}`, headers: bearer(token) })
      .its('status')
      .should('eq', 200),
  );
}

/** Liste lue par le rôle donné. */
function listAs<T>(role: Role, resource: 'activity-types' | 'projects'): Cypress.Chainable<T[]> {
  return tokenFor(role).then((token) =>
    cy.request({ url: `/api/v1/${resource}`, headers: bearer(token) }).then((response) => {
      expect(response.status).to.eq(200);
      return response.body as T[];
    }),
  );
}

describe('SFD-02 : référentiels', () => {
  it('CA-02-01 : un utilisateur connecté obtient les six types initiaux, actifs', () => {
    ROLES.forEach((role) => {
      listAs<ActivityType>(role, 'activity-types').then((types) => {
        Object.entries(INITIAL_TYPES).forEach(([code, expected]) => {
          const found = types.find((t) => t.code === code);
          expect(found, `${role} : type ${code} présent`).to.exist;
          expect(found?.active, `${role} : type ${code} actif`).to.eq(true);
          expect(found?.label, `${role} : libellé de ${code}`).to.eq(expected.label);
        });
      });
    });
    // L'écran ADMIN affiche aussi les six types initiaux.
    cy.login('ADMIN');
    cy.visit('/admin/types-activite');
    cy.getCy('table-types-activite').should('be.visible');
    listAs<ActivityType>('ADMIN', 'activity-types').then((types) => {
      Object.keys(INITIAL_TYPES).forEach((code) => {
        const id = types.find((t) => t.code === code)?.id;
        cy.getCy(`ligne-type-${id}`).should('contain', code);
        cy.getCy(`statut-type-${id}`).should('contain', 'Actif');
      });
    });
  });

  it('CA-02-02 : TRAVAIL_PROJET et REVUE_MR exigent un projet, les quatre autres non', () => {
    ROLES.forEach((role) => {
      listAs<ActivityType>(role, 'activity-types').then((types) => {
        Object.entries(INITIAL_TYPES).forEach(([code, expected]) => {
          expect(types.find((t) => t.code === code)?.projectRequired, `${role} : projectRequired de ${code}`).to.eq(expected.projectRequired);
        });
      });
    });
    cy.login('ADMIN');
    cy.visit('/admin/types-activite');
    listAs<ActivityType>('ADMIN', 'activity-types').then((types) => {
      Object.entries(INITIAL_TYPES).forEach(([code, expected]) => {
        const id = types.find((t) => t.code === code)?.id;
        cy.getCy(`projet-requis-type-${id}`).should('have.text', expected.projectRequired ? 'Oui' : 'Non');
      });
    });
  });

  it('CA-02-03 : un code déjà existant est refusé en 409 avec le code RG-02-01 (type et projet)', () => {
    cy.intercept('POST', '/api/v1/activity-types').as('createType');
    cy.intercept('POST', '/api/v1/projects').as('createProject');
    createType({ label: 'Type existant' }).then((existingType) => {
      createProject({ name: 'Projet existant' }).then((existingProject) => {
        cy.login('ADMIN');

        cy.visit('/admin/types-activite');
        cy.getCy('btn-nouveau-type').click();
        cy.getCy('dialogue-type-activite').should('be.visible');
        cy.getCy('champ-code-type').type(existingType.code);
        cy.getCy('champ-libelle-type').type('Doublon de type');
        cy.getCy('btn-enregistrer').click();
        cy.wait('@createType').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-02-01');
        });
        cy.getCy('erreur-champ-code-type').should('be.visible').and('contain', 'Ce code est déjà utilisé.');
        cy.getCy('dialogue-type-activite').should('be.visible'); // le dialogue reste ouvert

        cy.visit('/admin/projets');
        cy.getCy('btn-nouveau-projet').click();
        cy.getCy('dialogue-projet').should('be.visible');
        cy.getCy('champ-code-projet').type(existingProject.code);
        cy.getCy('champ-nom-projet').type('Doublon de projet');
        cy.getCy('btn-enregistrer').click();
        cy.wait('@createProject').then(({ response }) => {
          expect(response?.statusCode).to.eq(409);
          expect(response?.body.code).to.eq('RG-02-01');
        });
        cy.getCy('erreur-champ-code-projet').should('be.visible').and('contain', 'Ce code est déjà utilisé.');
        cy.getCy('dialogue-projet').should('be.visible');

        // Un type initial aussi : réponse de l'API seule, rien n'est modifié.
        tokenFor('ADMIN').then((token) => {
          cy.request({
            method: 'POST',
            url: '/api/v1/activity-types',
            headers: bearer(token),
            failOnStatusCode: false,
            body: { code: 'REUNION', label: 'Autre réunion', projectRequired: false, color: 'INFO' },
          }).then((response) => {
            expect(response.status).to.eq(409);
            expect(response.body.code).to.eq('RG-02-01');
          });
        });
      });
    });
  });

  it('CA-02-04 : le code « réunion client » met le champ en erreur et bloque l\'enregistrement (type et projet)', () => {
    cy.intercept('POST', '/api/v1/activity-types').as('createType');
    cy.intercept('POST', '/api/v1/projects').as('createProject');
    cy.login('ADMIN');

    cy.visit('/admin/types-activite');
    cy.getCy('btn-nouveau-type').click();
    cy.getCy('dialogue-type-activite').should('be.visible');
    cy.getCy('champ-code-type').type('réunion client');
    cy.getCy('champ-libelle-type').type('Réunion client');
    cy.getCy('btn-enregistrer').click();
    cy.getCy('erreur-champ-code-type').should('be.visible').and('contain', 'majuscules, chiffres et _');
    cy.getCy('dialogue-type-activite').should('be.visible');
    cy.get('@createType.all').should('have.length', 0);

    cy.visit('/admin/projets');
    cy.getCy('btn-nouveau-projet').click();
    cy.getCy('dialogue-projet').should('be.visible');
    cy.getCy('champ-code-projet').type('réunion client');
    cy.getCy('champ-nom-projet').type('Réunion client');
    cy.getCy('btn-enregistrer').click();
    cy.getCy('erreur-champ-code-projet').should('be.visible').and('contain', 'majuscules, chiffres et _');
    cy.getCy('dialogue-projet').should('be.visible');
    cy.get('@createProject.all').should('have.length', 0);

    // Le back refuse aussi le code tel que saisi (400, erreur sur le champ code), sans le transformer.
    tokenFor('ADMIN').then((token) => {
      [
        { url: '/api/v1/activity-types', body: { code: 'réunion client', label: 'Réunion client', projectRequired: false, color: 'INFO' } },
        { url: '/api/v1/projects', body: { code: 'réunion client', name: 'Réunion client' } },
      ].forEach(({ url, body }) => {
        cy.request({ method: 'POST', url, headers: bearer(token), body, failOnStatusCode: false }).then((response) => {
          expect(response.status).to.eq(400);
          expect(response.body.errors.map((e: { field: string }) => e.field)).to.include('code');
        });
      });
    });
  });

  it('CA-02-05 : le champ code n\'est pas modifiable à la modification d\'un type ou d\'un projet', () => {
    createType({ label: 'Type à modifier' }).then((type) => {
      createProject({ name: 'Projet à modifier' }).then((project) => {
        cy.login('ADMIN');

        cy.visit('/admin/types-activite');
        cy.getCy(`btn-modifier-type-${type.id}`).find('button').click();
        cy.getCy('dialogue-type-activite').should('be.visible');
        cy.getCy('champ-code-type').should('have.value', type.code).and('be.disabled');
        cy.getCy('champ-libelle-type').should('not.be.disabled').and('have.value', 'Type à modifier');
        cy.getCy('btn-annuler-dialogue').find('button').click();
        cy.getCy('dialogue-type-activite').should('not.exist');

        cy.visit('/admin/projets');
        cy.getCy(`btn-modifier-projet-${project.id}`).find('button').click();
        cy.getCy('dialogue-projet').should('be.visible');
        cy.getCy('champ-code-projet').should('have.value', project.code).and('be.disabled');
        cy.getCy('champ-nom-projet').should('not.be.disabled').and('have.value', 'Projet à modifier');
      });
    });
  });

  it('CA-02-06 : un projet désactivé disparaît de la liste du USER et reste visible (inactif) pour l\'ADMIN', () => {
    cy.intercept('POST', '/api/v1/projects/*/deactivation').as('deactivate');
    createProject({ name: 'Projet P1' }).then((project) => {
      listAs<Project>('USER', 'projects').then((projects) => {
        expect(projects.map((p) => p.id), 'avant : P1 proposé au USER').to.include(project.id);
      });

      cy.login('ADMIN');
      cy.visit('/admin/projets');
      cy.getCy(`btn-desactiver-projet-${project.id}`).find('button').click();
      cy.getCy('btn-confirmer').should('be.visible'); // E5 : confirmation avant désactivation
      cy.getCy('btn-confirmer').click();
      cy.wait('@deactivate').its('response.statusCode').should('eq', 200);
      cy.getCy('toast').should('contain', 'désactivé');
      cy.getCy(`statut-projet-${project.id}`).should('contain', 'Inactif');
      cy.getCy(`btn-reactiver-projet-${project.id}`).should('exist');

      NON_ADMIN.forEach((role) => {
        listAs<Project>(role, 'projects').then((projects) => {
          expect(projects.map((p) => p.id), `${role} : P1 absent`).not.to.include(project.id);
        });
      });
      listAs<Project>('ADMIN', 'projects').then((projects) => {
        const found = projects.find((p) => p.id === project.id);
        expect(found, 'ADMIN : P1 toujours listé').to.exist;
        expect(found?.active).to.eq(false);
      });
    });
  });

  it('CA-02-08 : un projet réactivé est de nouveau proposé aux USER', () => {
    cy.intercept('POST', '/api/v1/projects/*/reactivation').as('reactivate');
    createProject({ name: 'Projet réactivable' }).then((project) => {
      setProjectActive(project.id, false);
      listAs<Project>('USER', 'projects').then((projects) => {
        expect(projects.map((p) => p.id), 'avant : absent pour le USER').not.to.include(project.id);
      });

      cy.login('ADMIN');
      cy.visit('/admin/projets');
      cy.getCy(`statut-projet-${project.id}`).should('contain', 'Inactif');
      cy.getCy(`btn-reactiver-projet-${project.id}`).find('button').click();
      cy.wait('@reactivate').its('response.statusCode').should('eq', 200);
      cy.getCy('toast').should('contain', 'réactivé');
      cy.getCy(`statut-projet-${project.id}`).should('contain', 'Actif');

      NON_ADMIN.forEach((role) => {
        listAs<Project>(role, 'projects').then((projects) => {
          const found = projects.find((p) => p.id === project.id);
          expect(found, `${role} : projet de nouveau proposé`).to.exist;
          expect(found?.active).to.eq(true);
        });
      });
    });
  });

  it('CA-02-09 : USER et MANAGER reçoivent 403 sur les écritures et ne voient pas les menus « Types d\'activité » et « Projets »', () => {
    createType({ label: 'Type protégé' }).then((type) => {
      createProject({ name: 'Projet protégé' }).then((project) => {
        NON_ADMIN.forEach((role) => {
          cy.login(role);
          cy.visit('/journal');
          cy.getCy('nav-mon-journal').should('be.visible');
          cy.getCy('nav-types-activite').should('not.exist');
          cy.getCy('nav-projets').should('not.exist');

          const attemptedType = uniqueCode('X');
          const attemptedProject = uniqueCode('Y');
          tokenFor(role).then((token) => {
            const forbidden = (method: string, url: string, body?: Cypress.RequestBody) =>
              cy.request({ method, url, headers: bearer(token), body, failOnStatusCode: false }).its('status').should('eq', 403);
            forbidden('POST', '/api/v1/activity-types', { code: attemptedType, label: 'Interdit', projectRequired: false, color: 'INFO' });
            forbidden('PUT', `/api/v1/activity-types/${type.id}`, { label: 'Modifié', projectRequired: true, color: 'WARN', version: type.version });
            forbidden('POST', `/api/v1/activity-types/${type.id}/deactivation`);
            forbidden('POST', `/api/v1/activity-types/${type.id}/reactivation`);
            forbidden('POST', '/api/v1/projects', { code: attemptedProject, name: 'Interdit' });
            forbidden('PUT', `/api/v1/projects/${project.id}`, { name: 'Modifié', version: project.version });
            forbidden('POST', `/api/v1/projects/${project.id}/deactivation`);
            forbidden('POST', `/api/v1/projects/${project.id}/reactivation`);
          });

          // Rien n'a été modifié ni créé (vu par l'ADMIN).
          listAs<ActivityType>('ADMIN', 'activity-types').then((types) => {
            const found = types.find((t) => t.id === type.id);
            expect(found?.label).to.eq('Type protégé');
            expect(found?.active).to.eq(true);
            expect(types.map((t) => t.code)).not.to.include(attemptedType);
          });
          listAs<Project>('ADMIN', 'projects').then((projects) => {
            const found = projects.find((p) => p.id === project.id);
            expect(found?.name).to.eq('Projet protégé');
            expect(found?.active).to.eq(true);
            expect(projects.map((p) => p.code)).not.to.include(attemptedProject);
          });
        });

        // Contre-épreuve : l'ADMIN voit les deux menus.
        cy.login('ADMIN');
        cy.visit('/admin/comptes');
        cy.getCy('nav-types-activite').should('be.visible');
        cy.getCy('nav-projets').should('be.visible');
      });
    });
  });

  it('CA-02-11 : sans jeton, l\'API des référentiels répond 401', () => {
    createType({ label: 'Type sans jeton' }).then((type) => {
      createProject({ name: 'Projet sans jeton' }).then((project) => {
        const unauthorized = (method: string, url: string, body?: Cypress.RequestBody) =>
          cy.request({ method, url, body, failOnStatusCode: false }).its('status').should('eq', 401);
        unauthorized('GET', '/api/v1/activity-types');
        unauthorized('POST', '/api/v1/activity-types', { code: uniqueCode('N'), label: 'Anonyme', projectRequired: false, color: 'INFO' });
        unauthorized('PUT', `/api/v1/activity-types/${type.id}`, { label: 'Anonyme', projectRequired: false, color: 'INFO', version: type.version });
        unauthorized('POST', `/api/v1/activity-types/${type.id}/deactivation`);
        unauthorized('POST', `/api/v1/activity-types/${type.id}/reactivation`);
        unauthorized('GET', '/api/v1/projects');
        unauthorized('POST', '/api/v1/projects', { code: uniqueCode('N'), name: 'Anonyme' });
        unauthorized('PUT', `/api/v1/projects/${project.id}`, { name: 'Anonyme', version: project.version });
        unauthorized('POST', `/api/v1/projects/${project.id}/deactivation`);
        unauthorized('POST', `/api/v1/projects/${project.id}/reactivation`);
        // Un jeton invalide est refusé de la même façon.
        cy.request({ url: '/api/v1/projects', headers: bearer('invalide.jeton.expire'), failOnStatusCode: false })
          .its('status')
          .should('eq', 401);
      });
    });
  });

  // Hors critères d'acceptation : vérifie RG-02-10 (tri alphabétique) par l'API, pour les trois rôles.
  it('RG-02-10 : les listes sont triées par libellé (types) et par nom (projets)', () => {
    const labels = [`Zz ${runId} c`, `Zz ${runId} A`, `Zz ${runId} b`];
    labels.forEach((label) => {
      createType({ label });
      createProject({ name: label });
    });
    const expectedOrder = [`Zz ${runId} A`, `Zz ${runId} b`, `Zz ${runId} c`]; // insensible à la casse
    ROLES.forEach((role) => {
      listAs<ActivityType>(role, 'activity-types').then((types) => {
        expect(types.map((t) => t.label).filter((l) => l.startsWith(`Zz ${runId}`)), `${role} : types`).to.deep.eq(expectedOrder);
      });
      listAs<Project>(role, 'projects').then((projects) => {
        expect(projects.map((p) => p.name).filter((n) => n.startsWith(`Zz ${runId}`)), `${role} : projets`).to.deep.eq(expectedOrder);
      });
    });
  });
});
