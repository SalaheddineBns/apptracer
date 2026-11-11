import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { ProjectResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { referentialCodeMessages, referentialCodeValidators } from '../../../shared/forms/referential-code';
import { TeamsStore } from '../../teams/data/teams.store';
import { ProjectsStore } from '../data/projects.store';

export interface ProjectSaved {
  readonly mode: 'create' | 'update';
  readonly project: ProjectResponse;
}

/** SFD-02 E4 Project dialog: creation (project = null) or update of an existing project. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, MessageModule, SelectModule, FieldError, TPipe],
  selector: 'app-project-dialog',
  templateUrl: './project-dialog.html',
})
export class ProjectDialog {
  private readonly store = inject(ProjectsStore);
  private readonly teams = inject(TeamsStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Project to update, or `null` for a creation. */
  readonly project = input<ProjectResponse | null>(null);
  readonly saved = output<ProjectSaved>();
  /** Concurrent change detected: the caller reloads the list. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.project() !== null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-02-01: code format, cannot change after creation.
    code: ['', referentialCodeValidators],
    // Spec data: name required, 100 characters at most.
    name: ['', [Validators.required, Validators.maxLength(100)]],
    teamId: [null as number | null],
  });

  /**
   * RG-10-01: optional team of the project (field `teamId`, named like `errors[].field`); when empty, the project is shared.
   * RG-10-09: existing teams, in the back end's order (alphabetical by name, RG-05-13).
   */
  protected readonly teamOptions = computed(() => this.teams.items().map((team) => ({ label: team.name, value: team.id })));
  protected readonly teamsError = computed(() => this.teams.error());

  // The example is translated (RG-08-05): 'REUNION_CLIENT' in French, 'CLIENT_MEETING' in English.
  protected readonly codeExample = computed(() => this.i18n.t('referentialCode.projectExample'));
  protected readonly codeMessages = computed(() => referentialCodeMessages(this.codeExample()));
  protected readonly nameMessages: FieldMessages = {
    required: 'projects.dialog.errors.nameRequired',
    maxlength: 'projects.dialog.errors.nameTooLong',
  };

  private originCy: string | null = null;

  constructor() {
    // p-dialog evaluates `closeOnEscape` only on opening: while sending, Escape is intercepted before its listener
    // (document).
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
    this.form.reset({ code: project?.code ?? '', name: project?.name ?? '', teamId: project?.team?.id ?? null });
    this.teams.load();
    // CA-02-05, RG-02-01: the code can no longer be changed after creation.
    if (project) {
      this.form.controls.code.disable();
    } else {
      this.form.controls.code.enable();
    }
  }

  /**
   * Initial focus on the first editable field (the code on creation, the name on update). `onShow` fires at the end of
   * the opening animation: if the user is already typing in the form, focus is not taken back.
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

  /** Returns focus to the original button (found by its data-cy: the row may have been recreated). */
  protected onHide(): void {
    const cy = this.originCy;
    if (cy) {
      setTimeout(() => document.querySelector<HTMLElement>(`[data-cy="${cy}"] button, button[data-cy="${cy}"]`)?.focus());
    }
  }

  /** Reloads the teams after an error. */
  protected reloadTeams(): void {
    this.teams.load();
  }

  /** Saves the project. RG-10-02: on update, a missing team removes the project's team. */
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
    const team = value.teamId !== null ? { teamId: value.teamId } : {};
    const request$ = project
      ? this.store.update(project.id, { name: value.name, ...team, version: project.version })
      : this.store.create({ code: value.code, name: value.name, ...team });
    request$.subscribe({
      next: (saved) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: project ? 'update' : 'create', project: saved });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-02-01 (code): the errors[] are shown under the field; the dialog stays open.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
        // Concurrent change (409 without a code or errors[]): the message is shown by the interceptor; we reload.
        if (project && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
