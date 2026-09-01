import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { EquipmentApi, MaintenanceApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  EquipmentSummary, MaintenanceDetail, MaintenanceSummary, PageResponse,
} from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

const TYPES = ['PREVENTIVE', 'CORRECTIVE', 'INSPECTION', 'OVERHAUL'];
const PRIORITIES = ['LOW', 'NORMAL', 'HIGH', 'URGENT'];
const NEXT_STATUS: Record<string, string[]> = {
  OPEN: ['IN_PROGRESS', 'AWAITING_PARTS', 'CANCELLED'],
  IN_PROGRESS: ['AWAITING_PARTS', 'COMPLETED', 'CANCELLED'],
  AWAITING_PARTS: ['IN_PROGRESS', 'COMPLETED', 'CANCELLED'],
};

interface PartRow { description: string; quantity: number | null; unitCost: number | null; }
interface JobForm {
  equipmentId: number | null;
  maintenanceType: string;
  priority: string;
  description: string;
  serviceDate: string;
  labourCost: number | null;
  otherCost: number | null;
  currency: string;
  downtimeHours: number | null;
  parts: PartRow[];
}

function blankPart(): PartRow { return { description: '', quantity: null, unitCost: null }; }
function blankForm(): JobForm {
  return { equipmentId: null, maintenanceType: 'CORRECTIVE', priority: 'NORMAL', description: '', serviceDate: '', labourCost: null, otherCost: null, currency: '', downtimeHours: null, parts: [blankPart()] };
}

/** SRS §22 — maintenance jobs, parts and cost rollup. */
@Component({
  selector: 'app-maintenance-list',
  imports: [FormsModule, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Maintenance</h1>
          <div class="page-sub">Service jobs, parts and downtime</div>
        </div>
        @if (auth.has('maintenance.create')) {
          <button class="btn" (click)="startCreate()">New job</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">New maintenance job</div></div>
          <div class="form-grid">
            <div class="field"><label class="req">Equipment</label>
              <select class="select" [(ngModel)]="form.equipmentId">
                <option [ngValue]="null">Select equipment…</option>
                @for (e of equipment(); track e.id) { <option [ngValue]="e.id">{{ e.assetNumber }} — {{ e.name }}</option> }
              </select>
            </div>
            <div class="field"><label class="req">Type</label>
              <select class="select" [(ngModel)]="form.maintenanceType">@for (t of types; track t) { <option [value]="t">{{ t }}</option> }</select>
            </div>
            <div class="field"><label>Priority</label>
              <select class="select" [(ngModel)]="form.priority">@for (p of priorities; track p) { <option [value]="p">{{ p }}</option> }</select>
            </div>
            <div class="field"><label>Service date</label><input class="input" type="date" [(ngModel)]="form.serviceDate"></div>
            <div class="field" style="grid-column: span 2"><label class="req">Description</label><input class="input" [(ngModel)]="form.description"></div>
            <div class="field"><label>Labour cost</label><input class="input" type="number" step="any" [(ngModel)]="form.labourCost"></div>
            <div class="field"><label>Other cost</label><input class="input" type="number" step="any" [(ngModel)]="form.otherCost"></div>
            <div class="field"><label>Currency</label>
              <select class="select" [(ngModel)]="form.currency">
                <option value="">—</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field"><label>Downtime (hrs)</label><input class="input" type="number" step="any" [(ngModel)]="form.downtimeHours"></div>
          </div>

          <div class="card-title" style="margin:14px 0 8px">Parts</div>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Description</th><th class="num">Qty</th><th class="num">Unit cost</th><th class="num">Line total</th><th></th></tr></thead>
              <tbody>
                @for (p of form.parts; track $index) {
                  <tr>
                    <td><input class="input" [(ngModel)]="p.description"></td>
                    <td class="num"><input class="input" type="number" step="any" [(ngModel)]="p.quantity" style="width:90px"></td>
                    <td class="num"><input class="input" type="number" step="any" [(ngModel)]="p.unitCost" style="width:110px"></td>
                    <td class="num muted">{{ partTotal(p) | money: form.currency }}</td>
                    <td class="num"><button class="btn btn-secondary btn-sm" (click)="removePart($index)" [disabled]="form.parts.length === 1">✕</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="row" style="margin-top:8px">
            <button class="btn btn-secondary btn-sm" (click)="addPart()">Add part</button>
            <span class="muted">Parts {{ partsCost() | money: form.currency }} · Total {{ total() | money: form.currency }}</span>
          </div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">@if (saving()) { <span class="spin"></span> Saving… } @else { Create job }</button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      @if (detail(); as d) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div class="card-title">{{ d.jobNumber }} <span [class]="d.status | statusClass">{{ d.status | statusLabel }}</span></div>
            <button class="btn btn-secondary btn-sm" (click)="detail.set(null)">Close</button>
          </div>
          <div class="muted" style="margin-bottom:10px">
            {{ d.maintenanceType }} · {{ d.priority }} · {{ d.description }}
            · parts {{ d.partsCost | money: d.currency }} + labour {{ d.labourCost | money: d.currency }} + other {{ d.otherCost | money: d.currency }} = <strong>{{ d.totalCost | money: d.currency }}</strong>
            @if (d.completedDate) { · completed {{ d.completedDate | shortDate }} }
          </div>
          @if (d.parts.length) {
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Part</th><th class="num">Qty</th><th class="num">Unit cost</th><th class="num">Total</th></tr></thead>
                <tbody>
                  @for (p of d.parts; track p.id) {
                    <tr><td>{{ p.description }}</td><td class="num">{{ p.quantity }}</td><td class="num">{{ p.unitCost | money: d.currency }}</td><td class="num">{{ p.totalCost | money: d.currency }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
          @if (auth.has('maintenance.edit') && nextStatuses(d.status).length) {
            <div class="row" style="margin-top:12px">
              <span class="muted">Move to:</span>
              @for (s of nextStatuses(d.status); track s) {
                <button class="btn btn-sm" [class.btn-secondary]="s !== 'COMPLETED'" (click)="setStatus(d.id, s)">{{ s | statusLabel }}</button>
              }
            </div>
          }
        </div>
      }

      <div class="toolbar">
        <select class="select" [(ngModel)]="type" (change)="reload()">
          <option value="">All types</option>
          @for (t of types; track t) { <option [value]="t">{{ t }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="OPEN">Open</option>
          <option value="IN_PROGRESS">In progress</option>
          <option value="AWAITING_PARTS">Awaiting parts</option>
          <option value="COMPLETED">Completed</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading jobs…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Job #</th><th>Type</th><th>Priority</th><th>Service date</th><th class="num">Total</th><th>Status</th></tr></thead>
              <tbody>
                @for (m of page()?.content ?? []; track m.id) {
                  <tr style="cursor:pointer" (click)="open(m.id)">
                    <td class="mono">{{ m.jobNumber }}</td>
                    <td class="muted">{{ m.maintenanceType }}</td>
                    <td class="muted">{{ m.priority }}</td>
                    <td class="nowrap">{{ m.serviceDate ? (m.serviceDate | shortDate) : '—' }}</td>
                    <td class="num">{{ m.totalCost | money }}</td>
                    <td><span [class]="m.status | statusClass">{{ m.status | statusLabel }}</span></td>
                  </tr>
                } @empty { <tr><td class="empty" colspan="6">No maintenance jobs match these filters.</td></tr> }
              </tbody>
            </table>
          </div>
          @if (page(); as pg) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="pg.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ pg.page + 1 }} of {{ pg.totalPages || 1 }} · {{ pg.totalElements }} jobs</span>
              <button class="btn btn-secondary btn-sm" [disabled]="pg.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class MaintenanceListPage {

  private readonly api = inject(MaintenanceApi);
  private readonly equipmentApi = inject(EquipmentApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly types = TYPES;
  protected readonly priorities = PRIORITIES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<MaintenanceSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly detail = signal<MaintenanceDetail | null>(null);
  protected readonly equipment = signal<EquipmentSummary[]>([]);

  protected type = '';
  protected status = '';
  protected form: JobForm = blankForm();

  constructor() {
    this.reference.ensureLoaded();
    this.equipmentApi.list({ size: 1000 }).subscribe({ next: p => this.equipment.set(p.content), error: () => {} });
    this.reload();
  }

  protected nextStatuses(status: string): string[] { return NEXT_STATUS[status] ?? []; }

  protected partTotal(p: PartRow): number { return (p.quantity ?? 0) * (p.unitCost ?? 0); }
  protected partsCost(): number { return this.form.parts.reduce((s, p) => s + this.partTotal(p), 0); }
  protected total(): number { return this.partsCost() + (this.form.labourCost ?? 0) + (this.form.otherCost ?? 0); }

  protected addPart(): void { this.form.parts.push(blankPart()); }
  protected removePart(i: number): void { this.form.parts.splice(i, 1); }

  protected setPage(page: number): void { this.pageNo.set(Math.max(0, page)); this.reload(); }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({ type: this.type || undefined, status: this.status || undefined, page: this.pageNo() })
      .subscribe({ next: p => { this.page.set(p); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  protected startCreate(): void { this.form = blankForm(); this.detail.set(null); this.formOpen.set(true); }

  protected open(id: number): void { this.api.get(id).subscribe({ next: d => this.detail.set(d), error: () => {} }); }

  protected save(): void {
    const f = this.form;
    const parts = f.parts.filter(p => p.description && p.quantity);
    if (!f.equipmentId || !f.maintenanceType || !f.description) {
      this.toast.error('Equipment, type and description are required.');
      return;
    }
    this.saving.set(true);
    this.api.create({ ...f, parts }).subscribe({
      next: () => { this.saving.set(false); this.toast.success('Maintenance job created'); this.formOpen.set(false); this.reload(); },
      error: () => this.saving.set(false),
    });
  }

  protected setStatus(id: number, status: string): void {
    this.api.setStatus(id, status).subscribe({
      next: d => { this.detail.set(d); this.toast.success('Job ' + d.status.toLowerCase().replace('_', ' ')); this.reload(); },
      error: () => {},
    });
  }
}
