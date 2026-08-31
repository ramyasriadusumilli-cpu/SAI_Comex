import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ProjectApi, ReferenceService, ShaftApi, TransactionApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PageResponse, ProductionSummary, ProjectSummary, ShaftSummary } from '../../core/models/api.models';
import { QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

interface ProductionFormModel {
  projectId: number | null;
  shaftId: number | null;
  productionDate: string;
  shift: string;
  quantity: number | null;
  unitCode: string;
  oreTonnes: number | null;
  grade: number | null;
  targetQuantity: number | null;
  notes: string;
}

function blankModel(): ProductionFormModel {
  return {
    projectId: null, shaftId: null, productionDate: '', shift: '', quantity: null, unitCode: '',
    oreTonnes: null, grade: null, targetQuantity: null, notes: '',
  };
}

/** SRS §13 — daily production records. */
@Component({
  selector: 'app-production-list',
  imports: [FormsModule, QuantityPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Production</h1>
          <div class="page-sub">Daily production records across all shafts</div>
        </div>
        @if (auth.has('production.create')) {
          <button class="btn" (click)="startCreate()"> Record production
          </button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Record production</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-project">Project</label>
              <select id="f-project" class="select" [(ngModel)]="form.projectId" (change)="onFormProjectChange()">
                <option [ngValue]="null">Select project…</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="f-shaft">Shaft</label>
              <select id="f-shaft" class="select" [(ngModel)]="form.shaftId" [disabled]="!form.projectId">
                <option [ngValue]="null">Select shaft…</option>
                @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="f-date">Date</label>
              <input id="f-date" class="input" type="date" [(ngModel)]="form.productionDate">
            </div>
            <div class="field">
              <label for="f-shift">Shift</label>
              <input id="f-shift" class="input" [(ngModel)]="form.shift" placeholder="Day / Night">
            </div>
            <div class="field">
              <label class="req" for="f-qty">Quantity</label>
              <input id="f-qty" class="input" type="number" step="any" [(ngModel)]="form.quantity">
            </div>
            <div class="field">
              <label class="req" for="f-unit">Unit</label>
              <select id="f-unit" class="select" [(ngModel)]="form.unitCode">
                <option value="">Select unit…</option>
                @for (u of reference.data()?.productionUnits; track u.code) { <option [value]="u.code">{{ u.code }} — {{ u.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-ore">Ore tonnes</label>
              <input id="f-ore" class="input" type="number" step="any" [(ngModel)]="form.oreTonnes">
            </div>
            <div class="field">
              <label for="f-grade">Grade</label>
              <input id="f-grade" class="input" type="number" step="any" [(ngModel)]="form.grade">
            </div>
            <div class="field">
              <label for="f-target">Target quantity</label>
              <input id="f-target" class="input" type="number" step="any" [(ngModel)]="form.targetQuantity">
            </div>
            <div class="field" style="grid-column: 1 / -1">
              <label for="f-notes">Notes</label>
              <textarea id="f-notes" class="textarea" [(ngModel)]="form.notes"></textarea>
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
        <select class="select" [(ngModel)]="projectId" (change)="onFilterProjectChange()">
          <option value="">All projects</option>
          @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="shaftId" (change)="reload()">
          <option value="">All shafts</option>
          @for (s of filterShafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="SUBMITTED">Submitted</option>
          <option value="VERIFIED">Verified</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="CORRECTED">Corrected</option>
        </select>
        <input class="input" type="date" [(ngModel)]="from" (change)="reload()">
        <span class="muted">to</span>
        <input class="input" type="date" [(ngModel)]="to" (change)="reload()">
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading production…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Date</th><th>Project</th><th>Shaft</th><th>Shift</th>
                  <th class="num">Quantity</th><th class="num">Ore tonnes</th><th class="num">Grade</th>
                  <th class="num">Target</th><th class="num">Variance</th><th>Status</th><th>Recorded by</th>
                </tr>
              </thead>
              <tbody>
                @for (p of page()?.content ?? []; track p.id) {
                  <tr>
                    <td class="nowrap">{{ p.productionDate | shortDate }}</td>
                    <td class="muted">{{ p.projectName ?? '—' }}</td>
                    <td class="muted">{{ p.shaftName ?? '—' }}</td>
                    <td class="muted">{{ p.shift ?? '—' }}</td>
                    <td class="num">{{ p.quantity | qty: p.unitCode }}</td>
                    <td class="num muted">{{ p.oreTonnes != null ? (p.oreTonnes | qty) : '—' }}</td>
                    <td class="num muted">{{ p.grade != null ? (p.grade | qty) : '—' }}</td>
                    <td class="num muted">{{ p.targetQuantity != null ? (p.targetQuantity | qty) : '—' }}</td>
                    <td class="num" [class.neg]="(p.varianceQuantity ?? 0) < 0">{{ p.varianceQuantity != null ? (p.varianceQuantity | qty) : '—' }}</td>
                    <td><span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span></td>
                    <td class="muted">{{ p.recordedBy ?? '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="11">No production records match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} records</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .neg { color: var(--red); }
  `],
})
export class ProductionListPage {

  private readonly api = inject(TransactionApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<ProductionSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly filterShafts = signal<ShaftSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);

  protected projectId = '';
  protected shaftId = '';
  protected status = '';
  protected from = '';
  protected to = '';

  protected form: ProductionFormModel = blankModel();

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.filterShafts.set(s), error: () => {} });
    this.reload();
  }

  protected onFilterProjectChange(): void {
    this.shaftId = '';
    this.shaftApi.options(this.projectId ? { projectId: this.projectId } : undefined)
      .subscribe({ next: s => this.filterShafts.set(s), error: () => {} });
    this.reload();
  }

  protected onFormProjectChange(): void {
    this.form.shaftId = null;
    this.formShafts.set([]);
    if (this.form.projectId) {
      this.shaftApi.options({ projectId: this.form.projectId }).subscribe({ next: s => this.formShafts.set(s), error: () => {} });
    }
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.production({
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

  protected startCreate(): void {
    this.form = blankModel();
    this.formShafts.set([]);
    this.formOpen.set(true);
  }

  protected save(): void {
    if (!this.form.projectId || !this.form.shaftId || !this.form.productionDate || this.form.quantity === null || !this.form.unitCode) {
      this.toast.error('Project, shaft, date, quantity and unit are required.');
      return;
    }
    this.saving.set(true);
    this.api.createProduction(this.form).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Production recorded');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
