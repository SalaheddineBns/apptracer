import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import { ApplicationConfig, LOCALE_ID, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { PRIMEUI_LICENSE } from '../environments/primeui-license';
import { provideApiBasePath } from './core/api/api.config';
import { errorInterceptor } from './core/errors/error.interceptor';
import { AppTracerPreset } from './core/theme/apptracer.preset';
import { routes } from './app.routes';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Ordre = du plus externe au plus interne. T-03-13 ajoutera l'intercepteur JWT APRÈS errorInterceptor.
    provideHttpClient(withInterceptors([errorInterceptor])),
    provideApiBasePath(),
    MessageService,
    ConfirmationService,
    { provide: LOCALE_ID, useValue: 'fr' },
    providePrimeNG({
      license: PRIMEUI_LICENSE,
      theme: {
        preset: AppTracerPreset,
        options: {
          darkModeSelector: '.app-dark',
          // Place les styles PrimeNG dans un layer CSS pour que les utilitaires Tailwind priment
          cssLayer: { name: 'primeng', order: 'theme, base, primeng' },
        },
      },
    }),
  ],
};
