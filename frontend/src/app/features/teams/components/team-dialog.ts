import { ChangeDetectionStrategy, Component, computed, DestroyRef, effect, inject, input, model, output, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { SelectModule } from 'primeng/select';
import { TagModule } from 'primeng/tag';
import { TeamResponse } from '../../../core/api/generated';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { ErrorState } from '../../../shared/components/error-state';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { LoadingState } from '../../../shared/components/loading-state';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { TeamsStore } from '../data/teams.store';

export interface TeamSaved {
  readonly mode: 'create' | 'update';
  readonly team: TeamResponse;
}

/** Account offered in one of the dialog's lists (manager or member). */
export interface AccountOption {
  readonly id: number;
  /** 'First Last', followed by '(inactive)': the label of the selection chips. */
  readonly label: string;
  readonly name: string;
  readonly email: string;
  readonly active: boolean;
  /** The account's current team (H10), absent for a manager. */
  readonly currentTeam: { readonly id: number; readonly name: string } | null;
}

/** SFD-05 E2 Team dialog: creation (team = null) or update of an existing team (name, manager, members). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule, MultiSelectModule, MessageModule, TagModule, FieldError, ErrorState, LoadingState, TPipe],
  selector: 'app-team-dialog',
  templateUrl: './team-dialog.html',
})
export class TeamDialog {
  protected readonly store = inject(TeamsStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Team to update, or `null` for a creation. */
  readonly team = input<TeamResponse | null>(null);
  readonly saved = output<TeamSaved>();
  /** Concurrent change detected: the caller reloads the list. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.team() !== null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-05-01: 2 to 100 characters. The input is never changed (spaces included): the back end removes surrounding
    // spaces and decides. The controls carry the API's field names to receive `errors[]`.
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    // RG-05-02 : un manager exactement.
    managerId: [null as number | null, [Validators.required]],
    // RG-05-07: a team may have no members.
    memberIds: [[] as number[]],
  });

  protected readonly nameMessages: FieldMessages = {
    required: 'teams.dialog.errors.nameRequired',
    minlength: 'teams.dialog.errors.nameTooShort',
    maxlength: 'teams.dialog.errors.nameTooLong',
  };
  protected readonly managerMessages: FieldMessages = { required: 'teams.dialog.errors.managerRequired' };
  protected readonly memberMessages: FieldMessages = {};

  /** Selected manager (mirror of the control, used to filter the offered members). */
  private readonly selectedManagerId = signal<number | null>(null);
  /**
   * Member removed from the selection because they were just chosen as manager (RG-05-05), to be reported to the user.
   */
  private readonly removedMember = signal<{ name: string | null } | null>(null);
  protected readonly removedMemberName = computed(() => {
    const removed = this.removedMember();
    return removed === null ? null : (removed.name ?? this.i18n.t('teams.dialog.unknownAccount'));
  });

  /** RG-05-02, CA-05-03: the API offers only active accounts with the MANAGER role. */
  protected readonly managerOptions = computed<AccountOption[]>(() =>
    this.store.managerCandidates().map((c) => ({
      id: c.id,
      label: `${c.firstName} ${c.lastName}`,
      name: `${c.firstName} ${c.lastName}`,
      email: c.email,
      active: c.active,
      currentTeam: null,
    })),
  );

  /**
   * RG-05-03, CA-05-05: the API offers no ADMIN. RG-05-05: the account chosen as manager is removed from the offered members.
   * H9, RG-05-11: an inactive account is still offered, flagged. H10: an account already in another team is offered and flagged.
   */
  protected readonly memberOptions = computed<AccountOption[]>(() => {
    const managerId = this.selectedManagerId();
    const editedTeamId = this.team()?.id;
    return this.store
      .memberCandidates()
      .filter((c) => c.id !== managerId)
      .map((c) => {
        const name = `${c.firstName} ${c.lastName}`;
        // H10: the team being edited is not flagged (the account is already one of its members).
        const currentTeam = c.currentTeam && c.currentTeam.id !== editedTeamId ? c.currentTeam : null;
        // The label is used as the option's aria-label and the chip's text: the state and the current team appear in
        // it.
        const label = `${name}${c.active ? '' : this.i18n.t('teams.dialog.inactiveSuffix')}${currentTeam ? this.i18n.t('teams.dialog.teamSuffix', { name: currentTeam.name }) : ''}`;
        return {
          id: c.id,
          label,
          name,
          email: c.email,
          active: c.active,
          currentTeam,
        };
      });
  });

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

    this.form.controls.managerId.valueChanges.pipe(takeUntilDestroyed()).subscribe((id) => this.onManagerChange(id));

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    const active = document.activeElement;
    this.originCy = active instanceof HTMLElement ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null) : null;
    this.submitting.set(false);
    this.removedMember.set(null);
    const team = this.team();
    // Update: the team's values (inactive members kept, RG-05-11). No event: RG-05-05 only applies to a choice.
    const managerId = team?.manager.id ?? null;
    this.selectedManagerId.set(managerId);
    this.form.reset({ name: team?.name ?? '', managerId, memberIds: team?.members.map((m) => m.id) ?? [] }, { emitEvent: false });
    this.store.loadCandidates();
  }

  /**
   * RG-05-05: the manager is not a member. If they were already ticked as a member, they are unticked and the user is told.
   */
  private onManagerChange(managerId: number | null): void {
    this.selectedManagerId.set(managerId);
    this.removedMember.set(null);
    if (managerId === null) {
      return;
    }
    const members = this.form.controls.memberIds.value;
    if (members.includes(managerId)) {
      const removed = this.store.memberCandidates().find((c) => c.id === managerId);
      this.form.controls.memberIds.setValue(members.filter((id) => id !== managerId));
      this.removedMember.set({ name: removed ? `${removed.firstName} ${removed.lastName}` : null });
    }
  }

  /**
   * Initial focus on the name. `onShow` fires at the end of the opening animation:
   * if the user is already typing in the form, focus is not taken back.
   */
  protected focusFirstField(firstField: HTMLInputElement): void {
    const active = document.activeElement;
    if (active !== firstField && active instanceof HTMLElement && firstField.form?.contains(active)) {
      return;
    }
    firstField.focus();
  }

  /** Retry destroys the clicked button: focus moves to the first field to stay inside the dialog. */
  protected retry(firstField: HTMLInputElement): void {
    firstField.focus();
    this.store.loadCandidates();
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
    if (this.submitting() || this.store.candidatesLoading()) {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    if (value.managerId === null) {
      return;
    }
    this.submitting.set(true);
    const edited = this.team();
    const request = { name: value.name, managerId: value.managerId, memberIds: value.memberIds };
    const request$ = edited ? this.store.update(edited.id, { ...request, version: edited.version }) : this.store.create(request);
    request$.subscribe({
      next: (team) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: edited ? 'update' : 'create', team });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // RG-05-01 (name), RG-05-02 (managerId), RG-05-03 / RG-05-04 / RG-05-05 (memberIds): under the field, dialog
        // open.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
        // Concurrent change (409 without a code or errors[]): the message is shown by the interceptor; we reload.
        if (edited && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
