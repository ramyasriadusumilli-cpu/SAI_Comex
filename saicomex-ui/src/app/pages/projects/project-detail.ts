import { Component, OnInit, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { OperationApi, ProjectApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { OperationSummary, ProjectDetail, ShaftSummary } from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §6 — a single project: its record, its operations and its shafts. */
@Component({
  selector: 'app-project-detail',
  imports: [RouterLink, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading project…</div>
      } @else if (project(); as p) {

        <div class="crumbs">
          <a routerLink="/projects">Projects</a><span class="sep">›</span><span>{{ p.name }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ p.name }} <span class="muted mono" style="font-weight:400; font-size:14px">{{ p.code }}</span></h1>
            <div class="row" style="gap:8px">
              <span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span>
              <span class="page-sub">{{ p.locationName ?? 'No location set' }}</span>
            </div>
          </div>
          <div class="row">
            @if (auth.has('projects.edit')) {
              <button class="btn btn-secondary" (click)="router.navigateByUrl('/projects/' + p.id + '/edit')">Edit</button>
            }
            @if (auth.has('projects.edit')) {
              <button class="btn btn-danger" (click)="archive(p)">Archive</button>
            }
          </div>
        </div>

        <div class="kpi-grid" style="margin-bottom:16px">
          <div class="kpi">
            <span class="kpi-label">Mining operations</span>
            <span class="kpi-value">{{ p.operationCount }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">Shafts</span>
            <span class="kpi-value">{{ p.shaftCount }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">Active shafts</span>
            <span class="kpi-value">{{ p.activeShaftCount }}</span>
          </div>
          <div class="kpi">
            <span class="kpi-label">Documents</span>
            <span class="kpi-value">{{ p.documentCount ?? 0 }}</span>
          </div>
        </div>

        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Project record</div></div>
          <dl class="dl">
            <div><dt>Type</dt><dd>{{ p.projectType ?? '—' }}</dd></div>
            <div><dt>Manager</dt><dd>{{ p.projectManagerName ?? '—' }}</dd></div>
            <div><dt>Location</dt><dd>{{ p.locationName ?? '—' }}</dd></div>
            <div><dt>GPS</dt><dd>{{ p.latitude && p.longitude ? (p.latitude + ', ' + p.longitude) : '—' }}</dd></div>
            <div><dt>Start date</dt><dd>{{ p.startDate | shortDate }}</dd></div>
            <div><dt>Planned completion</dt><dd>{{ p.plannedCompletionDate | shortDate }}</dd></div>
            <div><dt>Actual completion</dt><dd>{{ p.actualCompletionDate | shortDate }}</dd></div>
            <div><dt>Budget</dt><dd>{{ p.budgetAmount ? (p.budgetAmount | money: p.budgetCurrency) : '—' }}</dd></div>
            <div><dt>Licence number</dt><dd>{{ p.licenceNumber ?? '—' }}</dd></div>
            <div><dt>Licence expiry</dt><dd>{{ p.licenceExpiryDate | shortDate }}</dd></div>
            <div><dt>Permit number</dt><dd>{{ p.permitNumber ?? '—' }}</dd></div>
            <div><dt>Permit expiry</dt><dd>{{ p.permitExpiryDate | shortDate }}</dd></div>
            <div style="grid-column: 1 / -1"><dt>Description</dt><dd>{{ p.description ?? '—' }}</dd></div>
            <div style="grid-column: 1 / -1"><dt>Notes</dt><dd>{{ p.notes ?? '—' }}</dd></div>
          </dl>
        </div>

        <div class="stack">
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Mining operations</div>
                <div class="card-sub">Operations under this project</div>
              </div>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Code</th><th>Name</th><th>Type</th><th>Status</th><th>Manager</th><th class="num">Shafts</th></tr></thead>
                <tbody>
                  @for (o of operations(); track o.id) {
                    <tr class="clickable" (click)="router.navigateByUrl('/operations')">
                      <td class="mono">{{ o.code }}</td>
                      <td><strong>{{ o.name }}</strong></td>
                      <td class="muted">{{ o.operationType }}</td>
                      <td><span [class]="o.status | statusClass">{{ o.status | statusLabel }}</span></td>
                      <td class="muted">{{ o.managerName ?? '—' }}</td>
                      <td class="num">{{ o.activeShaftCount ?? 0 }} / {{ o.shaftCount ?? 0 }}</td>
                    </tr>
                  } @empty {
                    <tr><td class="empty" colspan="6">No mining operations recorded under this project yet.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Shafts</div>
                <div class="card-sub">Shafts under this project</div>
              </div>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Code</th><th>Name</th><th>Operation</th><th>Owner partner</th><th>Status</th><th class="num">Production target</th></tr></thead>
                <tbody>
                  @for (s of shafts(); track s.id) {
                    <tr class="clickable" (click)="router.navigateByUrl('/shafts/' + s.id)">
                      <td class="mono">{{ s.code }}</td>
                      <td><strong>{{ s.name }}</strong></td>
                      <td class="muted">{{ s.operationName ?? '—' }}</td>
                      <td class="muted">{{ s.ownerPartnerName ?? '—' }}</td>
                      <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                      <td class="num">{{ s.productionTarget ? (s.productionTarget + ' ' + (s.productionTargetUnit ?? '')) : '—' }}</td>
                    </tr>
                  } @empty {
                    <tr><td class="empty" colspan="6">No shafts recorded under this project yet.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [],
})
export class ProjectDetailPage implements OnInit {

  readonly id = input.required<string>();

  private readonly api = inject(ProjectApi);
  private readonly operationApi = inject(OperationApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly project = signal<ProjectDetail | null>(null);
  protected readonly operations = signal<OperationSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);

  ngOnInit(): void {
    this.reload();
  }

  protected archive(p: ProjectDetail): void {
    const reason = prompt('Reason for archiving this project:');
    if (!reason) return;
    this.api.remove(p.id, reason).subscribe({
      next: () => {
        this.toast.success('Project archived');
        this.router.navigateByUrl('/projects');
      },
    });
  }

  private reload(): void {
    this.loading.set(true);
    const projectId = Number(this.id());
    this.api.get(projectId).subscribe({
      next: p => { this.project.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.operationApi.options(projectId).subscribe({ next: ops => this.operations.set(ops), error: () => {} });
    this.shaftApi.options({ projectId }).subscribe({ next: s => this.shafts.set(s), error: () => {} });
  }
}
