import { Directive, ElementRef, inject, input, OnInit, DestroyRef } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Arrows of a minutes `p-inputnumber`: only multiples of the step (15) between 0 and `max` (45), never 60 or 59.
 * PrimeNG cannot do this alone: `max` also clips the typed value (50 would silently become 45), while
 * without `max` the up arrow goes to 60. Keyboard arrows and buttons are therefore handled here, and the
 * component keeps its bounds open: a typed value stays as typed, and the form's validator judges it.
 */
@Directive({ selector: 'p-inputnumber[appQuarterHourStep]' })
export class QuarterHourStep implements OnInit {
  readonly step = input(15);
  readonly max = input(45);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly control = inject(NgControl);
  private readonly destroyRef = inject(DestroyRef);
  /** Last value set by an arrow: PrimeNG updates its display with a slight delay. */
  private pending: number | null = null;

  ngOnInit(): void {
    const onKeydown = (event: Event): void => {
      const key = (event as KeyboardEvent).key;
      if (key === 'ArrowUp' || key === 'ArrowDown') {
        this.stop(event);
        this.move(key === 'ArrowUp' ? 1 : -1);
      }
    };
    // Mouse and touch: PrimeNG spins the value as soon as it is pressed; we prevent that and act on click.
    const onPress = (event: Event): void => {
      if (this.direction(event) !== 0) {
        this.stop(event);
      }
    };
    const onClick = (event: Event): void => {
      const direction = this.direction(event);
      if (direction !== 0) {
        this.stop(event);
        this.move(direction);
      }
    };
    const handlers: [string, (event: Event) => void][] = [
      ['keydown', onKeydown],
      ['mousedown', onPress],
      ['touchstart', onPress],
      ['click', onClick],
    ];
    for (const [name, handler] of handlers) {
      // Capture: PrimeNG's handler, on the inner element, does not receive the event.
      this.host.addEventListener(name, handler, true);
    }
    this.destroyRef.onDestroy(() =>
      handlers.forEach(([name, handler]) => this.host.removeEventListener(name, handler, true)),
    );
  }

  private stop(event: Event): void {
    event.preventDefault();
    event.stopPropagation();
  }

  private direction(event: Event): 1 | -1 | 0 {
    const target = event.target as HTMLElement | null;
    if (target?.closest('.p-inputnumber-increment-button')) {
      return 1;
    }
    return target?.closest('.p-inputnumber-decrement-button') ? -1 : 0;
  }

  private move(direction: 1 | -1): void {
    const input = this.host.querySelector('input');
    if (!input || input.readOnly || input.disabled) {
      return;
    }
    const step = this.step();
    // Displayed value (typed or not yet validated): '10 min' gives 10, nothing readable gives 0. A press that closely
    // follows the previous one starts from the value already set, not from a lagging display.
    const digits = input.value.replace(/[^\d]/g, '');
    const displayed = digits === '' ? 0 : Number(digits);
    const current =
      this.pending !== null && this.control.control?.value === this.pending
        ? this.pending
        : displayed;
    const next =
      direction === 1
        ? Math.min(this.max(), Math.floor(current / step) * step + step)
        : Math.max(0, Math.ceil(current / step) * step - step);
    if (direction === 1 ? current >= this.max() : current === 0) {
      return;
    }
    this.pending = next;
    this.control.control?.setValue(next);
    this.control.control?.markAsDirty();
  }
}
