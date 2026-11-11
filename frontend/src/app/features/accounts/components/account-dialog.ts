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
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MessageService } from 'primeng/api';
import { ButtonModule } from 'primeng/button';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { SelectModule } from 'primeng/select';
import { Role, UserResponse } from '../../../core/api/generated';
import { AuthStore } from '../../../core/auth/auth.store';
import { ROLE_LABEL_KEYS } from '../../../core/auth/roles';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { PasswordField } from '../../../shared/components/password-field';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { PASSWORD_MESSAGES, passwordPolicyValidator } from '../../../shared/forms/password-rules';
import { JobTitlesStore } from '../../job-titles/data/job-titles.store';
import { AccountsStore } from '../data/accounts.store';

export interface AccountSaved {
  readonly mode: 'create' | 'update';
  readonly user: UserResponse;
}

/** SFD-03 E5 Account dialog: creation (account = null) or update of an existing account. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    MessageModule,
    SelectModule,
    FieldError,
    PasswordField,
    TPipe,
  ],
  selector: 'app-account-dialog',
  templateUrl: './account-dialog.html',
})
export class AccountDialog {
  private readonly store = inject(AccountsStore);
  private readonly jobTitles = inject(JobTitlesStore);
  private readonly auth = inject(AuthStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  readonly visible = model(false);
  /** Account to update, or `null` for a creation. */
  readonly account = input<UserResponse | null>(null);
  readonly saved = output<AccountSaved>();
  /** Concurrent change detected: the caller reloads the list. */
  readonly conflict = output<void>();

  protected readonly submitting = signal(false);
  protected readonly isEdit = computed(() => this.account() !== null);
  /** RG-03-13: an ADMIN cannot remove their own ADMIN role; the field is locked on their own account. */
  protected readonly isSelf = computed(() => {
    const account = this.account();
    return account !== null && account.id === this.auth.user()?.id;
  });

  protected readonly roleOptions = computed(() =>
    [Role.User, Role.Manager, Role.Admin].map((value) => ({ label: this.i18n.t(ROLE_LABEL_KEYS[value]), value })),
  );

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // RG-03-01 / SFD : prénom et nom 100 caractères au plus, e-mail au format valide.
    firstName: ['', [Validators.required, Validators.maxLength(100)]],
    lastName: ['', [Validators.required, Validators.maxLength(100)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(254)]],
    role: [Role.User, [Validators.required]],
    // RG-09-02 : fonction facultative (code), réservée aux rôles USER et MANAGER ; champ nommé comme `errors[].field`.
    jobTitleCode: [null as string | null],
    // RG-03-02 / RG-03-12 : mot de passe initial, création uniquement (champ nommé comme `errors[].field`).
    initialPassword: ['', [Validators.required, passwordPolicyValidator]],
  });

  /** Selected role (follows the form, including the locked role of one's own account). */
  private readonly selectedRole = signal<Role>(Role.User);
  /** RG-09-02: an ADMIN account has no job title; the list is offered for USER and MANAGER only. */
  protected readonly jobTitleVisible = computed(() => this.selectedRole() !== Role.Admin);

  /**
   * RG-09-06: the active job titles, plus the inactive one the account already has (flagged 'Inactive').
   * Labels are data, never translated (RG-09-10).
   */
  protected readonly jobTitleOptions = computed(() => {
    const options = this.jobTitles.activeItems().map((item) => ({ label: item.label, value: item.code, sortKey: item.label }));
    const current = this.account()?.jobTitle;
    if (current && !options.some((option) => option.value === current.code)) {
      const label = current.active ? current.label : this.i18n.t('accounts.dialog.jobTitleInactiveOption', { label: current.label });
      options.push({ label, value: current.code, sortKey: current.label });
    }
    // RG-09-08 : ordre alphabétique des libellés, la fonction inactive portée par le compte comprise.
    return options.sort((a, b) => a.sortKey.localeCompare(b.sortKey));
  });
  protected readonly jobTitlesError = computed(() => this.jobTitles.error());

  protected reloadJobTitles(): void {
    this.jobTitles.load();
  }

  protected readonly firstNameMessages: FieldMessages = {
    required: 'accounts.dialog.errors.firstNameRequired',
    maxlength: 'accounts.dialog.errors.firstNameTooLong',
  };
  protected readonly lastNameMessages: FieldMessages = {
    required: 'accounts.dialog.errors.lastNameRequired',
    maxlength: 'accounts.dialog.errors.lastNameTooLong',
  };
  protected readonly emailMessages: FieldMessages = {
    required: 'accounts.dialog.errors.emailRequired',
    email: 'accounts.dialog.errors.emailInvalid',
    maxlength: 'accounts.dialog.errors.emailTooLong',
  };
  protected readonly roleMessages: FieldMessages = { required: 'accounts.dialog.errors.roleRequired' };
  protected readonly passwordMessages = PASSWORD_MESSAGES;

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

    // RG-09-02, RG-09-03 : pour le rôle Administrateur, le champ est désactivé sans perdre sa valeur (un retour à
    // Utilisateur ou Manager avant d'enregistrer la retrouve) ; la fonction n'est pas envoyée pour un ADMIN.
    this.form.controls.role.valueChanges.pipe(takeUntilDestroyed()).subscribe((role) => {
      this.selectedRole.set(role);
      const jobTitle = this.form.controls.jobTitleCode;
      if (role === Role.Admin) {
        jobTitle.disable({ emitEvent: false });
      } else if (!this.submitting()) {
        jobTitle.enable({ emitEvent: false });
      }
    });

    effect(() => {
      if (this.visible()) {
        untracked(() => this.prepare());
      }
    });
  }

  private prepare(): void {
    const account = this.account();
    const active = document.activeElement;
    this.originCy =
      active instanceof HTMLElement
        ? (active.closest('[data-cy]')?.getAttribute('data-cy') ?? null)
        : null;
    this.submitting.set(false);
    this.form.reset({
      firstName: account?.firstName ?? '',
      lastName: account?.lastName ?? '',
      email: account?.email ?? '',
      role: account?.role ?? Role.User,
      jobTitleCode: account?.role === Role.Admin ? null : (account?.jobTitle?.code ?? null),
      initialPassword: '',
    });
    this.selectedRole.set(account?.role ?? Role.User);
    // Modification : e-mail non modifiable, pas de mot de passe ; sur son propre compte, rôle verrouillé (RG-03-13).
    this.setEnabled(this.form.controls.email, !account);
    this.setEnabled(this.form.controls.initialPassword, !account);
    this.setEnabled(this.form.controls.role, !(account && this.isSelf()));
  }

  private setEnabled(control: { enable: () => void; disable: () => void }, enabled: boolean): void {
    if (enabled) {
      control.enable();
    } else {
      control.disable();
    }
  }

  /**
   * Initial focus on the first field. `onShow` fires at the end of the opening animation:
   * if the user is already typing in the form, focus is not taken back.
   */
  protected focusFirstField(firstField: HTMLInputElement): void {
    const active = document.activeElement;
    if (
      active !== firstField &&
      active instanceof HTMLElement &&
      firstField.form?.contains(active)
    ) {
      return;
    }
    firstField.focus();
  }

  protected close(): void {
    this.visible.set(false);
  }

  /** Returns focus to the original button (found by its data-cy: the row may have been recreated). */
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
    const account = this.account();
    const value = this.form.getRawValue();
    // RG-09-03 : pas de fonction pour un ADMIN (non envoyée). RG-09-04 : à la modification, un code absent retire la fonction ;
    // le code de la fonction conservée est donc toujours envoyé, y compris lors d'un passage USER <-> MANAGER.
    const jobTitle = value.role !== Role.Admin && value.jobTitleCode ? { jobTitleCode: value.jobTitleCode } : {};
    const request$ = account
      ? this.store.update(account.id, {
          firstName: value.firstName,
          lastName: value.lastName,
          role: value.role,
          ...jobTitle,
          version: account.version,
        })
      : this.store.create({
          firstName: value.firstName,
          lastName: value.lastName,
          email: value.email,
          role: value.role,
          ...jobTitle,
          initialPassword: value.initialPassword,
        });
    request$.subscribe({
      next: (user) => {
        this.submitting.set(false);
        this.visible.set(false);
        this.saved.emit({ mode: account ? 'update' : 'create', user });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        const problem = toProblem(error);
        // CA-03-10 / RG-03-02 / RG-03-13 : les errors[] de l'API s'affichent sous le champ, le dialogue reste ouvert.
        for (const e of applyFieldErrors(this.form, problem.fieldErrors)) {
          this.messages.add({
            severity: 'error',
            summary: this.i18n.t('common.error'),
            detail: e.message,
            life: 5000,
          });
        }
        // Modification concurrente (409 sans code ni errors[]) : le message est affiché par l'intercepteur ; on recharge.
        if (
          account &&
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
}
