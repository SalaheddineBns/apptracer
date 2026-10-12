import { DOCUMENT } from '@angular/common';
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

/** Coquille applicative (SFD-03 E2 En-tête) : enveloppe les pages protégées, `/login` reste en dehors. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, AvatarModule, ButtonModule, MenuModule, ConfirmDialogModule],
  selector: 'app-shell',
  templateUrl: './shell.html',
})
export class Shell {
  private readonly auth = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);
  private confirmOpener: HTMLElement | null = null;

  constructor() {
    // p-confirmdialog ne rend pas le focus à la fermeture : on le rend au bouton d'origine (clavier, accessibilité).
    inject(ConfirmationService)
      .requireConfirmation$.pipe(takeUntilDestroyed())
      .subscribe(() => {
        const active = this.document.activeElement;
        this.confirmOpener = active instanceof HTMLElement && active !== this.document.body ? active : null;
      });
  }

  /** Fermeture de la confirmation (Échap, « Annuler », « Désactiver », croix) : le focus revient au déclencheur. */
  protected restoreFocus(): void {
    const opener = this.confirmOpener;
    this.confirmOpener = null;
    // Après la fermeture du dialogue (le piège à focus de PrimeNG reprend sinon le focus).
    setTimeout(() => {
      if (opener?.isConnected) {
        opener.focus();
      }
    });
  }

  protected readonly fullName = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName} ${user.lastName}`.trim() : '';
  });
  protected readonly initials = computed(() => {
    const user = this.auth.user();
    return user ? `${user.firstName.charAt(0)}${user.lastName.charAt(0)}`.toUpperCase() : '';
  });

  // RG-03-10 : « Mon journal » pour USER et MANAGER (hiérarchie), « Comptes » pour ADMIN uniquement.
  protected readonly showJournal = computed(() => this.auth.hasRole(Role.User));
  protected readonly showAccounts = computed(() => this.auth.hasRole(Role.Admin));
  // RG-02-08, CA-02-09 : « Types d'activité » réservé à l'ADMIN.
  protected readonly showActivityTypes = computed(() => this.auth.hasRole(Role.Admin));
  // RG-02-08, CA-02-09 : « Projets » réservé à l'ADMIN.
  protected readonly showProjects = computed(() => this.auth.hasRole(Role.Admin));

  protected readonly menuItems: MenuItem[] = [
    { label: 'Changer mon mot de passe', dataCy: 'menu-changer-mot-de-passe', command: () => void this.router.navigate(['/compte/mot-de-passe']) },
    { label: 'Se déconnecter', dataCy: 'menu-se-deconnecter', command: () => this.logout() },
  ];

  /** RG-03-08 / CA-03-08 : révoque le refresh token puis redirige vers `/login` (géré par le store). */
  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
