import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AlertDto, PageResponse, ShaftSummary } from '../../core/models/api.models';
import { DateTimePipe, QuantityPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §31 — operational alerts raised against thresholds. */
@Component({
  selector: 'app-alert-list',
  imports: [FormsModule, DateTimePipe, QuantityPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Alerts</h1>
          <div class="page-sub">Threshold breaches and operational warnings</div>
        </div>
      </div>

      <div class="toolbar">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="ACKNOWLEDGED">Acknowledged</option>
          <option value="RESOLVED">Resolved</option>
          <option value="DISMISSED">Dismissed</option>
        </select>
        <select class="select" [(ngModel)]="severity" (change)="reload()">
          <option value="">All severities</option>
          <option value="CRITICAL">Critical</option>
          <option value="WARNING">Warning</option>
          <option value="INFO">Info</option>
        </select>
        <select class="select" [(ngModel)]="category" (change)="reload()">
          <option value="">All categories</option>
          @for (c of categories(); track c) { <option [value]="c">{{ c | statusLabel }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading alerts…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Triggered at</th><th>Severity</th><th>Category</th><th>Title</th><th>Message</th>
                  <th>Shaft</th><th class="num">Actual</th><th class="num">Threshold</th><th>Status</th>
                  @if (auth.has('alerts.edit')) { <th></th> }
                </tr>
              </thead>
              <tbody>
                @for (a of page()?.content ?? []; track a.id) {
                  <tr>
                    <td class="nowrap">{{ a.triggeredAt | dateTime }}</td>
                    <td><span [class]="severityClass(a.severity)">{{ a.severity }}</span></td>
                    <td class="muted">{{ a.category | statusLabel }}</td>
                    <td><strong>{{ a.title }}</strong></td>
                    <td class="muted">{{ a.message }}</td>
                    <td class="muted">{{ shaftName(a.shaftId) }}</td>
                    <td class="num">{{ a.actualValue != null ? (a.actualValue | qty) : '—' }}</td>
                    <td class="num muted">{{ a.thresholdValue != null ? (a.thresholdValue | qty) : '—' }}</td>
                    <td><span [class]="statusPillClass(a.status)">{{ a.status | statusLabel }}</span></td>
                    @if (auth.has('alerts.edit')) {
                      <td>
                        <div class="row" style="flex-wrap:nowrap">
                          @if (a.status === 'OPEN') {
                            <button class="btn btn-ghost btn-sm" [disabled]="busyId() === a.id" (click)="acknowledge(a)">Acknowledge</button>
                          }
                          @if (a.status !== 'RESOLVED' && a.status !== 'DISMISSED') {
                            <button class="btn btn-ghost btn-sm" [disabled]="busyId() === a.id" (click)="resolve(a)">Resolve</button>
                          }
                        </div>
                      </td>
                    }
                  </tr>
                } @empty {
                  <tr><td class="empty" [attr.colspan]="auth.has('alerts.edit') ? 10 : 9">No alerts match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} alerts</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class AlertListPage {

  private readonly api = inject(AdminApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly busyId = signal<number | null>(null);
  protected readonly page = signal<PageResponse<AlertDto> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly shafts = signal<Map<number, string>>(new Map());

  // Matches alert_rules.category seeded in V7__reference_data.sql — there is
  // no reference-data endpoint for this list, so it is kept in step by hand.
  protected readonly categories = signal<string[]>([
    'PRODUCTION', 'EXPENSE', 'CONTRACT', 'INVENTORY', 'EQUIPMENT', 'REPORTING', 'FINANCIAL',
  ]);

  protected status = '';
  protected severity = '';
  protected category = '';

  constructor() {
    this.shaftApi.options().subscribe({
      next: (list: ShaftSummary[]) => this.shafts.set(new Map(list.map(s => [s.id, s.name]))),
      error: () => {},
    });
    this.reload();
  }

  protected shaftName(shaftId?: number): string {
    if (!shaftId) return '—';
    return this.shafts().get(shaftId) ?? ('#' + shaftId);
  }

  /** StatusClassPipe has no case for the alert severities, so they get their own mapping onto the existing pill colours. */
  protected severityClass(severity: string): string {
    switch (severity) {
      case 'CRITICAL': return 'pill pill-suspended';
      case 'WARNING': return 'pill pill-pending';
      default: return 'pill pill-info';
    }
  }

  protected statusPillClass(status: string): string {
    switch (status) {
      case 'OPEN': return 'pill pill-pending';
      case 'RESOLVED': return 'pill pill-active';
      case 'DISMISSED': return 'pill pill-suspended';
      default: return 'pill';
    }
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.alerts({
      status: this.status || undefined,
      severity: this.severity || undefined,
      category: this.category || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected acknowledge(a: AlertDto): void {
    const note = prompt('Note (optional):') ?? undefined;
    this.busyId.set(a.id);
    this.api.acknowledgeAlert(a.id, note || undefined).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success('Alert acknowledged');
        this.reload();
      },
      error: () => this.busyId.set(null),
    });
  }

  protected resolve(a: AlertDto): void {
    const note = prompt('Note (optional):') ?? undefined;
    this.busyId.set(a.id);
    this.api.resolveAlert(a.id, note || undefined).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success('Alert resolved');
        this.reload();
      },
      error: () => this.busyId.set(null),
    });
  }
}
