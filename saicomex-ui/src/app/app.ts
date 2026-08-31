import { Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ToastHost } from './shared/toast-host';
import { AuthService } from './core/services/auth.service';
import { ReferenceService } from './core/services/domain.services';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, ToastHost],
  template: `
    <app-toast-host />
    <router-outlet />
  `,
})
export class App {
  private auth = inject(AuthService);
  private reference = inject(ReferenceService);

  constructor() {
    // A returning operator arrives with a token in storage but no reference
    // data in memory, so warm it here rather than on every screen.
    if (this.auth.isLoggedIn()) this.reference.ensureLoaded();
  }
}
