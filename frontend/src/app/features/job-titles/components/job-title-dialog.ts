import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { JobTitleResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { referentialCodeMessages, referentialCodeValidators } from '../../../shared/forms/referential-code';
import { JobTitlesStore } from '../data/job-titles.store';

export interface JobTitleSaved {
  readonly mode: 'create' | 'update';
  readonly jobTitle: JobTitleResponse;
}

/** SFD-09 E2 Job title dialog: creation (jobTitle = null) or update of an existing job title. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, FieldError, TPipe],
  selector: 'app-job-title-dialog',
  templateUrl: './job-title-dialog.html',
})
export class JobTitleDialog {
  private readonly store = inject(JobTitlesStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Job title to update, or `null` for a creation. */
  readonly jobTitle = input<JobTitleResponse | null>(null);
  readonly saved = output<JobTitleSaved>();
  /** Concurrent change detected: the caller reloads the list. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.jobTitle() !== null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-09-05 : format du code (le back reste la référence), immuable après la création.
    code: ['', referentialCodeValidators],
    // RG-09-05 : libellé obligatoire, 100 caractères au plus.
    label: ['', [Validators.required, Validators.maxLength(100)]],
  });

  protected readonly codeExample = computed(() => this.i18n.t('referentialCode.jobTitleExample'));
  protected readonly codeMessages = computed(() => referentialCodeMessages(this.codeExample()));
  protected readonly labelMessages: FieldMessages = {
    required: 'jobTitles.dialog.errors.labelRequired',
    maxlength: 'jobTitles.dialog.errors.labelTooLong',
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
    inject(DestroyRef).onDestroy(() => window.removeEventListener('keydown', blockEscapeWhileSubmitting, true));

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    const jobTitle = this.jobTitle();
    const active = document.activeElement;
    this.originCy = active instanceof HTMLElement ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null) : null;
    this.submitting.set(false);
    this.form.reset({ code: jobTitle?.code ?? '', label: jobTitle?.label ?? '' });
    // RG-09-05 : le code n'est plus modifiable après la création.
    if (jobTitle) {
      this.form.controls.code.disable();
    } else {
      this.form.controls.code.enable();
    }
  }

  /** Initial focus on the first editable field (the code on creation, the label on update). */
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

  /** Returns focus to the original button (found by its data-cy: the row may have been recreated). */
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
    const jobTitle = this.jobTitle();
    const value = this.form.getRawValue();
    const request$ = jobTitle
      ? this.store.update(jobTitle.id, { label: value.label, version: jobTitle.version })
      : this.store.create({ code: value.code, label: value.label });
    request$.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: jobTitle ? 'update' : 'create', jobTitle: saved });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-09-05 (code unique) : les errors[] s'affichent sous le champ, le dialogue reste ouvert.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (jobTitle && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
