import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { ConfirmationService, MenuItem } from 'primeng/api';
import { AvatarModule } from 'primeng/avatar';
import { ButtonModule } from 'primeng/button';
import { ConfirmDialogModule } from 'primeng/confirmdialog';
import { MenuModule } from 'primeng/menu';
import { Role } from '../api/generated';
import { AuthStore } from '../auth/auth.store';
import { I18n } from '../i18n/i18n.service';
import { LANGS } from '../i18n/lang';
import { TPipe } from '../i18n/t.pipe';
import { FocusAnchor, FocusRestorer } from '../../shared/focus-restorer';
import { MenuRadio } from './menu-radio';

/** Application shell (SFD-03 E2 Header): wraps the protected pages; `/login` stays outside. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarModule, ButtonModule, MenuModule, ConfirmDialogModule, MenuRadio, TPipe],
  selector: 'app-shell',
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly focus = inject(FocusRestorer);
  private readonly i18n = inject(I18n);
  private anchor: FocusAnchor | null = null;

  constructor() {
    // p-confirmdialog does not return focus on close: we return it to the original button (keyboard, accessibility).
    inject(ConfirmationService)
      .requireConfirmation$.pipe(takeUntilDestroyed())
      .subscribe(() => (this.anchor = this.focus.capture()));
  }

  /**
   * Closing the confirmation (Escape, Cancel, confirmed action, close button): focus returns to the trigger,
   * or to the button that replaces it once the action is done (Deactivate becomes Reactivate).
   */
  protected restoreFocus(): void {
    const anchor = this.anchor;
    this.anchor = null;
    this.focus.restore(anchor);
  }

  protected readonly fullName = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName} ${user.lastName}`.trim() : '';
  });
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase() : '';
  });

  // RG-03-10: My journal for USER and MANAGER (hierarchy), Accounts for ADMIN only.
  protected readonly showJournal = computed(() => this.auth.hasRole(Role.User));
  // RG-04-01, CA-04-05, CA-04-06: My team reserved for the MANAGER (absent for USER and ADMIN).
  protected readonly showTeamView = computed(() => this.auth.hasRole(Role.Manager));
  protected readonly showAccounts = computed(() => this.auth.hasRole(Role.Admin));
  // RG-05-08, CA-05-11: Teams reserved for the ADMIN.
  protected readonly showTeams = computed(() => this.auth.hasRole(Role.Admin));
  // RG-02-08, CA-02-09: Activity types reserved for the ADMIN.
  protected readonly showActivityTypes = computed(() => this.auth.hasRole(Role.Admin));
  // RG-02-08, CA-02-09: Projects reserved for the ADMIN.
  protected readonly showProjects = computed(() => this.auth.hasRole(Role.Admin));

  // RG-09-07, CA-09-09: Job titles reserved for the ADMIN.
  protected readonly showJobTitles = computed(() => this.auth.hasRole(Role.Admin));

  // RG-06-03, CA-06-07: Settings reserved for the ADMIN.
  protected readonly showSettings = computed(() => this.auth.hasRole(Role.Admin));

  // SFD-08 E1: language choice, next to Change my password; the language in force is checked (`current`).
  protected readonly menuItems = computed<MenuItem[]>(() => {
    const current = this.i18n.lang();
    return [
      { label: this.i18n.t('userMenu.changePassword'), dataCy: 'menu-changer-mot-de-passe', command: () => void this.router.navigate(['/account/password']) },
      {
        label: this.i18n.t('language.label'),
        dataCy: 'menu-langue',
        items: LANGS.map((lang) => ({
          label: this.i18n.t(`language.${lang}`),
          dataCy: `choix-langue-${lang}`,
          lang,
          current: lang === current,
          command: () => this.i18n.setLang(lang),
        })),
      },
      { label: this.i18n.t('userMenu.signOut'), dataCy: 'menu-se-deconnecter', command: () => this.logout() },
    ];
  });

  /** RG-03-08 / CA-03-08: revokes the refresh token, then redirects to `/login` (handled by the store). */
  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
