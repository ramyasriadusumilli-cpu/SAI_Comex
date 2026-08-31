import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { OperationApi, ProjectApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { OperationSummary, PageResponse, ProjectSummary } from '../../core/models/api.models';
import { ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

interface OperationFormModel {
  id: number | null;
  code: string;
  name: string;
  operationType: string;
  projectId: number | null;
  status: string;
  managerName: string;
  startDate: string;
}

function blankModel(): OperationFormModel {
  return { id: null, code: '', name: '', operationType: 'OPEN_PIT', projectId: null, status: 'PLANNING', managerName: '', startDate: '' };
}

/**
 * SRS §7 — mining operations.
 *
 * app.routes.ts defines no /operations/new or /operations/:id/edit route, so
 * create and edit both happen through an inline card toggled on this page
 * rather than a navigation.
 */
@Component({
  selector: 'app-operation-list',
  imports: [FormsModule, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Mining operations</h1>
          <div class="page-sub">Operations within each project</div>
        </div>
        @if (auth.has('operations.create')) {
          <button class="btn" (click)="startCreate()"> New operation
          </button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div class="card-title">{{ form.id ? 'Edit operation' : 'New operation' }}</div>
          </div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-code">Code</label>
              <input id="f-code" class="input" [(ngModel)]="form.code" [disabled]="!!form.id" required>
            </div>
            <div class="field">
              <label class="req" for="f-name">Name</label>
              <input id="f-name" class="input" [(ngModel)]="form.name" required>
            </div>
            <div class="field">
              <label class="req" for="f-project">Project</label>
              <select id="f-project" class="select" [(ngModel)]="form.projectId">
                <option [ngValue]="null">Select project…</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-type">Type</label>
              <select id="f-type" class="select" [(ngModel)]="form.operationType">
                @for (t of reference.data()?.operationTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-status">Status</label>
              <select id="f-status" class="select" [(ngModel)]="form.status">
                @for (s of reference.data()?.operationStatuses; track s) { <option [value]="s">{{ s }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-manager">Manager</label>
              <input id="f-manager" class="input" [(ngModel)]="form.managerName">
            </div>
            <div class="field">
              <label for="f-start">Start date</label>
              <input id="f-start" class="input" type="date" [(ngModel)]="form.startDate">
            </div>
          </div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
            </button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      <div class="toolbar">
        <input class="input" placeholder="Search code or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="projectId" (change)="reload()">
          <option [ngValue]="null">All projects</option>
          @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of reference.data()?.operationStatuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading operations…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Code</th><th>Name</th><th>Type</th><th>Project</th><th>Status</th>
                  <th class="num">Shafts</th><th>Manager</th><th>Start date</th>
                  @if (auth.has('operations.edit')) { <th></th> }
                </tr>
              </thead>
              <tbody>
                @for (o of page()?.content ?? []; track o.id) {
                  <tr>
                    <td class="mono">{{ o.code }}</td>
                    <td><strong>{{ o.name }}</strong></td>
                    <td class="muted">{{ o.operationType }}</td>
                    <td class="muted">{{ o.projectName ?? '—' }}</td>
                    <td><span [class]="o.status | statusClass">{{ o.status | statusLabel }}</span></td>
                    <td class="num">{{ o.activeShaftCount ?? 0 }} / {{ o.shaftCount ?? 0 }}</td>
                    <td class="muted">{{ o.managerName ?? '—' }}</td>
                    <td class="muted nowrap">{{ o.startDate | shortDate }}</td>
                    @if (auth.has('operations.edit')) {
                      <td><button class="btn btn-ghost btn-sm" (click)="startEdit(o)">Edit</button></td>
                    }
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No mining operations match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} operations</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class OperationListPage {

  private readonly api = inject(OperationApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<OperationSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly formOpen = signal(false);

  protected search = '';
  protected status = '';
  protected projectId: number | null = null;
  protected form: OperationFormModel = blankModel();

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
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
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.formOpen.set(true);
  }

  protected startEdit(o: OperationSummary): void {
    this.form = {
      id: o.id, code: o.code, name: o.name, operationType: o.operationType,
      projectId: o.projectId, status: o.status, managerName: o.managerName ?? '', startDate: o.startDate ?? '',
    };
    this.formOpen.set(true);
  }

  protected save(): void {
    if (!this.form.code || !this.form.name || !this.form.projectId) {
      this.toast.error('Code, name and project are required.');
      return;
    }
    this.saving.set(true);
    const request = this.form.id ? this.api.update(this.form.id, this.form) : this.api.create(this.form);
    request.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(this.form.id ? 'Operation updated' : 'Operation created');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
