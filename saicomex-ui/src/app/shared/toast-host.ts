import { Component, inject } from '@angular/core';

import { ToastService } from '../core/services/toast.service';

@Component({
  selector: 'app-toast-host',
  template: `
    <div class="toast-host">
      @for (toast of toasts.toasts(); track toast.id) {
        <div class="toast toast-{{ toast.kind }}">
          <span>{{ toast.message }}</span>
          <button class="toast-close" (click)="toasts.dismiss(toast.id)" aria-label="Dismiss">&times;</button>
        </div>
      }
    </div>
  `,
})
export class ToastHost {
  protected readonly toasts = inject(ToastService);
}
