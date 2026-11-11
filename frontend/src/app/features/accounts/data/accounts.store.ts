import { HttpContext } from '@angular/common/http';
import { inject, Injectable, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import { CreateUserRequest, UpdateUserRequest, UserResponse, UsersService } from '../../../core/api/generated';
import { SKIP_ERROR_TOAST } from '../../../core/errors/error.interceptor';
import { toProblem } from '../../../core/errors/problem';
import { PendingIds } from '../../../shared/pending-ids';

/** Only sort fields accepted by the back end (any other value gives 400). */
export const ACCOUNT_SORT_FIELDS = ['lastName', 'firstName', 'email', 'role', 'active'] as const;
export type AccountSortField = (typeof ACCOUNT_SORT_FIELDS)[number];

export function isAccountSortField(value: unknown): value is AccountSortField {
  return typeof value === 'string' && (ACCOUNT_SORT_FIELDS as readonly string[]).includes(value);
}

/** State of the Accounts screen (SFD-03 E4): list paginated and sorted on the server. */
@Injectable({ providedIn: 'root' })
export class AccountsStore {
  private readonly api = inject(UsersService);
  private requestId = 0;

  /** Deactivations / reactivations waiting for a response: one request at a time per row. */
  readonly pending = new PendingIds();

  readonly users = signal<UserResponse[]>([]);
  readonly total = signal(0);
  readonly loading = signal(false);
  /** True as soon as a first response has arrived (tells the initial load apart from reloads). */
  readonly loaded = signal(false);
  readonly error = signal<string | null>(null);

  readonly page = signal(0);
  readonly size = signal(20);
  readonly sortField = signal<AccountSortField>('lastName');
  readonly sortOrder = signal<1 | -1>(1);

  /** Reloads the list with the current parameters. */
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

  /**
   * Loads a page / a sort. No effect if the parameters match the current state (p-table re-emits its state on initialisation).
   */
  query(page: number, size: number, field: AccountSortField, order: 1 | -1): void {
    if (page === this.page() && size === this.size() && field === this.sortField() && order === this.sortOrder()) {
      return;
    }
    this.page.set(page);
    this.size.set(size);
    this.sortField.set(field);
    this.sortOrder.set(order);
    this.load();
  }

  /** RG-03-11, RG-03-12: creation by an ADMIN. The list is reloaded (pagination and sorting on the server). */
  create(request: CreateUserRequest): Observable<UserResponse> {
    return this.api.createUser(request).pipe(tap(() => this.load()));
  }

  /** RG-03-11, RG-03-13: update (first name, last name, role) with the version received. */
  update(id: number, request: UpdateUserRequest): Observable<UserResponse> {
    return this.api.updateUser(id, request).pipe(tap((user) => this.replace(user)));
  }

  /** RG-03-11 / RG-03-14. Errors (e.g. 409 RG-03-13) are shown by the error interceptor. */
  deactivate(id: number): Observable<UserResponse> {
    return this.pending.guard(id, () => this.api.deactivateUser(id).pipe(tap((user) => this.replace(user))));
  }

  reactivate(id: number): Observable<UserResponse> {
    return this.pending.guard(id, () => this.api.reactivateUser(id).pipe(tap((user) => this.replace(user))));
  }

  private replace(user: UserResponse): void {
    this.users.update((list) => list.map((u) => (u.id === user.id ? user : u)));
  }
}
