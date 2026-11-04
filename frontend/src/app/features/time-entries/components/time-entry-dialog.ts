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
import { AppSettingsStore } from '../../../core/settings/app-settings.store';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { FieldError } from '../../../shared/components/field-error';
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

/** SFD-01 E2 Dialogue de saisie : création (entry = null) ou modification d'une saisie existante (RG-01-12). */
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

  readonly visible = model(false);
  /** Saisie à modifier, ou `null` pour une création. */
  readonly entry = input<TimeEntryResponse | null>(null);
  /** Date de début proposée pour une création (`YYYY-MM-DD`, « + » d'un jour du journal) ; la date du jour sinon. */
  readonly initialDate = input<string | null>(null);
  readonly saved = output<TimeEntrySaved>();
  /** Modification concurrente détectée : l'appelant recharge la semaine. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.entry() !== null);
  protected readonly Granularity = Granularity;
  protected readonly timeStep = TIME_STEP_MINUTES;
  protected readonly granularityOptions = [
    { label: 'Heure', value: Granularity.Hour },
    { label: 'Journée', value: Granularity.Day },
    { label: 'Plage', value: Granularity.Range },
  ];
  /** RG-01-07 : dernier jour proposé pour la date de début (date du jour à Paris). */
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

  /** RG-01-01 : heure de l'ouverture du dialogue, arrondie au quart d'heure inférieur. */
  private readonly openedAt = signal(quarterHourFloor(new Date()));
  /** Heure proposée par le sélecteur de début quand il est vide : toujours un multiple de 15 minutes. */
  protected readonly defaultStartTime = computed(() => this.openedAt());
  /** Sélecteur de fin vide : il part de l'heure de début saisie (alignée), sinon de la même heure que le début. */
  protected readonly defaultEndTime = computed(() => {
    const start = this.value().startTime;
    return start instanceof Date ? quarterHourFloor(start) : this.openedAt();
  });

  /** RG-01-05, RG-02-03 : le projet n'est demandé que si le type choisi l'exige (CA-01-06). */
  protected readonly projectRequired = computed(() => {
    const code = this.value().activityTypeCode;
    return this.types.activeItems().find((type) => type.code === code)?.projectRequired ?? false;
  });

  /** RG-01-08 : durée calculée en direct, avec la journée standard en vigueur (RG-00-02, CA-06-03). */
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
        ? 'aucun jour ouvré'
        : `${duration.weekdays} jour${duration.weekdays > 1 ? 's' : ''} ouvré${duration.weekdays > 1 ? 's' : ''}`;
    return `${text} (${days})`;
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
  protected readonly activityTypeMessages = { required: 'Le type d’activité est obligatoire.' };
  protected readonly projectMessages = {
    required: 'Le projet est obligatoire pour ce type d’activité.',
  };
  protected readonly commentMessages = {
    maxlength: `Le commentaire ne doit pas dépasser ${COMMENT_MAX_LENGTH} caractères.`,
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

  /** Rend le focus au bouton d'origine (retrouvé par son data-cy : la carte a pu être recréée). */
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

  /** Les `errors[]` de l'API s'affichent sous leur champ ; celles d'un champ absent de l'écran, en toast. */
  private showFieldErrors(errors: readonly ApiFieldError[]): void {
    const shown = errors.filter((error) => this.form.get(error.field)?.enabled);
    const hidden = errors.filter((error) => !shown.includes(error));
    for (const error of [...applyFieldErrors(this.form, shown), ...hidden]) {
      this.messages.add({ severity: 'error', summary: 'Erreur', detail: error.message, life: 5000 });
    }
  }
}
