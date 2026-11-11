import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  model,
  output,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DatePickerModule } from 'primeng/datepicker';
import { DialogModule } from 'primeng/dialog';
import { SelectModule } from 'primeng/select';
import { SelectButtonModule } from 'primeng/selectbutton';
import { TextareaModule } from 'primeng/textarea';
import { map } from 'rxjs';
import { CreateTimeEntryRequest, Granularity, TimeEntryResponse } from '../../../core/api/generated';
import { FieldError as ApiFieldError, toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { AppSettingsStore } from '../../../core/settings/app-settings.store';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { isoToLocalDate, localDateToIso, todayInParis } from '../../../shared/dates/week';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { formatDuration } from '../../../shared/pipes/duration';
import { ActivityTypesStore } from '../../referentials/data/activity-types.store';
import { ProjectsStore } from '../../referentials/data/projects.store';
import { TimeEntriesStore } from '../data/time-entries.store';
import {
  COMMENT_MAX_LENGTH,
  endDateValidator,
  endTimeAfterStartValidator,
  liveDuration,
  MAX_RANGE_DAYS,
  notFutureValidator,
  quarterHourValidator,
  TIME_STEP_MINUTES,
  quarterHourFloor,
  timeStringToDate,
  toTimeString,
} from './time-entry-period';

export interface TimeEntrySaved {
  readonly mode: 'create' | 'update';
  readonly entry: TimeEntryResponse;
}

/** SFD-01 E2 Time entry dialog: creation (entry = null) or update of an existing time entry (RG-01-12). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    DatePickerModule,
    SelectModule,
    SelectButtonModule,
    TextareaModule,
    FieldError,
    ActivityTypeTag,
    TPipe,
  ],
  selector: 'app-time-entry-dialog',
  templateUrl: './time-entry-dialog.html',
})
export class TimeEntryDialog {
  private readonly store = inject(TimeEntriesStore);
  protected readonly types = inject(ActivityTypesStore);
  protected readonly projects = inject(ProjectsStore);
  private readonly settings = inject(AppSettingsStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Time entry to update, or `null` for a creation. */
  readonly entry = input<TimeEntryResponse | null>(null);
  /** Start date offered for a creation (`YYYY-MM-DD`, from the + of a journal day); today's date otherwise. */
  readonly initialDate = input<string | null>(null);
  readonly saved = output<TimeEntrySaved>();
  /** Concurrent change detected: the caller reloads the week. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.entry() !== null);
  /**
   * RG-10-03: projects allowed for the time entry (filtered by the back end). RG-10-05: on update, the project already on
   * the entry is still offered if it is active, even if it is no longer allowed. RG-10-09: alphabetical order of the names.
   */
  protected readonly projectOptions = computed(() => {
    const options = this.projects.activeItems().map(({ code, name }) => ({ code, name }));
    const current = this.entry()?.project;
    if (current?.active && !options.some((option) => option.code === current.code)) {
      options.push({ code: current.code, name: current.name });
      options.sort((a, b) => a.name.localeCompare(b.name));
    }
    return options;
  });
  protected readonly Granularity = Granularity;
  protected readonly timeStep = TIME_STEP_MINUTES;
  protected readonly granularityOptions = computed(() => [
    { label: this.i18n.t('timeEntry.dialog.hour'), value: Granularity.Hour },
    { label: this.i18n.t('timeEntry.dialog.day'), value: Granularity.Day },
    { label: this.i18n.t('timeEntry.dialog.rangeOption'), value: Granularity.Range },
  ]);
  /** RG-01-07: last day offered for the start date (today in Paris). */
  protected readonly today = signal(isoToLocalDate(todayInParis()));

  // Les contrôles portent le nom des champs de l'API : ses `errors[]` s'affichent sous le champ concerné.
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
    // RG-01-04 : type obligatoire, choisi parmi les types actifs.
    activityTypeCode: ['', [Validators.required]],
    // RG-01-05 : projet obligatoire si le type l'exige ; le contrôle est désactivé sinon.
    projectCode: [null as string | null, [Validators.required]],
    // RG-01-09 : 500 caractères au plus.
    comment: ['', [Validators.maxLength(COMMENT_MAX_LENGTH)]],
  });

  private readonly value = toSignal(
    this.form.valueChanges.pipe(map(() => this.form.getRawValue())),
    { initialValue: this.form.getRawValue() },
  );
  protected readonly granularity = computed(() => this.value().granularity);

  /** RG-01-01: time the dialog was opened, rounded down to the quarter hour. */
  private readonly openedAt = signal(quarterHourFloor(new Date()));
  /** Time offered by the start picker when it is empty: always a multiple of 15 minutes. */
  protected readonly defaultStartTime = computed(() => this.openedAt());
  /**
   * Empty end picker: it starts from the entered start time (aligned), otherwise from the same time as the start.
   * PrimeNG resets its panel on each new `defaultDate`: the value must only change when the offered time changes,
   * never on each keystroke (otherwise the panel keeps jumping back to the start time).
   */
  protected readonly defaultEndTime = computed(
    () => {
      const start = this.value().startTime;
      return start instanceof Date ? quarterHourFloor(start) : this.openedAt();
    },
    { equal: (a, b) => a.getTime() === b.getTime() },
  );

  /** RG-01-05, RG-02-03: the project is asked for only if the selected type requires it (CA-01-06). */
  protected readonly projectRequired = computed(() => {
    const code = this.value().activityTypeCode;
    return this.types.activeItems().find((type) => type.code === code)?.projectRequired ?? false;
  });

  /** RG-01-08: duration computed live, with the standard day in force (RG-00-02, CA-06-03). */
  protected readonly durationText = computed(() => {
    const duration = liveDuration(this.value(), this.settings.standardDayMinutes());
    if (duration === null) {
      return '—';
    }
    const text = formatDuration(duration.minutes);
    if (duration.weekdays === null) {
      return text;
    }
    const days =
      duration.weekdays === 0
        ? this.i18n.t('timeEntry.dialog.noWorkingDay')
        : this.i18n.plural('timeEntry.dialog.workingDays', duration.weekdays);
    return `${text} (${days})`;
  });

  protected readonly startDateMessages: FieldMessages = {
    required: 'timeEntry.dialog.errors.dateRequired',
    future: 'timeEntry.dialog.errors.startDateFuture',
  };
  protected readonly endDateMessages: FieldMessages = {
    required: 'timeEntry.dialog.errors.endDateRequired',
    endDateNotAfter: 'timeEntry.dialog.errors.endDateNotAfter',
    rangeTooLong: { key: 'timeEntry.dialog.errors.rangeTooLong', params: { max: MAX_RANGE_DAYS } },
  };
  protected readonly startTimeMessages: FieldMessages = {
    required: 'timeEntry.dialog.errors.startTimeRequired',
    quarterHour: { key: 'timeEntry.dialog.errors.quarterHour', params: { step: TIME_STEP_MINUTES } },
  };
  protected readonly endTimeMessages: FieldMessages = {
    required: 'timeEntry.dialog.errors.endTimeRequired',
    quarterHour: { key: 'timeEntry.dialog.errors.quarterHour', params: { step: TIME_STEP_MINUTES } },
    endBeforeStart: 'timeEntry.dialog.errors.endBeforeStart',
  };
  protected readonly activityTypeMessages: FieldMessages = { required: 'timeEntry.dialog.errors.typeRequired' };
  protected readonly projectMessages: FieldMessages = { required: 'timeEntry.dialog.errors.projectRequired' };
  protected readonly commentMessages: FieldMessages = {
    maxlength: { key: 'timeEntry.dialog.errors.commentTooLong', params: { max: COMMENT_MAX_LENGTH } },
  };


  private originCy: string | null = null;

  constructor() {
    // p-dialog n'évalue `closeOnEscape` qu'à l'ouverture : pendant l'envoi, on intercepte Échap avant son écouteur (document).
    const blockEscapeWhileSubmitting = (event: KeyboardEvent): void => {
      if (event.key === 'Escape' && this.submitting()) {
        event.stopImmediatePropagation();
      }
    };
    window.addEventListener('keydown', blockEscapeWhileSubmitting, true);
    inject(DestroyRef).onDestroy(() =>
      window.removeEventListener('keydown', blockEscapeWhileSubmitting, true),
    );

    const { granularity, startDate, endDate, startTime, endTime } = this.form.controls;
    // Les champs de période s'adaptent à la granularité : un champ hors sujet est désactivé, donc ni validé ni envoyé.
    granularity.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((value) => this.applyGranularity(value));
    // Les contrôles croisés sont rejoués quand la borne de début change.
    startTime.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => endTime.updateValueAndValidity());
    startDate.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => endDate.updateValueAndValidity());

    effect(() => {
      // RG-01-05 : projet interdit pour un type qui ne l'exige pas ; la valeur n'est alors ni validée ni envoyée.
      const required = this.projectRequired();
      untracked(() => {
        const control = this.form.controls.projectCode;
        if (required) {
          control.enable({ emitEvent: false });
        } else {
          control.disable({ emitEvent: false });
        }
      });
    });

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    const active = document.activeElement;
    this.originCy =
      active instanceof HTMLElement
        ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null)
        : null;
    this.submitting.set(false);
    // RG-02-06 : seuls les types et projets actifs sont proposés ; les listes sont relues à chaque ouverture.
    this.types.load();
    this.projects.load();
    // CA-06-03 : la journée standard est rechargée à chaque ouverture pour suivre une modification du paramètre.
    this.settings.load();
    this.today.set(isoToLocalDate(todayInParis()));
    this.openedAt.set(quarterHourFloor(new Date()));
    const entry = this.entry();
    const initialDate = this.initialDate();
    // RG-01-12 : tous les champs sont repris, granularité comprise. RG-02-06 : un type ou un projet désactivé n'est
    // plus proposé, sa valeur n'est pas reprise et l'utilisateur doit en choisir un actif.
    this.form.reset({
      granularity: entry?.granularity ?? Granularity.Hour,
      startDate: isoToLocalDate(entry?.startDate ?? initialDate ?? todayInParis()),
      endDate: entry?.endDate ? isoToLocalDate(entry.endDate) : null,
      startTime: entry?.startTime ? timeStringToDate(entry.startTime) : null,
      endTime: entry?.endTime ? timeStringToDate(entry.endTime) : null,
      activityTypeCode: entry?.activityType.active ? entry.activityType.code : '',
      projectCode: entry?.project?.active ? entry.project.code : null,
      comment: entry?.comment ?? '',
    });
    this.applyGranularity(this.form.controls.granularity.value);
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

  /** Returns focus to the original button (found by its data-cy: the card may have been recreated). */
  protected onHide(): void {
    const cy = this.originCy;
    if (cy) {
      setTimeout(() =>
        document
          .querySelector<HTMLElement>(`[data-cy="${cy}"] button, button[data-cy="${cy}"]`)
          ?.focus(),
      );
    }
  }

  protected submit(): void {
    if (this.submitting()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    const editing = this.entry();
    const request = this.toRequest();
    // RG-01-12 : mêmes champs et mêmes contrôles qu'à la création, avec la version lue.
    const request$ = editing
      ? this.store.update(editing.id, { ...request, version: editing.version })
      : this.store.create(request);
    request$.subscribe({
      next: (entry) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: editing ? 'update' : 'create', entry });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-01-06 (chevauchement, CA-01-04) n'a pas d'erreur de champ : son message est affiché en toast par l'intercepteur.
        this.showFieldErrors(problem.fieldErrors);
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (
          editing &&
          problem.status === 409 &&
          problem.code === null &&
          problem.fieldErrors.length === 0
        ) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }

  private toRequest(): CreateTimeEntryRequest {
    const value = this.form.getRawValue();
    const hour = value.granularity === Granularity.Hour;
    const range = value.granularity === Granularity.Range;
    return {
      granularity: value.granularity,
      startDate: localDateToIso(value.startDate as Date),
      ...(range && value.endDate ? { endDate: localDateToIso(value.endDate) } : {}),
      ...(hour && value.startTime ? { startTime: toTimeString(value.startTime) } : {}),
      ...(hour && value.endTime ? { endTime: toTimeString(value.endTime) } : {}),
      activityTypeCode: value.activityTypeCode,
      // RG-01-05 : le projet n'est envoyé que pour un type qui l'exige.
      ...(this.projectRequired() && value.projectCode ? { projectCode: value.projectCode } : {}),
      ...(value.comment.trim() === '' ? {} : { comment: value.comment }),
    };
  }

  /** The API's `errors[]` are shown under their field; those of a field not on the screen, in a toast. */
  private showFieldErrors(errors: readonly ApiFieldError[]): void {
    const shown = errors.filter((error) => this.form.get(error.field)?.enabled);
    const hidden = errors.filter((error) => !shown.includes(error));
    for (const error of [...applyFieldErrors(this.form, shown), ...hidden]) {
      this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: error.message, life: 5000 });
    }
  }
}
