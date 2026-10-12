import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CreateUserRequest, UpdateUserRequest, UserResponse, UsersService } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';

/** Seuls champs de tri acceptés par le back (toute autre valeur donne 400). */
export const ACCOUNT_SORT_FIELDS = ['lastName', 'firstName', 'email', 'role', 'active'] as const;
export type AccountSortField = (typeof ACCOUNT_SORT_FIELDS)[number];

export function isAccountSortField(value: unknown): value is AccountSortField {
  return typeof value === 'string' && (ACCOUNT_SORT_FIELDS as readonly string[]).includes(value);
}

/** État de l'écran Comptes (SFD-03 E4) : liste paginée et triée côté serveur. */
@Injectable({ providedIn: 'root' })
export class AccountsStore {
  private readonly api = inject(UsersService);
  private requestId = 0;

  readonly users = signal<UserResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  /** Vrai dès qu'une première réponse est arrivée (distingue le chargement initial des rechargements). */
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  readonly page = signal(0);
  readonly size = signal(20);
  readonly sortField = signal<AccountSortField>('lastName');
  readonly sortOrder = signal<1 | -1>(1);

  /** Recharge la liste avec les paramètres courants. */
  load(): void {
    const id = ++this.requestId;
    this.loading.set(true);
    this.error.set(null);
    const sort = `${this.sortField()},${this.sortOrder() === 1 ? 'asc' : 'desc'}`;
    // L'erreur est affichée par l'écran (état d'erreur « Réessayer »), pas dans un toast.
    const context = new HttpContext().set(SKIP_ERROR_TOAST, true);
    this.api.listUsers(this.page(), this.size(), [sort], 'body', false, { context }).subscribe({
      next: (result) => {
        if (id !== this.requestId) {
          return;
        }
        this.users.set(result.content);
        this.total.set(result.totalElements);
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: (error: unknown) => {
        if (id !== this.requestId) {
          return;
        }
        this.error.set(toProblem(error).detail);
        this.loading.set(false);
      },
    });
  }

  query(page: number, size: number, field: AccountSortField, order: 1 | -1): void {
    this.page.set(page);
    this.size.set(size);
    this.sortField.set(field);
    this.sortOrder.set(order);
    this.load();
  }

  /** RG-03-11, RG-03-12 : création par un ADMIN. La liste est rechargée (pagination et tri côté serveur). */
  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.api.createUser(request).pipe(tap(() => this.load()));
  }

  /** RG-03-11, RG-03-13 : modification (prénom, nom, rôle) avec la version reçue. */
  update(id: number, request: UpdateUserRequest): Observable<UserResponse> {
    return this.api.updateUser(id, request).pipe(tap((user) => this.replace(user)));
  }

  /** RG-03-11 / RG-03-14. Les erreurs (ex. 409 RG-03-13) sont affichées par l'intercepteur d'erreurs. */
  deactivate(id: number): Observable<UserResponse> {
    return this.api.deactivateUser(id).pipe(tap((user) => this.replace(user)));
  }

  reactivate(id: number): Observable<UserResponse> {
    return this.api.reactivateUser(id).pipe(tap((user) => this.replace(user)));
  }

  private replace(user: UserResponse): void {
    this.users.update((list) => list.map((u) => (u.id === user.id ? user : u)));
  }
}
