import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { SettlementApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { SettlementDetail, SettlementLineDto } from '../../core/models/api.models';
import { MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

type Tab = 'derivation' | 'sources' | 'details';

const LINE_TYPES = ['REVENUE', 'EXPENSE', 'PRODUCTION'] as const;

/** SRS §25, §57 — a single settlement statement: its derivation trail and every source record behind it. */
@Component({
  selector: 'app-settlement-detail',
  imports: [RouterLink, MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading settlement…</div>
      } @else if (settlement(); as s) {

        <div class="crumbs">
          <a routerLink="/settlements">Settlements</a><span class="sep">›</span><span>{{ s.settlementNumber }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ s.settlementNumber }}</h1>
            <div class="row" style="gap:8px">
              <span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span>
              <span class="page-sub">{{ s.partnerName ?? '—' }} · {{ s.shaftName ?? '—' }} · {{ s.periodStart | shortDate }} – {{ s.periodEnd | shortDate }}</span>
            </div>
          </div>
          <div class="row">
            @if (auth.has('settlements.calculate') && (s.status === 'DRAFT' || s.status === 'CALCULATED')) {
              <button class="btn btn-secondary" [disabled]="busy()" (click)="recalculate(s)">Recalculate</button>
            }
            @if (auth.has('settlements.approve') && (s.status === 'CALCULATED' || s.status === 'PENDING_APPROVAL')) {
              <button class="btn" [disabled]="busy()" (click)="approve(s)">Approve</button>
            }
            @if (auth.has('settlements.edit') && s.status !== 'PAID') {
              <button class="btn btn-danger" [disabled]="busy()" (click)="cancel(s)">Cancel</button>
            }
          </div>
        </div>

        <div class="kpi-grid" style="margin-bottom:16px">
          <div class="kpi"><span class="kpi-label">Gross revenue</span><span class="kpi-value">{{ s.grossRevenue | money: s.currency }}</span></div>
          <div class="kpi"><span class="kpi-label">Total deductions</span><span class="kpi-value">{{ s.totalDeductions | money: s.currency }}</span></div>
          <div class="kpi kpi-accent"><span class="kpi-label">Net distributable</span><span class="kpi-value">{{ s.netDistributable | money: s.currency }}</span></div>
          <div class="kpi"><span class="kpi-label">SAIComex share</span><span class="kpi-value">{{ s.saicomexShare | money: s.currency }}</span></div>
          <div class="kpi"><span class="kpi-label">Partner payable</span><span class="kpi-value">{{ s.partnerNetPayable | money: s.currency }}</span></div>
          <div class="kpi"><span class="kpi-label">Paid</span><span class="kpi-value">{{ s.amountPaid | money: s.currency }}</span></div>
          <div class="kpi" [class.warn]="s.amountOutstanding > 0"><span class="kpi-label">Outstanding</span><span class="kpi-value">{{ s.amountOutstanding | money: s.currency }}</span></div>
        </div>

        <div class="tabs">
          <button class="tab" [class.active]="tab() === 'derivation'" (click)="tab.set('derivation')">Derivation</button>
          <button class="tab" [class.active]="tab() === 'sources'" (click)="tab.set('sources')">Source records</button>
          <button class="tab" [class.active]="tab() === 'details'" (click)="tab.set('details')">Details</button>
        </div>

        @switch (tab()) {
          @case ('derivation') {
            <div class="card">
              <div class="table-wrap">
                <table class="data">
                  <thead>
                    <tr><th class="num">#</th><th>Stage</th><th>Rule</th><th>Expression</th><th class="num">Amount</th><th class="num">Running balance</th><th>Beneficiary</th></tr>
                  </thead>
                  <tbody>
                    @for (step of s.steps; track step.stepNo) {
                      <tr>
                        <td class="num">{{ step.stepNo }}</td>
                        <td><span class="pill pill-info">{{ step.stage }}</span></td>
                        <td>{{ step.ruleName ?? '—' }}</td>
                        <td class="mono">{{ step.expression }}</td>
                        <td class="num">{{ step.resultAmount | money: s.currency }}</td>
                        <td class="num muted">{{ step.runningBalance != null ? (step.runningBalance | money: s.currency) : '—' }}</td>
                        <td class="muted">{{ step.beneficiary ?? '—' }}</td>
                      </tr>
                    } @empty {
                      <tr><td class="empty" colspan="7">No calculation steps recorded for this settlement.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }
          @case ('sources') {
            <!-- SRS §57 drill-down endpoint: the partner's payable figure traced to the
                 individual transaction that produced it, grouped the way the waterfall groups them. -->
            @for (type of lineTypes; track type) {
              <div class="card" style="margin-bottom:14px">
                <div class="card-header"><div class="card-title">{{ type | statusLabel }}</div></div>
                <div class="table-wrap">
                  <table class="data">
                    <thead>
                      <tr><th>Date</th><th>Description</th><th>Category</th><th class="num">Quantity</th><th class="num">Amount</th><th>Source</th></tr>
                    </thead>
                    <tbody>
                      @for (l of linesByType()[type]; track l.id) {
                        <tr>
                          <td class="nowrap">{{ l.lineDate | shortDate }}</td>
                          <td>{{ l.description }}</td>
                          <td class="muted">{{ l.categoryCode ?? '—' }}</td>
                          <td class="num">{{ l.quantity != null ? (l.quantity | qty: l.unitCode) : '—' }}</td>
                          <td class="num">{{ l.amount | money: (l.currency ?? s.currency) }}</td>
                          <td class="mono muted">{{ l.sourceTable }} #{{ l.sourceId ?? '—' }}</td>
                        </tr>
                      } @empty {
                        <tr><td class="empty" colspan="6">No {{ type.toLowerCase() }} lines included in this settlement.</td></tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            }
          }
          @case ('details') {
            <div class="card">
              <dl class="dl">
                <div><dt>Contract</dt><dd>@if (s.contractId) { <a [routerLink]="'/contracts/' + s.contractId">{{ s.contractNumber }}</a> } @else { — }</dd></div>
                <div><dt>Agreement</dt><dd>{{ s.agreementName ?? '—' }}</dd></div>
                <div><dt>Period</dt><dd>{{ s.periodStart | shortDate }} – {{ s.periodEnd | shortDate }}</dd></div>
                <div><dt>Settlement date</dt><dd>{{ s.settlementDate | shortDate }}</dd></div>
                <div><dt>Calculated by</dt><dd>{{ s.calculatedBy ?? '—' }}</dd></div>
                <div><dt>Calculated at</dt><dd>{{ s.calculatedAt | shortDate }}</dd></div>
                <div><dt>Approved by</dt><dd>{{ s.approvedBy ?? '—' }}</dd></div>
                <div><dt>Approved at</dt><dd>{{ s.approvedAt | shortDate }}</dd></div>
                <div style="grid-column: 1 / -1"><dt>Notes</dt><dd>{{ s.notes ?? '—' }}</dd></div>
              </dl>
            </div>
          }
        }
      }
    </div>
  `,
  styles: [`
    .kpi.warn .kpi-value { color: var(--amber); }
  `],
})
export class SettlementDetailPage implements OnInit {

  readonly id = input.required<string>();

  private readonly api = inject(SettlementApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly settlement = signal<SettlementDetail | null>(null);
  protected readonly tab = signal<Tab>('derivation');
  protected readonly lineTypes = LINE_TYPES;

  protected readonly linesByType = computed<Record<string, SettlementLineDto[]>>(() => {
    const lines = this.settlement()?.lines ?? [];
    const grouped: Record<string, SettlementLineDto[]> = {};
    for (const type of LINE_TYPES) grouped[type] = lines.filter(l => l.lineType === type);
    return grouped;
  });

  ngOnInit(): void {
    this.reload();
  }

  protected recalculate(s: SettlementDetail): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.recalculate(s.id).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Settlement recalculated'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  protected approve(s: SettlementDetail): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.approve(s.id).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Settlement approved'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  protected cancel(s: SettlementDetail): void {
    if (this.busy()) return;
    const reason = prompt('Reason for cancelling this settlement (required):');
    if (!reason) return;
    this.busy.set(true);
    this.api.cancel(s.id, reason).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Settlement cancelled'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.api.get(Number(this.id())).subscribe({
      next: s => { this.settlement.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
