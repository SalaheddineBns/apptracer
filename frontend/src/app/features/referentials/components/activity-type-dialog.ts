import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { CheckboxModule } from 'primeng/checkbox';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { SelectModule } from 'primeng/select';
import { ActivityTypeResponse, TagColor } from '../../../core/api/generated';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { labelKeyForColor, TAG_COLORS } from '../../../shared/activity-type.ui';
import { ActivityTypeTag } from '../../../shared/components/activity-type-tag';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { referentialCodeMessages, referentialCodeValidators } from '../../../shared/forms/referential-code';
import { ActivityTypesStore } from '../data/activity-types.store';

export interface ActivityTypeSaved {
  readonly mode: 'create' | 'update';
  readonly type: ActivityTypeResponse;
}

/** SFD-02 E2 Dialogue type : création (type = null) ou modification d'un type d'activité existant. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule, CheckboxModule, FieldError, ActivityTypeTag, TPipe],
  selector: 'app-activity-type-dialog',
  templateUrl: './activity-type-dialog.html',
})
export class ActivityTypeDialog {
  private readonly store = inject(ActivityTypesStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Type à modifier, ou `null` pour une création. */
  readonly type = input<ActivityTypeResponse | null>(null);
  readonly saved = output<ActivityTypeSaved>();
  /** Modification concurrente détectée : l'appelant recharge la liste. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.type() !== null);
  protected readonly colorOptions = computed(() =>
    TAG_COLORS.map((value) => ({ value, label: this.i18n.t(labelKeyForColor(value)) })),
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-02-01 : format du code (le back reste la référence), immuable après la création.
    code: ['', referentialCodeValidators],
    // SFD-02 données : libellé obligatoire, 100 caractères au plus.
    label: ['', [Validators.required, Validators.maxLength(100)]],
    // RG-02-04 : couleur de la palette.
    color: [TagColor.Info, [Validators.required]],
    // RG-02-03, RG-02-07 : le front ignore si le type est utilisé, l'API répond 409 le cas échéant.
    projectRequired: [false],
  });

  private readonly value = toSignal(this.form.valueChanges, { initialValue: this.form.getRawValue() });
  protected readonly preview = computed(() => {
    const value = { ...this.form.getRawValue(), ...this.value() };
    return { label: value.label?.trim() || this.i18n.t('activityTypes.dialog.previewLabel'), color: value.color ?? TagColor.Info, code: this.form.controls.code.value };
  });

  protected readonly codeExample = 'REUNION_CLIENT';
  protected readonly codeMessages = referentialCodeMessages(this.codeExample);
  protected readonly labelMessages: FieldMessages = {
    required: 'activityTypes.dialog.errors.labelRequired',
    maxlength: 'activityTypes.dialog.errors.labelTooLong',
  };
  protected readonly colorMessages: FieldMessages = { required: 'activityTypes.dialog.errors.colorRequired' };

  private originCy: string | null = null;

  constructor() {
    // p-dialog n'évalue `closeOnEscape` qu'à l'ouverture : pendant l'envoi, on intercepte Échap avant son écouteur (document).
    const blockEscapeWhileSubmitting = (event: KeyboardEvent): void => {
      if (event.key === 'Escape' && this.submitting()) {
        event.stopImmediatePropagation();
      }
    };
    window.addEventListener('keydown', blockEscapeWhileSubmitting, true);
    inject(DestroyRef).onDestroy(() => window.removeEventListener('keydown', blockEscapeWhileSubmitting, true));

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    const type = this.type();
    const active = document.activeElement;
    this.originCy = active instanceof HTMLElement ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null) : null;
    this.submitting.set(false);
    this.form.reset({
      code: type?.code ?? '',
      label: type?.label ?? '',
      color: type?.color ?? TagColor.Info,
      projectRequired: type?.projectRequired ?? false,
    });
    // CA-02-05, RG-02-01 : le code n'est plus modifiable après la création.
    if (type) {
      this.form.controls.code.disable();
    } else {
      this.form.controls.code.enable();
    }
  }

  /**
   * Focus initial sur le premier champ modifiable (le code en création, le libellé en modification). `onShow` arrive à la fin de
   * l'animation d'ouverture : si l'utilisateur saisit déjà dans le formulaire, on ne lui reprend pas le focus.
   */
  protected focusFirstField(form: HTMLFormElement): void {
    const first = form.querySelector<HTMLInputElement>('input:not(:disabled)');
    const active = document.activeElement;
    if (!first || (active !== first && active instanceof HTMLElement && form.contains(active))) {
      return;
    }
    first.focus();
  }

  protected close(): void {
    this.visible.set(false);
  }

  /** Rend le focus au bouton d'origine (retrouvé par son data-cy : la ligne a pu être recréée). */
  protected onHide(): void {
    const cy = this.originCy;
    if (cy) {
      setTimeout(() => document.querySelector<HTMLElement>(`[data-cy="${cy}"] button, button[data-cy="${cy}"]`)?.focus());
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
    const type = this.type();
    const value = this.form.getRawValue();
    const request$ = type
      ? this.store.update(type.id, { label: value.label, color: value.color, projectRequired: value.projectRequired, version: type.version })
      : this.store.create({ code: value.code, label: value.label, color: value.color, projectRequired: value.projectRequired });
    request$.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: type ? 'update' : 'create', type: saved });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-02-01 (code), RG-02-07 (projectRequired) : les errors[] s'affichent sous le champ, le dialogue reste ouvert.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (type && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
