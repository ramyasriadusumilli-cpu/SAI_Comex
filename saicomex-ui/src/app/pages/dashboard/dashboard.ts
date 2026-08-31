import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { DashboardApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import {
  ExecutiveDashboard, ProjectPerformance, ShaftPerformance,
} from '../../core/models/api.models';
import {
  MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe,
} from '../../shared/format';

/**
 * SRS §5 — the executive dashboard, and the top of the SRS §45 drill-down.
 *
 * Every tile and every row here is a link into the level below it: a KPI goes
 * to the list it summarises, a project row goes to its shafts, a shaft row
 * goes to that shaft's cost breakdown. SRS §57 makes that path mandatory, and
 * building the dashboard as a set of dead-end numbers is the usual way it
 * quietly fails to be delivered.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, MoneyPipe, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Executive Dashboard</h1>
          <div class="page-sub">
            {{ auth.user()?.companyName }} — group position
            @if (data(); as d) { <span> · {{ d.periodStart | shortDate }} to {{ d.periodEnd | shortDate }}</span> }
          </div>
        </div>
        <div class="row">
          <input class="input" type="date" [value]="from()" (change)="setFrom($any($event.target).value)">
          <span class="muted">to</span>
          <input class="input" type="date" [value]="to()" (change)="setTo($any($event.target).value)">
          <button class="btn btn-secondary btn-sm" (click)="thisMonth()">This month</button>
          <button class="btn btn-secondary btn-sm" (click)="thisYear()">Year to date</button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading group position…</div>
      } @else if (data(); as d) {

        @for (note of d.dataNotes; track note) {
          <div class="banner banner-info" style="margin-bottom:12px">{{ note }}
          </div>
        }

        <!-- Portfolio -->
        <h2 style="margin:4px 0 10px">Portfolio</h2>
        <div class="kpi-grid">
          <div class="kpi clickable kpi-accent" (click)="go('/projects')">
            <span class="kpi-label">Projects</span>
            <span class="kpi-value">{{ d.totalProjects }}</span>
            <span class="kpi-sub">{{ d.activeProjects }} active · {{ d.suspendedProjects }} suspended · {{ d.closedProjects }} closed</span>
          </div>
          <div class="kpi clickable" (click)="go('/operations')">
            <span class="kpi-label">Mining operations</span>
            <span class="kpi-value">{{ d.totalOperations }}</span>
            <span class="kpi-sub">across all projects</span>
          </div>
          <div class="kpi clickable" (click)="go('/shafts')">
            <span class="kpi-label">Shafts</span>
            <span class="kpi-value">{{ d.totalShafts }}</span>
            <span class="kpi-sub">{{ d.activeShafts }} active</span>
          </div>
          <div class="kpi clickable" [class.warn]="d.nonProducingShafts > 0" (click)="go('/shafts')">
            <span class="kpi-label">Not producing</span>
            <span class="kpi-value">{{ d.nonProducingShafts }}</span>
            <span class="kpi-sub">active shafts with no recent production</span>
          </div>
        </div>

        <!-- Production -->
        <h2 style="margin:20px 0 10px">Production</h2>
        <div class="kpi-grid">
          <div class="kpi clickable" (click)="go('/production')">
            <span class="kpi-label">Today</span>
            <span class="kpi-value">{{ d.productionToday | qty }}</span>
            <span class="kpi-sub">{{ d.productionUnit }}</span>
          </div>
          <div class="kpi clickable" (click)="go('/production')">
            <span class="kpi-label">This week</span>
            <span class="kpi-value">{{ d.productionThisWeek | qty }}</span>
            <span class="kpi-sub">{{ d.productionUnit }}</span>
          </div>
          <div class="kpi clickable" (click)="go('/production')">
            <span class="kpi-label">This month</span>
            <span class="kpi-value">{{ d.productionThisMonth | qty }}</span>
            <span class="kpi-sub">{{ d.productionUnit }}</span>
          </div>
          <div class="kpi clickable kpi-accent" (click)="go('/production')">
            <span class="kpi-label">Year to date</span>
            <span class="kpi-value">{{ d.productionYearToDate | qty }}</span>
            <span class="kpi-sub">{{ d.productionUnit }}</span>
          </div>
        </div>

        <!-- Financial -->
        @if (auth.has('financial.view')) {
          <h2 style="margin:20px 0 10px">Financial position <span class="muted" style="font-weight:400">({{ d.currency }})</span></h2>
          <div class="kpi-grid">
            <div class="kpi clickable" (click)="go('/sales')">
              <span class="kpi-label">Gross revenue</span>
              <span class="kpi-value">{{ d.grossRevenue | money }}</span>
              <span class="kpi-sub">confirmed sales in period</span>
            </div>
            <div class="kpi clickable" (click)="go('/expenses')">
              <span class="kpi-label">Operating expenditure</span>
              <span class="kpi-value">{{ d.operatingExpenditure | money }}</span>
              <span class="kpi-sub">approved expenses</span>
            </div>
            <div class="kpi clickable" (click)="go('/expenses')">
              <span class="kpi-label">Capital expenditure</span>
              <span class="kpi-value">{{ d.capitalExpenditure | money }}</span>
              <span class="kpi-sub">capex categories</span>
            </div>
            <div class="kpi kpi-accent" [class.negative]="d.netOperatingResult < 0">
              <span class="kpi-label">Net operating result</span>
              <span class="kpi-value">{{ d.netOperatingResult | money }}</span>
              <span class="kpi-sub">revenue less all expenditure</span>
            </div>
          </div>

          <div class="kpi-grid" style="margin-top:12px">
            <div class="kpi clickable" (click)="go('/settlements')">
              <span class="kpi-label">SAIComex share</span>
              <span class="kpi-value">{{ d.saicomexShare | money }}</span>
              <span class="kpi-sub">from settled periods</span>
            </div>
            <div class="kpi clickable" (click)="go('/settlements')">
              <span class="kpi-label">Partner share</span>
              <span class="kpi-value">{{ d.partnerShare | money }}</span>
              <span class="kpi-sub">from settled periods</span>
            </div>
            <div class="kpi clickable" (click)="go('/settlements')">
              <span class="kpi-label">Outstanding to partners</span>
              <span class="kpi-value">{{ d.outstandingPartnerSettlements | money }}</span>
              <span class="kpi-sub">approved but unpaid</span>
            </div>
            <div class="kpi clickable" (click)="go('/expenses')">
              <span class="kpi-label">Pending approvals</span>
              <span class="kpi-value">{{ d.pendingApprovals }}</span>
              <span class="kpi-sub">expenses awaiting sign-off</span>
            </div>
          </div>
        }

        <!-- Drill level 2 -->
        <div class="card" style="margin-top:22px">
          <div class="card-header">
            <div>
              <div class="card-title">Project performance</div>
              <div class="card-sub">Click a project to see its shafts</div>
            </div>
            <a class="btn btn-secondary btn-sm" routerLink="/projects">All projects</a>
          </div>
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Project</th><th>Status</th><th class="num">Shafts</th>
                  <th class="num">Production</th>
                  @if (auth.has('financial.view')) {
                    <th class="num">Revenue</th><th class="num">Expenses</th><th class="num">Net</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (p of projects(); track p.projectId) {
                  <tr class="clickable" (click)="go('/projects/' + p.projectId)">
                    <td><strong>{{ p.projectName }}</strong>&nbsp;<span class="muted mono">{{ p.projectCode }}</span></td>
                    <td><span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span></td>
                    <td class="num">{{ p.activeShaftCount }} / {{ p.shaftCount }}</td>
                    <td class="num">{{ p.production | qty }} {{ p.productionUnit }}</td>
                    @if (auth.has('financial.view')) {
                      <td class="num">{{ p.revenue | money }}</td>
                      <td class="num">{{ p.expenses | money }}</td>
                      <td class="num" [class.neg]="p.netResult < 0"><strong>{{ p.netResult | money }}</strong></td>
                    }
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="7">No projects yet. Create one to get started.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </div>

        <!-- Drill level 3 — SRS §30 comparison -->
        <div class="card" style="margin-top:14px">
          <div class="card-header">
            <div>
              <div class="card-title">Shaft comparison</div>
              <div class="card-sub">Every shaft, side by side, for the selected period</div>
            </div>
            <a class="btn btn-secondary btn-sm" routerLink="/shafts">All shafts</a>
          </div>
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Shaft</th><th>Project</th><th>Partner</th><th>Status</th>
                  <th class="num">Production</th><th class="num">Target</th>
                  @if (auth.has('financial.view')) {
                    <th class="num">Revenue</th><th class="num">Expenses</th>
                    <th class="num">Net</th><th class="num">Cost / unit</th>
                  }
                  <th>Last production</th>
                </tr>
              </thead>
              <tbody>
                @for (s of shafts(); track s.shaftId) {
                  <tr class="clickable" (click)="go('/shafts/' + s.shaftId)">
                    <td><strong>{{ s.shaftName }}</strong></td>
                    <td class="muted">{{ s.projectName }}</td>
                    <td class="muted">{{ s.partnerName ?? '—' }}</td>
                    <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                    <td class="num">{{ s.production | qty }}</td>
                    <td class="num muted">{{ s.productionTarget ? (s.productionTarget | qty) : '—' }}</td>
                    @if (auth.has('financial.view')) {
                      <td class="num">{{ s.revenue | money }}</td>
                      <td class="num">{{ s.expenses | money }}</td>
                      <td class="num" [class.neg]="s.netResult < 0"><strong>{{ s.netResult | money }}</strong></td>
                      <td class="num muted">{{ s.costPerUnit ? (s.costPerUnit | money) : '—' }}</td>
                    }
                    <td class="muted nowrap">{{ s.lastProductionDate | shortDate }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="11">No shafts recorded yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .kpi.warn { border-left: 3px solid var(--amber); }
    .kpi.negative .kpi-value, .neg { color: var(--red); }
    .toolbar .input { min-width: 0; }
  `],
})
export class DashboardPage {

  private readonly api = inject(DashboardApi);
  private readonly router = inject(Router);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly data = signal<ExecutiveDashboard | null>(null);
  protected readonly projects = signal<ProjectPerformance[]>([]);
  protected readonly shafts = signal<ShaftPerformance[]>([]);

  protected readonly from = signal(startOfMonth());
  protected readonly to = signal(today());

  constructor() {
    this.reload();
  }

  protected setFrom(value: string): void { this.from.set(value); this.reload(); }
  protected setTo(value: string): void { this.to.set(value); this.reload(); }

  protected thisMonth(): void {
    this.from.set(startOfMonth());
    this.to.set(today());
    this.reload();
  }

  protected thisYear(): void {
    const now = new Date();
    this.from.set(iso(new Date(now.getFullYear(), 0, 1)));
    this.to.set(today());
    this.reload();
  }

  protected go(route: string): void {
    this.router.navigateByUrl(route);
  }

  private reload(): void {
    this.loading.set(true);
    const from = this.from();
    const to = this.to();

    this.api.executive(from, to).subscribe({
      next: d => { this.data.set(d); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.api.projects(from, to).subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.api.shafts(undefined, from, to).subscribe({ next: s => this.shafts.set(s), error: () => {} });
  }
}

function iso(date: Date): string {
  return date.toISOString().slice(0, 10);
}

function today(): string {
  return iso(new Date());
}

function startOfMonth(): string {
  const now = new Date();
  return iso(new Date(now.getFullYear(), now.getMonth(), 1));
}
