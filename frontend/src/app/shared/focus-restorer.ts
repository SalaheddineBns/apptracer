import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/** Où se trouvait le focus : l'élément, et sa position dans sa ligne de tableau s'il en a une. */
export interface FocusAnchor {
  readonly element: HTMLElement;
  readonly row: string | null;
  readonly cell: number;
  readonly button: number;
}

const GIVE_UP_AFTER_MS = 5000;
const TOAST_GRACE_MS = 500;

/**
 * Rend le focus au bouton d'origine après une action, sans le laisser tomber sur `<body>`.
 * Le bouton est désactivé pendant l'appel puis, pour une désactivation / réactivation, remplacé par son
 * contraire : on attend donc qu'un bouton utilisable existe à la même place de la même ligne.
 * On renonce si l'utilisateur a déplacé le focus ailleurs entre-temps.
 */
@Injectable({ providedIn: 'root' })
export class FocusRestorer {
  private readonly document = inject(DOCUMENT);

  /** À appeler quand l'action est déclenchée (le focus est encore sur son bouton). */
  capture(): FocusAnchor | null {
    const element = this.document.activeElement;
    if (!(element instanceof HTMLElement) || element === this.document.body) {
      return null;
    }
    const row = element.closest('tr[data-cy]');
    const cell = element.closest('td');
    return {
      element,
      row: row?.getAttribute('data-cy') ?? null,
      cell: cell ? cell.cellIndex : -1,
      button: cell ? Array.from(cell.querySelectorAll('button')).indexOf(element as HTMLButtonElement) : -1,
    };
  }

  restore(anchor: FocusAnchor | null): void {
    if (!anchor) {
      return;
    }
    // Après la fermeture du dialogue (le piège à focus de PrimeNG reprend sinon le focus).
    setTimeout(() => {
      if (this.tryFocus(anchor)) {
        return;
      }
      const observer = new MutationObserver(() => {
        if (this.tryFocus(anchor)) {
          stop();
        }
      });
      const timer = setTimeout(() => stop(), GIVE_UP_AFTER_MS);
      const stop = () => {
        observer.disconnect();
        clearTimeout(timer);
      };
      observer.observe(this.document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ['disabled'] });
    });
  }

  /** Vrai quand il n'y a plus rien à faire : focus rendu, ou focus déplacé volontairement. */
  private tryFocus(anchor: FocusAnchor): boolean {
    const active = this.document.activeElement;
    const moved =
      active instanceof HTMLElement &&
      active !== this.document.body &&
      active !== anchor.element &&
      !active.closest('[role="dialog"], [role="alertdialog"], .p-toast');
    if (moved) {
      return true;
    }
    const target = this.find(anchor);
    if (!target) {
      return false;
    }
    target.focus();
    this.keepAgainstToast(target);
    return true;
  }

  /**
   * Le bouton de fermeture d'un toast porte `autofocus` (PrimeNG) : le premier toast affiché dans la page prend le
   * focus. Le toast de succès arrive juste après l'action, on reprend donc le focus s'il le vole.
   */
  private keepAgainstToast(target: HTMLElement): void {
    const onFocusIn = (event: FocusEvent) => {
      if (target.isConnected && event.target instanceof HTMLElement && event.target.closest('.p-toast')) {
        target.focus();
      }
    };
    this.document.addEventListener('focusin', onFocusIn);
    setTimeout(() => this.document.removeEventListener('focusin', onFocusIn), TOAST_GRACE_MS);
  }

  private find(anchor: FocusAnchor): HTMLElement | null {
    const usable = (element: HTMLElement | null | undefined): element is HTMLElement =>
      !!element && element.isConnected && !(element as HTMLButtonElement).disabled;
    if (anchor.element.isConnected) {
      return usable(anchor.element) ? anchor.element : null;
    }
    if (anchor.row === null) {
      return null;
    }
    const row = Array.from(this.document.querySelectorAll<HTMLTableRowElement>('tr[data-cy]')).find(
      (candidate) => candidate.getAttribute('data-cy') === anchor.row,
    );
    const button = row?.cells[anchor.cell]?.querySelectorAll('button')[anchor.button];
    return usable(button) ? button : null;
  }
}
