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
    // Order = from outermost to innermost: the JWT interceptor renews and replays before the 401 reaches
    // errorInterceptor.
    provideHttpClient(withInterceptors([errorInterceptor, languageInterceptor, authInterceptor])),
    // RG-08-05: the language (document, title, PrimeNG) is applied before the first render.
    provideAppInitializer(() => void inject(I18n)),
    provideAppInitializer(() => inject(AuthStore).init()),
    provideApiBasePath(),
    MessageService,
    ConfirmationService,
    // No locale-dependent Angular pipe (date, number…) is used: formats go through `Intl` and the language service
    // (RG-08-08). The framework's locale is the language's at start-up, so as not to leave a fixed fr.
    { provide: LOCALE_ID, useFactory: () => inject(I18n).lang() },
    providePrimeNG({
      license: PRIMEUI_LICENSE,
      // No `translation` here: the PrimeNG translation belongs to the `I18n` service, which sets it at start-up and on
      // each language change (RG-08-05). A fixed value would overwrite it after the initialiser. The `p-select` trigger
      // has a hard-coded `aria-label="dropdown trigger"` (English in both languages). It is redundant: the neighbouring
      // `combobox` field already carries the name, the open/closed state and the list. So it is hidden from screen
      // readers rather than translated.
      pt: { select: { dropdown: { 'aria-hidden': 'true' } } },
      theme: {
        preset: AppTracerPreset,
        options: {
          darkModeSelector: '.app-dark',
          // Puts the PrimeNG styles in a CSS layer so that the Tailwind utilities take precedence
          cssLayer: { name: 'primeng', order: 'theme, base, primeng' },
        },
      },
    }),
  ],
};
