import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi } from '../../core/services/domain.services';
import { AuditEntry, PageResponse } from '../../core/models/api.models';
import { DateTimePipe } from '../../shared/format';

/** SRS §39 — the immutable audit trail behind every change the platform records. */
@Component({
  selector: 'app-audit-log',
  imports: [FormsModule, DateTimePipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Audit log</h1>
          <div class="page-sub">Every recorded change, who made it and when</div>
        </div>
      </div>

      <div class="toolbar">
        <input class="input" placeholder="Action…" [(ngModel)]="action" (keyup.enter)="reload()" (change)="reload()">
        <input class="input" placeholder="Entity type…" [(ngModel)]="entityType" (keyup.enter)="reload()" (change)="reload()">
        <input class="input" placeholder="User email…" [(ngModel)]="userEmail" (keyup.enter)="reload()" (change)="reload()">
        <input class="input" type="date" [(ngModel)]="from" (change)="reload()">
        <span class="muted">to</span>
        <input class="input" type="date" [(ngModel)]="to" (change)="reload()">
        <div class="spacer"></div>
        <button class="btn btn-secondary btn-sm" (click)="reset()">Clear filters</button>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading audit entries…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Occurred at</th><th>User</th><th>Role</th><th>Action</th><th>Entity type</th>
                  <th>Entity</th><th>Field</th><th>Change</th><th>Reason</th><th>IP</th>
                </tr>
              </thead>
              <tbody>
                @for (a of page()?.content ?? []; track a.id) {
                  <tr>
                    <td class="nowrap">{{ a.occurredAt | dateTime }}</td>
                    <td class="muted">{{ a.userEmail ?? '—' }}</td>
                    <td class="muted">{{ a.userRole ?? '—' }}</td>
                    <td><span class="pill pill-info">{{ a.action }}</span></td>
                    <td class="muted">{{ a.entityType }}</td>
                    <td class="muted">{{ a.entityLabel ?? (a.entityId ?? '—') }}</td>
                    <td class="mono muted">{{ a.fieldName ?? '—' }}</td>
                    <td class="mono">
                      @if (a.fieldName) {
                        <span class="old-value">{{ a.oldValue ?? '—' }}</span>
                        <span class="muted"> → </span>
                        <span>{{ a.newValue ?? '—' }}</span>
                      } @else {
                        {{ a.summary ?? '—' }}
                      }
                    </td>
                    <td class="muted">{{ a.reason ?? '—' }}</td>
                    <td class="mono muted">{{ a.ipAddress ?? '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="10">No audit entries match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} entries</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .old-value { text-decoration: line-through; color: var(--mut); }
  `],
})
export class AuditLogPage {

  private readonly api = inject(AdminApi);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<AuditEntry> | null>(null);
  protected readonly pageNo = signal(0);

  protected action = '';
  protected entityType = '';
  protected userEmail = '';
  protected from = '';
  protected to = '';

  constructor() {
    this.reload();
  }

  protected reset(): void {
    this.action = '';
    this.entityType = '';
    this.userEmail = '';
    this.from = '';
    this.to = '';
    this.setPage(0);
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.audit({
      action: this.action || undefined,
      entityType: this.entityType || undefined,
      userEmail: this.userEmail || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
