// Pile e2e isolée et jetable : Postgres (conteneur), back (Spring Boot), front (ng serve), puis Cypress.
// Ne touche ni à la base de développement (5432), ni aux ports 4200 / 8080.
// Usage : npm run e2e:stack [-- <arguments passés à "cypress run">]
// Ports surchargeables : E2E_DB_PORT (55432), E2E_API_PORT (8081), E2E_WEB_PORT (4310).
// Le proxy du front (proxy.e2e.conf.json) cible 8081 : si E2E_API_PORT change, il est généré à la volée.
import { spawn, spawnSync } from 'node:child_process';
import { existsSync, readFileSync, writeFileSync, rmSync, mkdtempSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const frontendDir = resolve(dirname(fileURLToPath(import.meta.url)), '..');
const backendDir = resolve(frontendDir, '../backend');
const dbPort = process.env.E2E_DB_PORT ?? '55432';
const apiPort = process.env.E2E_API_PORT ?? '8081';
const webPort = process.env.E2E_WEB_PORT ?? '4310';
const container = `apptracer-e2e-db-${process.pid}`;
const api = `http://localhost:${apiPort}/api/v1`;
// Valeurs factices, non secrètes, réservées à cette pile jetable.
const db = { name: 'apptracer_e2e', user: 'apptracer_e2e', password: 'e2e-db-password' };
const jwtSecret = 'e2e-only-jwt-secret-not-a-real-secret-0123456789';

const credentialsFile = ['cypress.env.json', 'cypress.env.example.json']
  .map((f) => join(frontendDir, f))
  .find((f) => existsSync(f));
const creds = JSON.parse(readFileSync(credentialsFile, 'utf8'));

const children = [];
let tmpDir = null;
const log = (m) => console.log(`[e2e-stack] ${m}`);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function cleanup() {
  for (const child of children) {
    try {
      process.kill(-child.pid, 'SIGTERM'); // groupe de processus (mvnw -> java, ng -> node)
    } catch {
      /* déjà terminé */
    }
  }
  spawnSync('docker', ['rm', '-f', '-v', container], { stdio: 'ignore' });
  if (tmpDir) rmSync(tmpDir, { recursive: true, force: true });
}
for (const sig of ['SIGINT', 'SIGTERM', 'SIGHUP']) {
  process.on(sig, () => {
    cleanup();
    process.exit(130);
  });
}

function start(command, args, options) {
  const child = spawn(command, args, { detached: true, stdio: ['ignore', 'pipe', 'pipe'], ...options });
  children.push(child);
  child.output = '';
  for (const stream of [child.stdout, child.stderr]) {
    stream.on('data', (d) => (child.output = (child.output + d).slice(-20000)));
  }
  child.exited = new Promise((r) => child.on('exit', r));
  return child;
}

async function waitUntil(label, check, child, timeoutMs) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    if (child && child.exitCode !== null) {
      throw new Error(`${label} : processus arrêté prématurément\n${child.output}`);
    }
    if (await check().catch(() => false)) return;
    await sleep(1000);
  }
  throw new Error(`${label} : délai dépassé\n${child?.output ?? ''}`);
}

async function post(path, body, token) {
  return fetch(`${api}${path}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: JSON.stringify(body),
  });
}

/** Crée USER et MANAGER avec le jeton de l'ADMIN (idempotent : un compte qui se connecte déjà est ignoré). */
async function seedAccounts() {
  const adminLogin = await post('/auth/login', creds.user_ADMIN);
  if (!adminLogin.ok) throw new Error(`connexion ADMIN impossible (${adminLogin.status})`);
  const { accessToken } = await adminLogin.json();
  for (const role of ['USER', 'MANAGER']) {
    const { email, password } = creds[`user_${role}`];
    if ((await post('/auth/login', { email, password })).ok) continue;
    const res = await post(
      '/users',
      { email, firstName: role === 'USER' ? 'Ulysse' : 'Marion', lastName: `E2E-${role}`, role, initialPassword: password },
      accessToken,
    );
    if (!res.ok) throw new Error(`création du compte ${role} impossible (${res.status}) ${await res.text()}`);
    log(`compte ${role} créé`);
  }
}

let exitCode = 1;
try {
  log(`Postgres jetable ${container} sur le port ${dbPort}`);
  const run = spawnSync(
    'docker',
    [
      'run', '-d', '--rm', '--name', container, '-p', `127.0.0.1:${dbPort}:5432`,
      '-e', `POSTGRES_DB=${db.name}`, '-e', `POSTGRES_USER=${db.user}`, '-e', `POSTGRES_PASSWORD=${db.password}`,
      'postgres:16-alpine',
    ],
    { encoding: 'utf8' },
  );
  if (run.status !== 0) throw new Error(`docker run a échoué : ${run.stderr}`);
  await waitUntil(
    'Postgres',
    async () => spawnSync('docker', ['exec', container, 'pg_isready', '-U', db.user, '-d', db.name]).status === 0,
    null,
    60000,
  );
  await sleep(1500); // pg_isready répond avant la fin de l'initialisation (redémarrage interne)

  log(`back sur le port ${apiPort}`);
  const backend = start('./mvnw', ['-q', 'spring-boot:run'], {
    cwd: backendDir,
    env: {
      ...process.env,
      SERVER_PORT: apiPort,
      DB_HOST: 'localhost',
      DB_PORT: dbPort,
      POSTGRES_DB: db.name,
      POSTGRES_USER: db.user,
      POSTGRES_PASSWORD: db.password,
      JWT_SECRET: jwtSecret,
      ADMIN_EMAIL: creds.user_ADMIN.email,
      ADMIN_PASSWORD: creds.user_ADMIN.password,
      ADMIN_FIRST_NAME: 'Ada',
      ADMIN_LAST_NAME: 'E2E-ADMIN',
    },
  });
  await waitUntil('back', async () => (await post('/auth/login', creds.user_ADMIN)).ok, backend, 240000);
  await seedAccounts();

  let proxyConfig = 'proxy.e2e.conf.json';
  if (apiPort !== '8081') {
    tmpDir = mkdtempSync(join(tmpdir(), 'e2e-proxy-'));
    proxyConfig = join(tmpDir, 'proxy.json');
    writeFileSync(proxyConfig, JSON.stringify({ '/api': { target: `http://localhost:${apiPort}`, secure: false } }));
  }
  log(`front sur le port ${webPort}`);
  const front = start('npx', ['ng', 'serve', '--port', webPort, '--proxy-config', proxyConfig], {
    cwd: frontendDir,
    env: { ...process.env, NG_CLI_ANALYTICS: 'false' },
  });
  await waitUntil(
    'front',
    async () => {
      const res = await fetch(`http://localhost:${webPort}/`);
      return res.ok;
    },
    front,
    240000,
  );

  if (process.argv.includes('--serve')) {
    // Mode démonstration : la pile reste en marche pour un essai manuel, jusqu'à Ctrl+C.
    log(`pile prête : http://localhost:${webPort} (Ctrl+C pour arrêter)`);
    await new Promise(() => {});
  }

  log('Cypress');
  // Asynchrone (et non spawnSync) : tant que Cypress tourne, la boucle d'événements doit continuer à vider les tubes de sortie
  // du back et du front. Bloquée, elle laisse le tube (64 Ko) se remplir ; le front (ng serve), qui écrit ses journaux de
  // façon synchrone, se fige alors et toutes les requêtes passent en délai dépassé (constaté au-delà de quelques centaines de requêtes).
  const cypress = spawn('npx', ['cypress', 'run', ...process.argv.slice(2)], {
    cwd: frontendDir,
    stdio: 'inherit',
    env: { ...process.env, E2E_BASE_URL: `http://localhost:${webPort}` },
  });
  exitCode = await new Promise((resolve) => {
    cypress.on('error', () => resolve(1));
    cypress.on('exit', (code) => resolve(code ?? 1));
  });
} catch (error) {
  console.error(`[e2e-stack] ERREUR : ${error.message}`);
} finally {
  cleanup();
  log('pile arrêtée');
}
process.exit(exitCode);
