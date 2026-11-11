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

/** SFD-06 E1 Settings (ADMIN): length of the standard day, entered in hours and minutes (RG-00-02). */
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

  // The form holds the duration in hours and minutes; the API receives the total in minutes (`standardDayMinutes`).
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
  /** RG-06-06: saving the value in force has no effect; the button stays disabled until it changes. */
  protected readonly unchanged = () => this.total() === this.store.standardDayMinutes();
  /** Fields flagged in error (same display condition as the message: after interaction or a server error). */
  protected readonly invalid = toSignal(
    this.form.events.pipe(
      startWith(null),
      map(() => this.form.invalid && this.form.touched),
    ),
    { initialValue: false },
  );

  constructor() {
    // The form takes the store's value on each load, save or reload after a conflict.
    effect(() => {
      const settings = this.store.settings();
      if (settings) {
        untracked(() => this.form.reset(fromMinutes(settings.standardDayMinutes)));
      }
    });
    // Fresh read on each opening: the version sent on save is the back end's.
    this.store.load();
  }

  /** After a 409, a failed re-read must not suggest that the form shows the current value. */
  protected readonly loadErrorMessage = computed(() =>
    this.conflict()
      ? this.i18n.t('settings.conflictReloadFailed')
      : (this.store.error() ?? ''),
  );

  /**
   * RG-06-05: 'First Last, le dd/MM/yyyy à HH:mm' or 'First Last, on dd/MM/yyyy at HH:mm' (Paris time), or the 'never changed' mention.
   */
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
    // RG-06-06: identical value = nothing to send.
    if (this.unchanged() && this.form.valid) {
      return;
    }
    this.form.markAllAsTouched();
    const minutes = toMinutes(this.form.getRawValue());
    // CA-06-05: value refused by RG-06-02, no request.
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
          // The back end remains the reference: its message is shown under the field, without a toast.
          this.form.setErrors({ [SERVER_ERROR_KEY]: field.message });
          this.form.markAllAsTouched();
        } else if (problem.status === 409 && problem.code === null) {
          // Concurrent change: the store reloads and the form takes the reloaded value.
          this.conflict.set(true);
        }
      },
    });
  }
}
