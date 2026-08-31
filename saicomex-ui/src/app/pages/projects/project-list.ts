import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ProjectApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { PageResponse, ProjectSummary } from '../../core/models/api.models';
import { MoneyPipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §6 — projects: the paged list at the top of the project/operation/shaft hierarchy. */
@Component({
  selector: 'app-project-list',
  imports: [FormsModule, MoneyPipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Projects</h1>
          <div class="page-sub">Mining projects across the group</div>
        </div>
        @if (auth.has('projects.create')) {
          <button class="btn" (click)="router.navigateByUrl('/projects/new')"> New project
          </button>
        }
      </div>

      <div class="toolbar">
        <input class="input" placeholder="Search code or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of reference.data()?.projectStatuses; track s) {
            <option [value]="s">{{ s | statusLabel }}</option>
          }
        </select>
        <select class="select" [(ngModel)]="type" (change)="reload()">
          <option value="">All types</option>
          @for (t of types(); track t) {
            <option [value]="t">{{ t }}</option>
          }
        </select>
        <div class="spacer"></div>
        <button class="btn btn-secondary btn-sm" (click)="reset()">Clear filters</button>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading projects…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Code</th><th>Name</th><th>Type</th><th>Status</th>
                  <th>Location</th><th>Manager</th>
                  <th class="num">Operations</th><th class="num">Shafts</th>
                  <th class="num">Budget</th>
                </tr>
              </thead>
              <tbody>
                @for (p of page()?.content ?? []; track p.id) {
                  <tr class="clickable" (click)="router.navigateByUrl('/projects/' + p.id)">
                    <td class="mono">{{ p.code }}</td>
                    <td><strong>{{ p.name }}</strong></td>
                    <td class="muted">{{ p.projectType ?? '—' }}</td>
                    <td><span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span></td>
                    <td class="muted">{{ p.locationName ?? '—' }}</td>
                    <td class="muted">{{ p.projectManagerName ?? '—' }}</td>
                    <td class="num">{{ p.operationCount }}</td>
                    <td class="num">{{ p.activeShaftCount }} / {{ p.shaftCount }}</td>
                    <td class="num">{{ p.budgetAmount ? (p.budgetAmount | money: p.budgetCurrency) : '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No projects match these filters. Try clearing them, or create a new project.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} projects</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class ProjectListPage {

  private readonly api = inject(ProjectApi);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<ProjectSummary> | null>(null);
  protected readonly pageNo = signal(0);

  protected search = '';
  protected status = '';
  protected type = '';

  protected readonly types = signal<string[]>(['OPEN_PIT', 'UNDERGROUND', 'ALLUVIAL', 'MIXED']);

  constructor() {
    this.reload();
  }

  protected reset(): void {
    this.search = '';
    this.status = '';
    this.type = '';
    this.setPage(0);
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({
      search: this.search || undefined,
      status: this.status || undefined,
      projectType: this.type || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
