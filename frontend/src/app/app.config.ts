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
    // Aucun pipe Angular dépendant de la locale (date, number…) n'est utilisé : les formats passent par `Intl` et le service de langue
    // (RG-08-08). La locale du framework est celle de la langue au démarrage, pour ne pas laisser un « fr » figé.
    { provide: LOCALE_ID, useFactory: () => inject(I18n).lang() },
    providePrimeNG({
      license: PRIMEUI_LICENSE,
      // Pas de `translation` ici : la traduction PrimeNG appartient au service `I18n`, qui la pose au démarrage et à chaque
      // changement de langue (RG-08-05). Une valeur fixe écraserait la sienne après l'initialiseur.
      // Le déclencheur de `p-select` porte un `aria-label="dropdown trigger"` codé en dur (anglais dans les deux langues).
      // Il est redondant : le champ `combobox` voisin porte déjà le nom, l'état ouvert/fermé et la liste. On le masque donc
      // aux lecteurs d'écran plutôt que de le traduire.
      pt: { select: { dropdown: { 'aria-hidden': 'true' } } },
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
