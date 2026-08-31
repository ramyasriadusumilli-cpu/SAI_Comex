import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';

import { ApiService } from './api.service';
import { CurrentUser, LoginResponse } from '../models/api.models';

const TOKEN_KEY = 'saicomex.token';
const USER_KEY = 'saicomex.user';

/**
 * Session state.
 *
 * The permission list held here drives what the UI renders — nothing more.
 * Every action is re-authorised server-side on the request itself, so a user
 * who edits their own localStorage gains a menu item and a 403, not access.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  readonly user = signal<CurrentUser | null>(restoreUser());
  readonly isLoggedIn = computed(() => !!this.user() && !!this.getToken());
  readonly permissions = computed(() => new Set(this.user()?.permissions ?? []));

  login(email: string, password: string): Observable<LoginResponse> {
    return this.api.post<LoginResponse>('/auth/login', { email, password }).pipe(
      tap(response => {
        localStorage.setItem(TOKEN_KEY, response.token);
        const user: CurrentUser = {
          userId: response.userId,
          email: response.email,
          fullName: response.fullName,
          roleCode: response.roleCode,
          roleName: response.roleName,
          permissions: response.permissions,
          projectIds: response.projectIds,
          shaftIds: response.shaftIds,
          mustChangePassword: response.mustChangePassword,
          preferredCurrency: response.preferredCurrency,
          companyName: 'SAIComex Mining Company',
          reportingCurrency: 'USD',
        };
        this.setUser(user);
        // The login payload does not carry company details; /auth/me does, and
        // the shell needs them for the header.
        this.refresh().subscribe({ error: () => {} });
      }),
    );
  }

  refresh(): Observable<CurrentUser> {
    return this.api.get<CurrentUser>('/auth/me').pipe(tap(user => this.setUser(user)));
  }

  logout(navigate = true): void {
    // Tell the server first so the token is blacklisted; clear locally either
    // way, because a failed logout call must not strand the operator signed in.
    this.api.post('/auth/logout').subscribe({ complete: () => {}, error: () => {} });
    this.clear();
    if (navigate) this.router.navigate(['/login']);
  }

  /** Session ended server-side (expired or revoked) — clear without a round trip. */
  sessionExpired(): void {
    this.clear();
    this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  has(permission: string): boolean {
    return this.permissions().has(permission);
  }

  hasAny(...permissions: string[]): boolean {
    return permissions.some(p => this.permissions().has(p));
  }

  /** True when the user can see any part of a module, whatever the action. */
  canSeeModule(module: string): boolean {
    return this.has(`${module}.view`);
  }

  private setUser(user: CurrentUser): void {
    this.user.set(user);
    localStorage.setItem(USER_KEY, JSON.stringify(user));
  }

  private clear(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.user.set(null);
  }
}

function restoreUser(): CurrentUser | null {
  try {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as CurrentUser) : null;
  } catch {
    return null;
  }
}
