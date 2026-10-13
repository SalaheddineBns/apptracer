import { ChangeDetectionStrategy, Component, effect, inject, model, signal, untracked } from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { Granularity } from '../../../core/api/generated';
import { FieldError } from '../../../shared/components/field-error';
import { isoToLocalDate, todayInParis } from '../../../shared/dates/week';
import {
  COMMENT_MAX_LENGTH,
  endDateValidator,
  endTimeAfterStartValidator,
  MAX_RANGE_DAYS,
  notFutureValidator,
  quarterHourValidator,
  TIME_STEP_MINUTES,
} from './time-entry-period';

/** SFD-01 E2 Dialogue de saisie. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    DatePickerModule,
    SelectButtonModule,
    TextareaModule,
    FieldError,
  ],
  selector: 'app-time-entry-dialog',
  templateUrl: './time-entry-dialog.html',
})
export class TimeEntryDialog {
  readonly visible = model(false);

  protected readonly Granularity = Granularity;
  protected readonly timeStep = TIME_STEP_MINUTES;
  protected readonly granularityOptions = [
    { label: 'Heure', value: Granularity.Hour },
    { label: 'Journée', value: Granularity.Day },
    { label: 'Plage', value: Granularity.Range },
  ];
  /** RG-01-07 : dernier jour proposé pour la date de début (date du jour à Paris). */
  protected readonly today = signal(isoToLocalDate(todayInParis()));

  protected readonly form = inject(FormBuilder).nonNullable.group({
    granularity: [Granularity.Hour, [Validators.required]],
    // RG-01-07 : pas de date de début dans le futur (le back reste la référence).
    startDate: [null as Date | null, [Validators.required, notFutureValidator]],
    // RG-01-03 : après la date de début, 31 jours au plus bornes incluses.
    endDate: [null as Date | null, [Validators.required, endDateValidator]],
    // RG-01-01 : même jour, par pas de 15 minutes, fin après le début.
    startTime: [null as Date | null, [Validators.required, quarterHourValidator]],
    endTime: [
      null as Date | null,
      [Validators.required, quarterHourValidator, endTimeAfterStartValidator],
    ],
    // RG-01-09 : 500 caractères au plus.
    comment: ['', [Validators.maxLength(COMMENT_MAX_LENGTH)]],
  });

  protected readonly granularity = toSignal(this.form.controls.granularity.valueChanges, {
    initialValue: this.form.controls.granularity.value,
  });

  protected readonly startDateMessages = {
    required: 'La date est obligatoire.',
    future: 'La date de début ne peut pas être dans le futur.',
  };
  protected readonly endDateMessages = {
    required: 'La date de fin est obligatoire.',
    endDateNotAfter: 'La date de fin doit être après la date de début.',
    rangeTooLong: `Une plage fait au plus ${MAX_RANGE_DAYS} jours, bornes incluses.`,
  };
  protected readonly startTimeMessages = {
    required: 'L’heure de début est obligatoire.',
    quarterHour: `L’heure se saisit par pas de ${TIME_STEP_MINUTES} minutes.`,
  };
  protected readonly endTimeMessages = {
    required: 'L’heure de fin est obligatoire.',
    quarterHour: `L’heure se saisit par pas de ${TIME_STEP_MINUTES} minutes.`,
    endBeforeStart: 'L’heure de fin doit être après l’heure de début.',
  };
  protected readonly commentMessages = {
    maxlength: `Le commentaire ne doit pas dépasser ${COMMENT_MAX_LENGTH} caractères.`,
  };

  constructor() {
    const { granularity, startDate, endDate, startTime, endTime } = this.form.controls;
    // Les champs de période s'adaptent à la granularité : un champ hors sujet est désactivé, donc ni validé ni envoyé.
    granularity.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.applyGranularity(value));
    // Les contrôles croisés sont rejoués quand la borne de début change.
    startTime.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => endTime.updateValueAndValidity());
    startDate.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => endDate.updateValueAndValidity());

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    this.today.set(isoToLocalDate(todayInParis()));
    this.form.reset({
      granularity: Granularity.Hour,
      startDate: this.today(),
      endDate: null,
      startTime: null,
      endTime: null,
      comment: '',
    });
    this.applyGranularity(Granularity.Hour);
  }

  private applyGranularity(granularity: Granularity): void {
    const { endDate, startTime, endTime } = this.form.controls;
    const toggle = (control: typeof endDate, enabled: boolean) =>
      enabled ? control.enable({ emitEvent: false }) : control.disable({ emitEvent: false });
    toggle(startTime, granularity === Granularity.Hour); // RG-01-01
    toggle(endTime, granularity === Granularity.Hour);
    toggle(endDate, granularity === Granularity.Range); // RG-01-03
  }

  protected close(): void {
    this.visible.set(false);
  }
}
