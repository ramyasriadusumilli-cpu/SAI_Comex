import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { AuthService } from '../../core/services/auth.service';
import { ReferenceService } from '../../core/services/domain.services';

@Component({
  selector: 'app-login',
  imports: [FormsModule],
  template: `
    <div class="auth">
      <form class="auth-card" (ngSubmit)="submit()">
        <div class="auth-brand">
          <span class="auth-mark">SC</span>
          <div>
            <h1>SAIComex</h1>
            <p class="page-sub">Mining Operations &amp; Management Platform</p>
          </div>
        </div>

        @if (expired()) {
          <div class="banner banner-warn">Your session has ended. Please sign in again.</div>
        }
        @if (error()) {
          <div class="banner banner-error">{{ error() }}</div>
        }

        <div class="field">
          <label for="email">Email</label>
          <input id="email" class="input" type="email" name="email"
                 [(ngModel)]="email" autocomplete="username" required autofocus>
        </div>

        <div class="field">
          <label for="password">Password</label>
          <input id="password" class="input" type="password" name="password"
                 [(ngModel)]="password" autocomplete="current-password" required>
        </div>

        <button class="btn auth-submit" type="submit" [disabled]="busy()">
          @if (busy()) { <span class="spin"></span> Signing in… } @else { Sign in }
        </button>

        <p class="auth-foot">Authorised users only. All activity is recorded.</p>
      </form>
    </div>
  `,
  styles: [`
    .auth {
      min-height: 100vh; display: grid; place-items: center; padding: 24px;
      background:
        radial-gradient(1100px 460px at 12% -8%, rgba(184,117,20,.16), transparent 62%),
        linear-gradient(160deg, #1E2124 0%, #24282C 58%, #1A1D20 100%);
    }
    .auth-card {
      width: 100%; max-width: 380px;
      background: var(--card); border-radius: 16px; padding: 26px;
      box-shadow: 0 24px 60px -18px rgba(0,0,0,.55);
      display: flex; flex-direction: column; gap: 15px;
    }
    .auth-brand { display: flex; gap: 12px; align-items: center; margin-bottom: 4px; }
    .auth-mark {
      width: 40px; height: 40px; flex: 0 0 40px; border-radius: 11px;
      background: var(--brand); color: #fff;
      display: grid; place-items: center; font-weight: 800; font-size: 14px;
    }
    .auth-brand h1 { font-size: 19px; }
    .auth-brand p { margin: 1px 0 0; }
    .auth-submit { justify-content: center; margin-top: 4px; padding: 10px; font-size: 13px; }
    .auth-foot { margin: 2px 0 0; font-size: 10.5px; color: var(--mut); text-align: center; }
  `],
})
export class LoginPage {

  private readonly auth = inject(AuthService);
  private readonly reference = inject(ReferenceService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);

  protected email = '';
  protected password = '';
  protected readonly busy = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly expired = signal(this.route.snapshot.queryParamMap.has('expired'));

  protected submit(): void {
    if (this.busy()) return;
    this.error.set(null);
    this.expired.set(false);

    if (!this.email || !this.password) {
      this.error.set('Enter your email and password.');
      return;
    }

    this.busy.set(true);
    this.auth.login(this.email.trim(), this.password).subscribe({
      next: response => {
        this.reference.ensureLoaded();
        if (response.mustChangePassword) {
          // The seeded administrator lands here on first sign-in. Say so
          // plainly rather than dropping them on a dashboard with a silent
          // obligation attached.
          this.error.set(null);
        }
        const returnUrl = this.route.snapshot.queryParamMap.get('returnUrl');
        this.router.navigateByUrl(returnUrl || '/dashboard');
      },
      error: err => {
        this.busy.set(false);
        this.error.set(err?.error?.message ?? 'Incorrect email or password.');
      },
    });
  }
}
