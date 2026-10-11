// Génère src/environments/primeui-license.ts (ignoré par git) pour que la clé PrimeUI
// ne soit jamais commitée. Source : variable d'environnement PRIMEUI_LICENSE (CI),
// sinon le fichier local existant est conservé, sinon une clé vide est écrite.
import { existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const target = resolve(
  dirname(fileURLToPath(import.meta.url)),
  '../src/environments/primeui-license.ts',
);
const fromEnv = process.env.PRIMEUI_LICENSE?.trim();

if (!fromEnv && existsSync(target)) {
  process.exit(0);
}

if (fromEnv && !/^[\w.-]+$/.test(fromEnv)) {
  console.error('PRIMEUI_LICENSE : format de clé inattendu.');
  process.exit(1);
}

mkdirSync(dirname(target), { recursive: true });
writeFileSync(target, `export const PRIMEUI_LICENSE = '${fromEnv ?? ''}';\n`);

if (!fromEnv) {
  console.warn(
    'Clé PrimeUI absente : renseignez-la dans src/environments/primeui-license.ts ' +
      '(ou définissez PRIMEUI_LICENSE).',
  );
}
