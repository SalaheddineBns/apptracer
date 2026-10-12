import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/** Preset PrimeNG AppTracer : Aura avec la couleur primaire indigo (design system). */
export const AppTracerPreset = definePreset(Aura, {
  semantic: {
    primary: {
      50: '{indigo.50}',
      100: '{indigo.100}',
      200: '{indigo.200}',
      300: '{indigo.300}',
      400: '{indigo.400}',
      500: '{indigo.500}',
      600: '{indigo.600}',
      700: '{indigo.700}',
      800: '{indigo.800}',
      900: '{indigo.900}',
      950: '{indigo.950}',
      // Contraste AA : blanc sur indigo 500 ne fait que 4,47:1, le clair part de 600.
      // Aura 3.x définit ces jetons en `light-dark(clair, sombre)` : le sombre reste celui d'Aura.
      color: 'light-dark({primary.600}, {primary.400})',
      contrastColor: 'light-dark(#ffffff, {surface.900})',
      hoverColor: 'light-dark({primary.700}, {primary.300})',
      activeColor: 'light-dark({primary.800}, {primary.200})',
    },
  },
  components: {
    // Contraste AA du message d'erreur (jeton Aura `message.error.color`, par défaut red.600 / red.500).
    message: {
      error: { color: 'light-dark({red.700}, {red.400})' },
    },
  },
});
