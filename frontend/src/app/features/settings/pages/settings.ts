import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { MessageModule } from 'primeng/message';
import { ButtonModule } from 'primeng/button';
import { InputNumberModule } from 'primeng/inputnumber';
import { SkeletonModule } from 'primeng/skeleton';
import { map, startWith } from 'rxjs';
import { AppSettingsStore } from '../../../core/settings/app-settings.store';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorState } from '../../../shared/components/error-state';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { parisDateTimeParts } from '../../../shared/dates/paris-date-time';
import { QuarterHourStep } from '../../../shared/forms/quarter-hour-step';
import { SERVER_ERROR_KEY } from '../../../shared/forms/apply-field-errors';
import {
  formatDuration,
  fromMinutes,
  standardDayValidator,
  toMinutes,
} from '../standard-day';

/** SFD-06 E1 Paramètres (ADMIN) : durée de la journée standard, saisie en heures et minutes (RG-00-02). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    ButtonModule,
    InputNumberModule,
    MessageModule,
    QuarterHourStep,
    SkeletonModule,
    ErrorState,
    FieldError,
    TPipe,
  ],
  selector: 'app-settings',
  templateUrl: './settings.html',
})
export class SettingsPage {
  protected readonly store = inject(AppSettingsStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  // Le formulaire porte la durée en heures et minutes ; l'API reçoit le total en minutes (`standardDayMinutes`).
  protected readonly form = new FormGroup(
    { hours: new FormControl<number | null>(null), minutes: new FormControl<number | null>(null) },
    // RG-06-02
    { validators: standardDayValidator },
  );

  protected readonly durationMessages: FieldMessages = { standardDay: 'settings.invalid' };
  protected readonly conflict = signal(false);

  private readonly total = toSignal(
    this.form.valueChanges.pipe(
      startWith(null),
      map(() => toMinutes(this.form.getRawValue())),
    ),
    { initialValue: null },
  );
  /** RG-06-06 : enregistrer la valeur en vigueur est sans effet, le bouton reste inactif tant qu'elle n'a pas changé. */
  protected readonly unchanged = () => this.total() === this.store.standardDayMinutes();
  /** Champs marqués en erreur (même condition d'affichage que le message : après interaction ou erreur serveur). */
  protected readonly invalid = toSignal(
    this.form.events.pipe(
      startWith(null),
      map(() => this.form.invalid && this.form.touched),
    ),
    { initialValue: false },
  );

  constructor() {
    // Le formulaire reprend la valeur du store à chaque chargement, enregistrement ou rechargement après conflit.
    effect(() => {
      const settings = this.store.settings();
      if (settings) {
        untracked(() => this.form.reset(fromMinutes(settings.standardDayMinutes)));
      }
    });
    // Lecture fraîche à chaque ouverture : la version envoyée à l'enregistrement est celle du back.
    this.store.load();
  }

  /** Après un 409, une relecture en échec ne doit pas laisser croire que le formulaire montre la valeur actuelle. */
  protected readonly loadErrorMessage = computed(() =>
    this.conflict()
      ? this.i18n.t('settings.conflictReloadFailed')
      : (this.store.error() ?? ''),
  );

  /** RG-06-05 : « Prénom Nom, le dd/MM/yyyy à HH:mm » ou « Prénom Nom, on dd/MM/yyyy at HH:mm » (heure de Paris), ou la mention « jamais modifiée ». */
  protected lastModifiedText(): string {
    const at = this.store.lastModifiedAt();
    if (!at) {
      return this.i18n.t('settings.neverModified');
    }
    const when = parisDateTimeParts(at) ?? { date: '', time: '' };
    const author = this.store.lastModifiedBy();
    return author
      ? this.i18n.t('settings.lastModifiedBy', { author: `${author.firstName} ${author.lastName}`, ...when })
      : this.i18n.t('settings.lastModified', when);
  }

  protected submit(): void {
    if (this.store.saving()) {
      return;
    }
    // RG-06-06 : valeur identique = rien à envoyer.
    if (this.unchanged() && this.form.valid) {
      return;
    }
    this.form.markAllAsTouched();
    const minutes = toMinutes(this.form.getRawValue());
    // CA-06-05 : valeur refusée par RG-06-02, aucune requête.
    if (this.form.invalid || minutes === null) {
      return;
    }
    this.conflict.set(false);
    this.store.update(minutes).subscribe({
      next: (saved) =>
        this.messages.add({
          severity: 'success',
          summary: this.i18n.t('settings.saved'),
          detail: this.i18n.t('settings.savedDetail', { duration: formatDuration(saved.standardDayMinutes) }),
          life: 3000,
        }),
      error: (error: unknown) => {
        const problem = toProblem(error);
        const field = problem.fieldErrors.find((e) => e.field === 'standardDayMinutes');
        if (field) {
          // Le back reste la référence : son message s'affiche sous le champ, sans toast.
          this.form.setErrors({ [SERVER_ERROR_KEY]: field.message });
          this.form.markAllAsTouched();
        } else if (problem.status === 409 && problem.code === null) {
          // Modification concurrente : le store se recharge et le formulaire reprend la valeur rechargée.
          this.conflict.set(true);
        }
      },
    });
  }
}
