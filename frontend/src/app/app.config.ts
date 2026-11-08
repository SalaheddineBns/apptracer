import { registerLocaleData } from '@angular/common';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import localeFr from '@angular/common/locales/fr';
import {
  ApplicationConfig,
  inject,
  LOCALE_ID,
  provideAppInitializer,
  provideBrowserGlobalErrorListeners,
} from '@angular/core';
import { provideRouter } from '@angular/router';
import { ConfirmationService, MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { PRIMEUI_LICENSE } from '../environments/primeui-license';
import { provideApiBasePath } from './core/api/api.config';
import { authInterceptor } from './core/auth/auth.interceptor';
import { AuthStore } from './core/auth/auth.store';
import { errorInterceptor } from './core/errors/error.interceptor';
import { I18n } from './core/i18n/i18n.service';
import { languageInterceptor } from './core/i18n/language.interceptor';
import { AppTracerPreset } from './core/theme/apptracer.preset';
import { PRIMENG_FR } from './core/theme/primeng-fr';
import { routes } from './app.routes';

registerLocaleData(localeFr);

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    // Ordre = du plus externe au plus interne : l'intercepteur JWT renouvelle et rejoue avant que le 401 n'atteigne errorInterceptor.
    provideHttpClient(withInterceptors([errorInterceptor, languageInterceptor, authInterceptor])),
    // RG-08-05 : la langue (document, titre, PrimeNG) est appliquée avant le premier affichage.
    provideAppInitializer(() => void inject(I18n)),
    provideAppInitializer(() => inject(AuthStore).init()),
    provideApiBasePath(),
    MessageService,
    ConfirmationService,
    { provide: LOCALE_ID, useValue: 'fr' },
    providePrimeNG({
      license: PRIMEUI_LICENSE,
      translation: PRIMENG_FR,
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
