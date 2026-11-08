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
    // Pas de règle de composition du mot de passe à la connexion (RG-03-02 ne s'applique pas ici).
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  protected readonly emailMessages: FieldMessages = {
    required: 'login.errors.emailRequired',
    email: 'login.errors.emailInvalid',
  };
  protected readonly passwordMessages: FieldMessages = { required: 'password.errors.required' };

  constructor() {
    // RG-08-04 : le message du serveur (« Identifiants invalides ») est dans la langue de la requête ; au changement de langue
    // il est effacé, l'utilisateur le retrouve traduit à sa prochaine tentative.
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
        // CA-03-01 : page demandée (interne uniquement) sinon accueil du rôle.
        const target = safeReturnUrl(this.route.snapshot.queryParamMap.get('returnUrl')) ?? homeUrlFor(user.role);
        this.submitting.set(false);
        void this.router.navigateByUrl(target);
      },
      error: (error: unknown) => {
        // RG-03-04 : message identique pour e-mail inconnu, mot de passe incorrect et compte inactif (detail de l'API).
        this.errorMessage.set(toProblem(error).detail);
        this.submitting.set(false);
      },
    });
  }
}
