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
import { ErrorState } from '../../../shared/components/error-state';
import { FieldError } from '../../../shared/components/field-error';
import { LoadingState } from '../../../shared/components/loading-state';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { TeamsStore } from '../data/teams.store';

export interface TeamSaved {
  readonly mode: 'create' | 'update';
  readonly team: TeamResponse;
}

/** Compte proposé dans une liste du dialogue (manager ou membre). */
export interface AccountOption {
  readonly id: number;
  /** « Prénom Nom », suivi de « (inactif) » : c'est le libellé des pastilles de la sélection. */
  readonly label: string;
  readonly name: string;
  readonly email: string;
  readonly active: boolean;
  /** Équipe actuelle du compte (H10), absente pour un manager. */
  readonly currentTeam: { readonly id: number; readonly name: string } | null;
}

/** SFD-05 E2 Dialogue équipe : création (team = null) ou modification d'une équipe existante (nom, manager, membres). */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, DialogModule, ButtonModule, InputTextModule, SelectModule, MultiSelectModule, MessageModule, TagModule, FieldError, ErrorState, LoadingState],
  selector: 'app-team-dialog',
  templateUrl: './team-dialog.html',
})
export class TeamDialog {
  protected readonly store = inject(TeamsStore);
  private readonly messages = inject(MessageService);

  readonly visible = model(false);
  /** Équipe à modifier, ou `null` pour une création. */
  readonly team = input<TeamResponse | null>(null);
  readonly saved = output<TeamSaved>();
  /** Modification concurrente détectée : l'appelant recharge la liste. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.team() !== null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-05-01 : 2 à 100 caractères. La saisie n'est jamais modifiée (espaces compris) : le back retire les espaces de bord
    // et tranche. Les contrôles portent le nom des champs de l'API pour recevoir `errors[]`.
    name: ['', [Validators.required, Validators.minLength(2), Validators.maxLength(100)]],
    // RG-05-02 : un manager exactement.
    managerId: [null as number | null, [Validators.required]],
    // RG-05-07 : une équipe peut n'avoir aucun membre.
    memberIds: [[] as number[]],
  });

  protected readonly nameMessages = {
    required: 'Le nom est obligatoire.',
    minlength: 'Le nom doit comporter au moins 2 caractères.',
    maxlength: 'Le nom ne doit pas dépasser 100 caractères.',
  };
  protected readonly managerMessages = { required: 'Le manager est obligatoire.' };
  protected readonly memberMessages = {};

  /** Manager choisi (miroir du contrôle, pour filtrer les membres proposés). */
  private readonly selectedManagerId = signal<number | null>(null);
  /** Membre retiré de la sélection parce qu'il vient d'être choisi comme manager (RG-05-05), à signaler à l'utilisateur. */
  protected readonly removedMemberName = signal<string | null>(null);

  /** RG-05-02, CA-05-03 : l'API ne propose que des comptes actifs de rôle MANAGER. */
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
   * RG-05-03, CA-05-05 : l'API ne propose ni ADMIN. RG-05-05 : le compte choisi comme manager est retiré des membres proposés.
   * H9, RG-05-11 : un compte inactif reste proposé, signalé. H10 : un compte déjà dans une autre équipe est proposé et signalé.
   */
  protected readonly memberOptions = computed<AccountOption[]>(() => {
    const managerId = this.selectedManagerId();
    const editedTeamId = this.team()?.id;
    return this.store
      .memberCandidates()
      .filter((c) => c.id !== managerId)
      .map((c) => {
        const name = `${c.firstName} ${c.lastName}`;
        // H10 : on ne signale pas l'équipe en cours de modification (le compte en est déjà membre).
        const currentTeam = c.currentTeam && c.currentTeam.id !== editedTeamId ? c.currentTeam : null;
        // Le libellé sert d'aria-label de l'option et de texte de la pastille : l'état et l'équipe actuelle y figurent.
        const label = `${name}${c.active ? '' : ' (inactif)'}${currentTeam ? ` (équipe ${currentTeam.name})` : ''}`;
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
    // p-dialog n'évalue `closeOnEscape` qu'à l'ouverture : pendant l'envoi, on intercepte Échap avant son écouteur (document).
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
    this.removedMemberName.set(null);
    const team = this.team();
    // Modification : valeurs de l'équipe (membres inactifs conservés, RG-05-11). Sans événement : RG-05-05 ne s'applique qu'à un choix.
    const managerId = team?.manager.id ?? null;
    this.selectedManagerId.set(managerId);
    this.form.reset({ name: team?.name ?? '', managerId, memberIds: team?.members.map((m) => m.id) ?? [] }, { emitEvent: false });
    this.store.loadCandidates();
  }

  /** RG-05-05 : le manager n'est pas membre. S'il était déjà coché comme membre, il est décoché et l'utilisateur en est informé. */
  private onManagerChange(managerId: number | null): void {
    this.selectedManagerId.set(managerId);
    this.removedMemberName.set(null);
    if (managerId === null) {
      return;
    }
    const members = this.form.controls.memberIds.value;
    if (members.includes(managerId)) {
      const removed = this.store.memberCandidates().find((c) => c.id === managerId);
      this.form.controls.memberIds.setValue(members.filter((id) => id !== managerId));
      this.removedMemberName.set(removed ? `${removed.firstName} ${removed.lastName}` : 'Ce compte');
    }
  }

  /**
   * Focus initial sur le nom. `onShow` arrive à la fin de l'animation d'ouverture :
   * si l'utilisateur saisit déjà dans le formulaire, on ne lui reprend pas le focus.
   */
  protected focusFirstField(firstField: HTMLInputElement): void {
    const active = document.activeElement;
    if (active !== firstField && active instanceof HTMLElement && firstField.form?.contains(active)) {
      return;
    }
    firstField.focus();
  }

  /** « Réessayer » détruit le bouton cliqué : le focus passe au premier champ pour rester dans le dialogue. */
  protected retry(firstField: HTMLInputElement): void {
    firstField.focus();
    this.store.loadCandidates();
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
        // RG-05-01 (name), RG-05-02 (managerId), RG-05-03 / RG-05-04 / RG-05-05 (memberIds) : sous le champ, dialogue ouvert.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({ severity: 'error', summary: 'Erreur', detail: e.message, life: 5000 });
        }
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (edited && problem.status === 409 && problem.code === null && problem.fieldErrors.length === 0) {
          this.visible.set(false);
          this.conflict.emit();
        }
      },
    });
  }
}
