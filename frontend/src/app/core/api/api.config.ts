import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Configuration } from './generated';

/**
 * Le contrat OpenAPI déclare le serveur `http://localhost` : on force un chemin de base vide
 * pour que les appels partent en relatif (`/api/v1/...`) et passent par le proxy de développement.
 */
export function provideApiBasePath(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: Configuration, useValue: new Configuration({ basePath: '' }) },
  ]);
}
