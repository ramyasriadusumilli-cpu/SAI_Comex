import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from '../services/auth.service';
import { ToastService } from '../services/toast.service';

export const authGuard: CanActivateFn = (_route, state) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) return true;

  // Carry the requested URL through the sign-in round trip so a deep link
  // from an email or a bookmark still lands where it was pointing.
  const returnUrl = state.url && state.url !== '/' ? state.url : undefined;
  router.navigate(['/login'], returnUrl ? { queryParams: { returnUrl } } : {});
  return false;
};

/**
 * Route-level permission gate.
 *
 * This is UI affordance only — every endpoint behind the route enforces the
 * same permission server-side. Its job is to keep an operator from landing on
 * a screen that would only ever show them errors.
 */
export const permissionGuard = (permission: string): CanActivateFn => () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const toast = inject(ToastService);

  if (auth.has(permission)) return true;
  toast.error('You do not have access to that area.');
  router.navigate(['/dashboard']);
  return false;
};
