import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { DashboardApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { CategoryBreakdown, ShaftDetail, ShaftKpis } from '../../core/models/api.models';
import { MoneyPipe, PercentPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/**
 * SRS §8 shaft record and SRS §45 drill-down.
 *
 * This is the bottom of the dashboard's drill path: a KPI on the executive
 * dashboard leads to a project, a project row leads here, and the cost
 * breakdown card below is the last hop — every row on it links out to the
 * underlying expenses, so a number on this page is never a dead end.
 */
@Component({
  selector: 'app-shaft-detail',
  imports: [RouterLink, FormsModule, MoneyPipe, QuantityPipe, PercentPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading shaft…</div>
      } @else if (shaft(); as s) {

        <div class="crumbs">
          <a routerLink="/shafts">Shafts</a><span class="sep">›</span><span>{{ s.name }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ s.name }} <span class="muted mono" style="font-weight:400; font-size:14px">{{ s.code }}</span></h1>
            <div class="row" style="gap:8px">
              <span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span>
              <span class="page-sub">{{ s.projectName ?? '—' }} · {{ s.operationName ?? '—' }}</span>
            </div>
          </div>
          <div class="row">
            @if (auth.has('shafts.edit')) {
              <button class="btn btn-secondary" (click)="router.navigateByUrl('/shafts/' + s.id + '/edit')">Edit</button>
            }
          </div>
        </div>

        @if (auth.has('shafts.edit')) {
          <div class="card" style="margin-bottom:16px">
            <div class="card-header"><div class="card-title">Change status</div></div>
            <div class="row">
              <select class="select" [(ngModel)]="newStatus">
                @for (st of reference.data()?.shaftStatuses; track st) { <option [value]="st">{{ st }}</option> }
              </select>
              <input class="input" style="min-width:280px" placeholder="Reason for the change (required)" [(ngModel)]="statusReason">
              <button class="btn" [disabled]="changingStatus()" (click)="applyStatus(s)">
                @if (changingStatus()) { <span class="spin"></span> Applying… } @else { Apply }
              </button>
            </div>
          </div>
        }

        @if (kpiLoading()) {
          <div class="loading-block"><span class="spin"></span> Loading performance…</div>
        } @else if (kpis(); as k) {
          <div class="kpi-grid" style="margin-bottom:16px">
            <div class="kpi kpi-accent">
              <span class="kpi-label">Production</span>
              <span class="kpi-value">{{ k.production | qty: k.productionUnit }}</span>
              <span class="kpi-sub">{{ k.periodStart | shortDate }} – {{ k.periodEnd | shortDate }}</span>
            </div>
            @if (auth.has('financial.view')) {
              <div class="kpi">
                <span class="kpi-label">Revenue</span>
                <span class="kpi-value">{{ k.revenue | money: k.currency }}</span>
              </div>
              <div class="kpi">
                <span class="kpi-label">Expenses</span>
                <span class="kpi-value">{{ k.expenses | money: k.currency }}</span>
              </div>
              <div class="kpi" [class.negative]="k.netResult < 0">
                <span class="kpi-label">Net result</span>
                <span class="kpi-value">{{ k.netResult | money: k.currency }}</span>
              </div>
              <div class="kpi">
                <span class="kpi-label">Cost per unit</span>
                <span class="kpi-value">{{ k.costPerUnit ? (k.costPerUnit | money: k.currency) : '—' }}</span>
              </div>
              <div class="kpi">
                <span class="kpi-label">Partner payable</span>
                <span class="kpi-value">{{ k.partnerPayable ? (k.partnerPayable | money: k.currency) : '—' }}</span>
              </div>
              <div class="kpi">
                <span class="kpi-label">Partner outstanding</span>
                <span class="kpi-value">{{ k.partnerOutstanding ? (k.partnerOutstanding | money: k.currency) : '—' }}</span>
              </div>
            }
          </div>
        }

        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Shaft record</div></div>
          <dl class="dl">
            <div><dt>Project</dt><dd><a [routerLink]="'/projects/' + s.projectId">{{ s.projectName ?? '—' }}</a></dd></div>
            <div><dt>Operation</dt><dd>{{ s.operationName ?? '—' }}</dd></div>
            <div><dt>Owner partner</dt><dd>@if (s.ownerPartnerId) { <a [routerLink]="'/partners/' + s.ownerPartnerId">{{ s.ownerPartnerName }}</a> } @else { — }</dd></div>
            <div><dt>Shaft manager</dt><dd>{{ s.shaftManagerName ?? '—' }}</dd></div>
            <div><dt>Shaft number</dt><dd>{{ s.shaftNumber ?? '—' }}</dd></div>
            <div><dt>GPS</dt><dd>{{ s.latitude && s.longitude ? (s.latitude + ', ' + s.longitude) : '—' }}</dd></div>
            <div><dt>Depth</dt><dd>{{ s.depthMetres ? (s.depthMetres + ' m') : '—' }}</dd></div>
            <div><dt>Commissioned</dt><dd>{{ s.commissionedDate | shortDate }}</dd></div>
            <div><dt>Start date</dt><dd>{{ s.startDate | shortDate }}</dd></div>
            <div><dt>Closure date</dt><dd>{{ s.closureDate | shortDate }}</dd></div>
            <div><dt>Production target</dt><dd>{{ s.productionTarget ? (s.productionTarget | qty: s.productionTargetUnit) + ' / ' + (s.productionTargetPeriod ?? '') : '—' }}</dd></div>
            <div><dt>Active contract</dt><dd>@if (s.activeContractId) { <a [routerLink]="'/contracts/' + s.activeContractId">{{ s.activeContractNumber }}</a> } @else { — }</dd></div>
            <div style="grid-column: 1 / -1"><dt>Description</dt><dd>{{ s.description ?? '—' }}</dd></div>
            <div style="grid-column: 1 / -1"><dt>Notes</dt><dd>{{ s.notes ?? '—' }}</dd></div>
          </dl>
        </div>

        @if (auth.has('financial.view')) {
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Cost breakdown</div>
                <div class="card-sub">Click a category to see the underlying expenses</div>
              </div>
            </div>
            @if (expensesLoading()) {
              <div class="loading-block"><span class="spin"></span> Loading cost breakdown…</div>
            } @else {
              <div class="table-wrap">
                <table class="data">
                  <thead><tr><th>Category</th><th>Class</th><th class="num">Amount</th><th class="num">% of total</th></tr></thead>
                  <tbody>
                    @for (c of expenses(); track c.categoryCode) {
                      <tr class="clickable" (click)="router.navigate(['/expenses'], { queryParams: { shaftId: s.id } })">
                        <td><strong>{{ c.categoryName }}</strong></td>
                        <td class="muted">{{ c.expenseClass }}</td>
                        <td class="num">{{ c.amount | money }}</td>
                        <td class="num">{{ c.percentOfTotal | pct }}</td>
                      </tr>
                    } @empty {
                      <tr><td class="empty" colspan="4">No expenses recorded against this shaft yet.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            }
          </div>
        }
      }
    </div>
  `,
  styles: [`
    .kpi.negative .kpi-value { color: var(--red); }
  `],
})
export class ShaftDetailPage implements OnInit {

  readonly id = input.required<string>();

  private readonly api = inject(ShaftApi);
  private readonly dashboardApi = inject(DashboardApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly kpiLoading = signal(true);
  protected readonly expensesLoading = signal(true);
  protected readonly changingStatus = signal(false);

  protected readonly shaft = signal<ShaftDetail | null>(null);
  protected readonly kpis = signal<ShaftKpis | null>(null);
  protected readonly expenses = signal<CategoryBreakdown[]>([]);

  protected newStatus = '';
  protected statusReason = '';

  ngOnInit(): void {
    this.reload();
  }

  protected applyStatus(s: ShaftDetail): void {
    if (!this.statusReason.trim()) {
      this.toast.error('Enter a reason for the status change.');
      return;
    }
    if (!this.newStatus || this.newStatus === s.status) {
      this.toast.error('Choose a different status to apply.');
      return;
    }
    this.changingStatus.set(true);
    this.api.setStatus(s.id, this.newStatus, this.statusReason.trim()).subscribe({
      next: updated => {
        this.shaft.set(updated);
        this.changingStatus.set(false);
        this.statusReason = '';
        this.toast.success('Shaft status updated');
      },
      error: () => this.changingStatus.set(false),
    });
  }

  private reload(): void {
    const shaftId = Number(this.id());
    this.loading.set(true);
    this.api.get(shaftId).subscribe({
      next: s => { this.shaft.set(s); this.newStatus = s.status; this.loading.set(false); },
      error: () => this.loading.set(false),
    });

    this.kpiLoading.set(true);
    this.dashboardApi.shaftKpis(shaftId).subscribe({
      next: k => { this.kpis.set(k); this.kpiLoading.set(false); },
      error: () => this.kpiLoading.set(false),
    });

    this.expensesLoading.set(true);
    this.dashboardApi.shaftExpenses(shaftId).subscribe({
      next: e => { this.expenses.set(e); this.expensesLoading.set(false); },
      error: () => this.expensesLoading.set(false),
    });
  }
}
