import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { ProjectResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { referentialCodeMessages, referentialCodeValidators } from '../../../shared/forms/referential-code';
import { ProjectsStore } from '../data/projects.store';

export interface ProjectSaved {
  readonly mode: 'create' | 'update';
  readonly project: ProjectResponse;
}

/** SFD-02 E4 Dialogue projet : création (project = null) ou modification d'un projet existant. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, FieldError, TPipe],
  selector: 'app-project-dialog',
  templateUrl: './project-dialog.html',
})
export class ProjectDialog {
  private readonly store = inject(ProjectsStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Projet à modifier, ou `null` pour une création. */
  readonly project = input<ProjectResponse | null>(null);
  readonly saved = output<ProjectSaved>();
  /** Modification concurrente détectée : l'appelant recharge la liste. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.project() !== null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-02-01 : format du code, immuable après la création.
    code: ['', referentialCodeValidators],
    // SFD-02 données : nom obligatoire, 100 caractères au plus.
    name: ['', [Validators.required, Validators.maxLength(100)]],
  });

  // L'exemple est traduit (RG-08-05) : « REUNION_CLIENT » en français, « CLIENT_MEETING » en anglais.
  protected readonly codeExample = computed(() => this.i18n.t('referentialCode.projectExample'));
  protected readonly codeMessages = computed(() => referentialCodeMessages(this.codeExample()));
  protected readonly nameMessages: FieldMessages = {
    required: 'projects.dialog.errors.nameRequired',
    maxlength: 'projects.dialog.errors.nameTooLong',
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
    const project = this.project();
    const active = document.activeElement;
    this.originCy = active instanceof HTMLElement ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null) : null;
    this.submitting.set(false);
    this.form.reset({ code: project?.code ?? '', name: project?.name ?? '' });
    // CA-02-05, RG-02-01 : le code n'est plus modifiable après la création.
    if (project) {
      this.form.controls.code.disable();
    } else {
      this.form.controls.code.enable();
    }
  }

  /**
   * Focus initial sur le premier champ modifiable (le code en création, le nom en modification). `onShow` arrive à la fin de
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
    const project = this.project();
    const value = this.form.getRawValue();
    const request$ = project
      ? this.store.update(project.id, { name: value.name, version: project.version })
      : this.store.create({ code: value.code, name: value.name });
    request$.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: project ? 'update' : 'create', project: saved });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-02-01 (code) : les errors[] s'affichent sous le champ, le dialogue reste ouvert.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (project && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
