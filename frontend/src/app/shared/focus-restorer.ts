import { DOCUMENT } from '@angular/common';
import { inject, Injectable } from '@angular/core';

/** Where the focus was: the element, and its position in its table row if it has one. */
export interface FocusAnchor {
  readonly element: HTMLElement;
  readonly row: string | null;
  readonly cell: number;
  readonly button: number;
}

const GIVE_UP_AFTER_MS = 5000;
const TOAST_GRACE_MS = 500;

/**
 * Returns focus to the original button after an action, without letting it fall onto `<body>`.
 * The button is disabled during the call and then, for a deactivation / reactivation, replaced by its
 * opposite: so we wait until a usable button exists at the same place in the same row.
 * We give up if the user has moved the focus elsewhere in the meantime.
 */
@Injectable({ providedIn: 'root' })
export class FocusRestorer {
  private readonly document = inject(DOCUMENT);

  /** To be called when the action is triggered (the focus is still on its button). */
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
    // After the dialog closes (otherwise PrimeNG's focus trap takes the focus back).
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

  /** True when there is nothing left to do: focus returned, or focus moved on purpose. */
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
   * A toast's close button has `autofocus` (PrimeNG): the first toast shown in the page takes the
   * focus. The success toast arrives right after the action, so the focus is taken back if it steals it.
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
