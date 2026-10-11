import { EnvironmentProviders } from '@angular/core';
import { provideApi } from './generated';

/**
 * Le contrat OpenAPI déclare le serveur `http://localhost` : on force un chemin de base vide
 * pour que les appels partent en relatif (`/api/v1/...`) et passent par le proxy de développement.
 */
export function provideApiBasePath(): EnvironmentProviders {
  return provideApi('');
}
