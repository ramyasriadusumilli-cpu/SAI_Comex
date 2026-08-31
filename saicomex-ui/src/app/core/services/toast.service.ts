import { Injectable, signal } from '@angular/core';

export interface Toast {
  id: number;
  kind: 'success' | 'error' | 'info';
  message: string;
}

@Injectable({ providedIn: 'root' })
export class ToastService {

  readonly toasts = signal<Toast[]>([]);
  private nextId = 1;

  success(message: string): void { this.push('success', message, 4000); }
  info(message: string): void    { this.push('info', message, 4000); }

  /** Errors linger: the operator needs time to read what went wrong. */
  error(message: string): void   { this.push('error', message, 9000); }

  dismiss(id: number): void {
    this.toasts.update(list => list.filter(t => t.id !== id));
  }

  private push(kind: Toast['kind'], message: string, ttl: number): void {
    const id = this.nextId++;
    this.toasts.update(list => [...list, { id, kind, message }]);
    setTimeout(() => this.dismiss(id), ttl);
  }
}
