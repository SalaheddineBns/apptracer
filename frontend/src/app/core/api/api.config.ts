import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Configuration } from './generated';

/**
 * The OpenAPI contract declares the server `http://localhost`: the base path is forced to empty
 * so that calls are relative (`/api/v1/...`) and go through the development proxy.
 */
export function provideApiBasePath(): EnvironmentProviders {
  return makeEnvironmentProviders([
    { provide: Configuration, useValue: new Configuration({ basePath: '' }) },
  ]);
}
