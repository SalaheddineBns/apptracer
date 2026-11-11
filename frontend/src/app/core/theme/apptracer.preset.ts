import Aura from '@primeuix/themes/aura';
import { definePreset } from '@primeuix/themes';

/** AppTracer PrimeNG preset: Aura with the indigo primary colour (design system). */
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
      // AA contrast: white on indigo 500 is only 4.47:1, so light mode starts from 600. Aura 3.x defines these tokens
      // as `light-dark(light, dark)`: the dark value stays Aura's.
      color: 'light-dark({primary.600}, {primary.400})',
      contrastColor: 'light-dark(#ffffff, {surface.900})',
      hoverColor: 'light-dark({primary.700}, {primary.300})',
      activeColor: 'light-dark({primary.800}, {primary.200})',
    },
  },
  components: {
    // AA contrast of the error message (Aura token `message.error.color`, red.600 / red.500 by default).
    message: {
      error: { color: 'light-dark({red.700}, {red.400})' },
      // Contraste AA de l'avertissement (jeton Aura `message.warn.color` yellow.600 en clair : 2,84:1).
      warn: { color: 'light-dark({yellow.800}, {yellow.500})' },
    },
    // AA contrast of the danger button (white on red.500 = 3.76:1): light mode starts from 600, the dark value stays
    // Aura's.
    button: {
      root: {
        danger: {
          background: 'light-dark({red.600}, {red.400})',
          borderColor: 'light-dark({red.600}, {red.400})',
          hoverBackground: 'light-dark({red.700}, {red.300})',
          hoverBorderColor: 'light-dark({red.700}, {red.300})',
          activeBackground: 'light-dark({red.800}, {red.200})',
          activeBorderColor: 'light-dark({red.800}, {red.200})',
        },
      },
    },
    // AA contrast of the unselected option (Aura token `togglebutton.color` surface.500 in light mode: 4.34:1 on
    // surface.100): light mode starts from surface.600, the dark value stays Aura's. The active option is also outlined
    // in the primary colour, so that it stands out without comparing text colours (WCAG 1.4.1).
    togglebutton: {
      root: { color: 'light-dark({surface.600}, {surface.400})' },
      icon: { color: 'light-dark({surface.600}, {surface.400})' },
      content: {
        checkedShadow:
          '0 0 0 1px {primary.color}, 0px 1px 2px 0px rgba(0, 0, 0, 0.02), 0px 1px 2px 0px rgba(0, 0, 0, 0.04)',
      },
    },
    // AA contrast of the toast titles (Aura tokens `toast.success.color` green.600 and `toast.error.color` red.600 in
    // light mode).
    toast: {
      success: { color: 'light-dark({green.700}, {green.500})' },
      error: { color: 'light-dark({red.700}, {red.400})' },
    },
  },
});
