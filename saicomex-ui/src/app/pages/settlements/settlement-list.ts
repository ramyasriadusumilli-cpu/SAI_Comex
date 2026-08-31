import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { PartnerApi, ProjectApi, SettlementApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { PageResponse, PartnerSummary, ProjectSummary, SettlementSummary, ShaftSummary } from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §25 — partner settlement statements, paged and filterable by shaft, partner and period. */
@Component({
  selector: 'app-settlement-list',
  imports: [FormsModule, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Settlements</h1>
          <div class="page-sub">Partner statements for each settlement period</div>
        </div>
        @if (auth.has('settlements.calculate')) {
          <button class="btn" (click)="router.navigateByUrl('/settlements/new')"> Run a settlement
          </button>
        }
      </div>

      <div class="toolbar">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="CALCULATED">Calculated</option>
          <option value="PENDING_APPROVAL">Pending approval</option>
          <option value="APPROVED">Approved</option>
          <option value="PARTIALLY_PAID">Partially paid</option>
          <option value="PAID">Paid</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <select class="select" [(ngModel)]="projectId" (change)="onProjectChange()">
          <option value="">All projects</option>
          @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="shaftId" (change)="reload()">
          <option value="">All shafts</option>
          @for (s of shafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="partnerId" (change)="reload()">
          <option value="">All partners</option>
          @for (p of partners(); track p.id) { <option [value]="p.id">{{ p.legalName }}</option> }
        </select>
        <input class="input" type="date" [(ngModel)]="from" (change)="reload()">
        <span class="muted">to</span>
        <input class="input" type="date" [(ngModel)]="to" (change)="reload()">
        <div class="spacer"></div>
        <button class="btn btn-secondary btn-sm" (click)="reset()">Clear filters</button>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading settlements…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Settlement #</th><th>Project</th><th>Shaft</th><th>Partner</th><th>Period</th>
                  <th class="num">Gross revenue</th><th class="num">Net distributable</th>
                  <th class="num">SAIComex share</th><th class="num">Partner payable</th>
                  <th class="num">Paid</th><th class="num">Outstanding</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (s of page()?.content ?? []; track s.id) {
                  <tr class="clickable" (click)="router.navigateByUrl('/settlements/' + s.id)">
                    <td class="mono">{{ s.settlementNumber }}</td>
                    <td class="muted">{{ s.projectName ?? '—' }}</td>
                    <td class="muted">{{ s.shaftName ?? '—' }}</td>
                    <td class="muted">{{ s.partnerName ?? '—' }}</td>
                    <td class="nowrap">{{ s.periodStart | shortDate }} – {{ s.periodEnd | shortDate }}</td>
                    <td class="num">{{ s.grossRevenue | money: s.currency }}</td>
                    <td class="num">{{ s.netDistributable | money: s.currency }}</td>
                    <td class="num">{{ s.saicomexShare | money: s.currency }}</td>
                    <td class="num">{{ s.partnerNetPayable | money: s.currency }}</td>
                    <td class="num">{{ s.amountPaid | money: s.currency }}</td>
                    <td class="num">{{ s.amountOutstanding | money: s.currency }}</td>
                    <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="12">No settlements match these filters. Try clearing them, or run a new settlement.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} settlements</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class SettlementListPage {

  private readonly api = inject(SettlementApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly partnerApi = inject(PartnerApi);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<SettlementSummary> | null>(null);
  protected readonly pageNo = signal(0);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);
  protected readonly partners = signal<PartnerSummary[]>([]);

  protected status = '';
  protected projectId = '';
  protected shaftId = '';
  protected partnerId = '';
  protected from = '';
  protected to = '';

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.partnerApi.options().subscribe({ next: p => this.partners.set(p), error: () => {} });
    this.reload();
  }

  protected onProjectChange(): void {
    this.shaftId = '';
    this.shaftApi.options(this.projectId ? { projectId: this.projectId } : undefined)
      .subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.reload();
  }

  protected reset(): void {
    this.status = '';
    this.projectId = '';
    this.shaftId = '';
    this.partnerId = '';
    this.from = '';
    this.to = '';
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.setPage(0);
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({
      status: this.status || undefined,
      projectId: this.projectId || undefined,
      shaftId: this.shaftId || undefined,
      partnerId: this.partnerId || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
