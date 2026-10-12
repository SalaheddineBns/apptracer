import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { MenuItem } from 'primeng/api';
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

  protected readonly menuItems: MenuItem[] = [
    { label: 'Changer mon mot de passe', dataCy: 'menu-changer-mot-de-passe', command: () => void this.router.navigate(['/compte/mot-de-passe']) },
    { label: 'Se déconnecter', dataCy: 'menu-se-deconnecter', command: () => this.logout() },
  ];

  /** RG-03-08 / CA-03-08 : révoque le refresh token puis redirige vers `/login` (géré par le store). */
  protected logout(): void {
    this.auth.logout().subscribe();
  }
}
