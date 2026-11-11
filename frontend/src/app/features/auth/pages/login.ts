import { ChangeDetectionStrategy, Component, effect, inject, signal, untracked } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { AuthStore } from '../../../core/auth/auth.store';
import { safeReturnUrl } from '../../../core/auth/return-url';
import { homeUrlFor } from '../../../core/auth/roles';
import { I18n } from '../../../core/i18n/i18n.service';
import { LanguageSelector } from '../../../core/i18n/language-selector';
import { TPipe } from '../../../core/i18n/t.pipe';
import { toProblem } from '../../../core/errors/problem';
import { FieldError, FieldMessages } from '../../../shared/components/field-error';
import { PasswordField } from '../../../shared/components/password-field';

/** SFD-03 E1 Connexion. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, ButtonModule, InputTextModule, MessageModule, FieldError, PasswordField, LanguageSelector, TPipe],
  selector: 'app-login',
  templateUrl: './login.html',
})
export class LoginPage {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  private readonly i18n = inject(I18n);

  protected readonly submitting = signal(false);
  protected readonly errorMessage = signal<string | null>(null);

  protected readonly form = inject(FormBuilder).nonNullable.group({
    // No password composition rule at sign-in (RG-03-02 does not apply here).
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly emailMessages: FieldMessages = {
    required: 'login.errors.emailRequired',
    email: 'login.errors.emailInvalid',
  };
  protected readonly passwordMessages: FieldMessages = { required: 'password.errors.required' };

  constructor() {
    // RG-08-04: the server's message (invalid credentials) is in the request's language; it is cleared when the
    // language changes, and the user sees it translated on their next attempt.
    let previous = this.i18n.lang();
    effect(() => {
      const lang = this.i18n.lang();
      if (lang !== previous) {
        previous = lang;
        untracked(() => this.errorMessage.set(null));
      }
    });
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
    this.errorMessage.set(null);
    const { email, password } = this.form.getRawValue();
    this.auth.login(email, password).subscribe({
      next: (user) => {
        // CA-03-01: the requested page (internal only), otherwise the role's home page.
        const target = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')) ?? homeUrlFor(user.role);
        this.submitting.set(false);
        void this.router.navigateByUrl(target);
      },
      error: (error: unknown) => {
        // RG-03-04: identical message for an unknown e-mail, a wrong password and an inactive account (the API's
        // detail).
        this.errorMessage.set(toProblem(error).detail);
        this.submitting.set(false);
      },
    });
  }
}
