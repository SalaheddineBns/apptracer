import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ButtonModule } from 'primeng/button';
import { MessageService } from 'primeng/api';
import { AuthStore } from '../../../core/auth/auth.store';
import { toProblem } from '../../../core/errors/problem';
import { I18n } from '../../../core/i18n/i18n.service';
import { TPipe } from '../../../core/i18n/t.pipe';
import type { FieldMessages } from '../../../shared/components/field-error';
import { PasswordField } from '../../../shared/components/password-field';
import { applyFieldErrors } from '../../../shared/forms/apply-field-errors';
import { PASSWORD_MESSAGES, passwordPolicyValidator, sameAs } from '../../../shared/forms/password-rules';

/** SFD-03 E3 Changer mon mot de passe. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonModule, PasswordField, TPipe],
  selector: 'app-change-password',
  templateUrl: './change-password.html',
})
export class ChangePasswordPage {
  private readonly auth = inject(AuthStore);
  private readonly messages = inject(MessageService);
  private readonly i18n = inject(I18n);

  protected readonly submitting = signal(false);

  // Les noms des contrôles `currentPassword` / `newPassword` sont ceux des `errors[].field` de l'API.
  protected readonly form = inject(FormBuilder).nonNullable.group({
    currentPassword: ['', [Validators.required]],
    // RG-03-02
    newPassword: ['', [Validators.required, passwordPolicyValidator]],
    // Validation côté formulaire uniquement : la confirmation n'est pas envoyée à l'API.
    confirmation: ['', [Validators.required, sameAs('newPassword')]],
  });

  protected readonly currentMessages: FieldMessages = { required: 'changePassword.errors.currentRequired' };
  protected readonly newMessages = PASSWORD_MESSAGES;
  protected readonly confirmationMessages: FieldMessages = {
    required: 'changePassword.errors.confirmationRequired',
    mismatch: 'changePassword.errors.mismatch',
  };

  constructor() {
    this.form.controls.newPassword.valueChanges.subscribe(() => this.form.controls.confirmation.updateValueAndValidity());
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
    const { currentPassword, newPassword } = this.form.getRawValue();
    this.auth.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.submitting.set(false);
        // Le blur du champ actif marque le contrôle « touched » : on le retire avant de réinitialiser, sinon
        // « La confirmation est obligatoire. » apparaît à la première tabulation suivant le succès.
        if (document.activeElement instanceof HTMLElement) {
          document.activeElement.blur();
        }
        this.form.reset();
        this.messages.add({ severity: 'success', summary: this.i18n.t('changePassword.done'), detail: this.i18n.t('changePassword.doneDetail'), life: 3000 });
      },
      error: (error: unknown) => {
        this.submitting.set(false);
        // RG-03-15 / RG-03-02 : les errors[] de l'API s'affichent sous le champ ; sans errors[], le toast vient de l'intercepteur.
        const unmatched = applyFieldErrors(this.form, toProblem(error).fieldErrors);
        for (const e of unmatched) {
          this.messages.add({ severity: 'error', summary: this.i18n.t('common.error'), detail: e.message, life: 5000 });
        }
      },
    });
  }
}
