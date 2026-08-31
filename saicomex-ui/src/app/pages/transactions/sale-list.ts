import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ProjectApi, ShaftApi, TransactionApi } from '../../core/services/domain.services';
import { PageResponse, ProjectSummary, SaleSummary, ShaftSummary } from '../../core/models/api.models';
import { MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §23 — mineral sales. Read-only: sales are recorded through the intake workflow, not this screen. */
@Component({
  selector: 'app-sale-list',
  imports: [FormsModule, MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Sales</h1>
          <div class="page-sub">Mineral sales across all shafts</div>
        </div>
      </div>

      <div class="toolbar">
        <select class="select" [(ngModel)]="projectId" (change)="onFilterProjectChange()">
          <option value="">All projects</option>
          @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="shaftId" (change)="reload()">
          <option value="">All shafts</option>
          @for (s of shafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="CONFIRMED">Confirmed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <input class="input" type="date" [(ngModel)]="from" (change)="reload()">
        <span class="muted">to</span>
        <input class="input" type="date" [(ngModel)]="to" (change)="reload()">
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading sales…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Sale #</th><th>Date</th><th>Project</th><th>Shaft</th><th>Buyer</th><th>Product</th>
                  <th class="num">Quantity</th><th class="num">Unit price</th>
                  <th class="num">Gross</th><th class="num">Net</th><th>Currency</th>
                  <th>Payment</th><th>Settlement</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (s of page()?.content ?? []; track s.id) {
                  <tr>
                    <td class="mono">{{ s.saleNumber }}</td>
                    <td class="nowrap">{{ s.saleDate | shortDate }}</td>
                    <td class="muted">{{ s.projectName ?? '—' }}</td>
                    <td class="muted">{{ s.shaftName ?? '—' }}</td>
                    <td class="muted">{{ s.buyerName ?? '—' }}</td>
                    <td>{{ s.product }}</td>
                    <td class="num">{{ s.quantity | qty: s.unitCode }}</td>
                    <td class="num">{{ s.unitPrice | money }}</td>
                    <td class="num">{{ s.grossAmount | money }}</td>
                    <td class="num">{{ s.netAmount | money }}</td>
                    <td class="muted">{{ s.currency }}</td>
                    <td class="muted">{{ s.paymentStatus ?? '—' }}</td>
                    <td class="muted">{{ s.settlementStatus ?? '—' }}</td>
                    <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="14">No sales match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} sales</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class SaleListPage {

  private readonly api = inject(TransactionApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<SaleSummary> | null>(null);
  protected readonly pageNo = signal(0);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);

  protected projectId = '';
  protected shaftId = '';
  protected status = '';
  protected from = '';
  protected to = '';

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.reload();
  }

  protected onFilterProjectChange(): void {
    this.shaftId = '';
    this.shaftApi.options(this.projectId ? { projectId: this.projectId } : undefined)
      .subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.reload();
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.sales({
      status: this.status || undefined,
      projectId: this.projectId || undefined,
      shaftId: this.shaftId || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
