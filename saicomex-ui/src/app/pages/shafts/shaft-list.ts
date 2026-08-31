import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { OperationApi, PartnerApi, ProjectApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { OperationSummary, PageResponse, PartnerSummary, ProjectSummary, ShaftSummary } from '../../core/models/api.models';
import { QuantityPipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/** SRS §8 — shafts: the paged list beneath a project's mining operations. */
@Component({
  selector: 'app-shaft-list',
  imports: [FormsModule, QuantityPipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Shafts</h1>
          <div class="page-sub">Every shaft across the group</div>
        </div>
        @if (auth.has('shafts.create')) {
          <button class="btn" (click)="router.navigateByUrl('/shafts/new')"> New shaft
          </button>
        }
      </div>

      <div class="toolbar">
        <input class="input" placeholder="Search code or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="projectId" (change)="onProjectChange()">
          <option [ngValue]="null">All projects</option>
          @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="operationId" (change)="reload()">
          <option [ngValue]="null">All operations</option>
          @for (o of operations(); track o.id) { <option [ngValue]="o.id">{{ o.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="partnerId" (change)="reload()">
          <option [ngValue]="null">All partners</option>
          @for (p of partners(); track p.id) { <option [ngValue]="p.id">{{ p.legalName }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of reference.data()?.shaftStatuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading shafts…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Code</th><th>Name</th><th>Project</th><th>Operation</th><th>Owner partner</th>
                  <th>Status</th><th class="num">Production target</th><th>Contract</th>
                </tr>
              </thead>
              <tbody>
                @for (s of page()?.content ?? []; track s.id) {
                  <tr class="clickable" (click)="router.navigateByUrl('/shafts/' + s.id)">
                    <td class="mono">{{ s.code }}</td>
                    <td><strong>{{ s.name }}</strong></td>
                    <td class="muted">{{ s.projectName ?? '—' }}</td>
                    <td class="muted">{{ s.operationName ?? '—' }}</td>
                    <td class="muted">{{ s.ownerPartnerName ?? '—' }}</td>
                    <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                    <td class="num">{{ s.productionTarget ? (s.productionTarget | qty: s.productionTargetUnit) : '—' }}</td>
                    <td>
                      @if (s.contractStatus) {
                        <span [class]="s.contractStatus | statusClass">{{ s.contractStatus | statusLabel }}</span>
                      } @else { <span class="muted">—</span> }
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="8">No shafts match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} shafts</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class ShaftListPage {

  private readonly api = inject(ShaftApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly operationApi = inject(OperationApi);
  private readonly partnerApi = inject(PartnerApi);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<ShaftSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly operations = signal<OperationSummary[]>([]);
  protected readonly partners = signal<PartnerSummary[]>([]);

  protected search = '';
  protected status = '';
  protected projectId: number | null = null;
  protected operationId: number | null = null;
  protected partnerId: number | null = null;

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.operationApi.options().subscribe({ next: o => this.operations.set(o), error: () => {} });
    this.partnerApi.options().subscribe({ next: p => this.partners.set(p), error: () => {} });
    this.reload();
  }

  protected onProjectChange(): void {
    this.operationId = null;
    this.operationApi.options(this.projectId ?? undefined).subscribe({ next: o => this.operations.set(o), error: () => {} });
    this.reload();
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
      projectId: this.projectId ?? undefined,
      miningOperationId: this.operationId ?? undefined,
      ownerPartnerId: this.partnerId ?? undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
