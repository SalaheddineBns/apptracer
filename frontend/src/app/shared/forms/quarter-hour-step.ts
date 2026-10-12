import { Directive, ElementRef, inject, input, OnInit, DestroyRef } from '@angular/core';
import { NgControl } from '@angular/forms';

/**
 * Flèches d'un `p-inputnumber` de minutes : uniquement des multiples du pas (15) entre 0 et `max` (45), jamais 60 ou 59.
 * PrimeNG ne le permet pas seul : `max` rogne aussi la valeur tapée (50 deviendrait 45 en silence), alors que
 * sans `max` la flèche haut passe à 60. Les flèches du clavier et les boutons sont donc pris en charge ici, et le
 * composant garde ses bornes ouvertes : une valeur tapée reste telle que tapée, le validateur du formulaire la juge.
 */
@Directive({ selector: 'p-inputnumber[appQuarterHourStep]' })
export class QuarterHourStep implements OnInit {
  readonly step = input(15);
  readonly max = input(45);

  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly control = inject(NgControl);
  private readonly destroyRef = inject(DestroyRef);
  /** Dernière valeur posée par une flèche : l'affichage de PrimeNG est mis à jour avec un léger différé. */
  private pending: number | null = null;

  ngOnInit(): void {
    const onKeydown = (event: Event): void => {
      const key = (event as KeyboardEvent).key;
      if (key === 'ArrowUp' || key === 'ArrowDown') {
        this.stop(event);
        this.move(key === 'ArrowUp' ? 1 : -1);
      }
    };
    // Souris et toucher : PrimeNG fait tourner la valeur dès l'appui ; on l'en empêche et on agit au clic.
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
      // Capture : le gestionnaire de PrimeNG, sur l'élément interne, ne reçoit pas l'événement.
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
    // Valeur affichée (tapée ou non encore validée) : « 10 min » donne 10, rien de lisible donne 0.
    // Une pression qui suit de près la précédente part de la valeur déjà posée, pas d'un affichage en retard.
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
