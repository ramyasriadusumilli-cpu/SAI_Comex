import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { EquipmentApi, ProjectApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  AllocationDetail, EquipmentDetail, EquipmentSummary, PageResponse, ProjectSummary, ShaftSummary,
} from '../../core/models/api.models';
import { ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

const TYPES = ['EXCAVATOR', 'LOADER', 'TIPPER', 'COMPRESSOR', 'JACKHAMMER', 'WASH_PLANT', 'MILL', 'PUMP', 'GENERATOR', 'VEHICLE', 'OTHER'];
const STATUSES = ['ACTIVE', 'STANDBY', 'UNDER_MAINTENANCE', 'BREAKDOWN', 'DECOMMISSIONED', 'DISPOSED'];
const OWNERSHIP = ['OWNED', 'LEASED', 'PARTNER', 'HIRED'];

interface EquipForm {
  assetNumber: string;
  name: string;
  equipmentType: string;
  ownership: string;
  manufacturer: string;
  model: string;
  serialNumber: string;
  registrationNumber: string;
  purchaseDate: string;
  operatingHours: number | null;
  projectId: number | null;
  shaftId: number | null;
  status: string;
  notes: string;
}

function blankForm(): EquipForm {
  return {
    assetNumber: '', name: '', equipmentType: 'EXCAVATOR', ownership: 'OWNED', manufacturer: '', model: '',
    serialNumber: '', registrationNumber: '', purchaseDate: '', operatingHours: null,
    projectId: null, shaftId: null, status: 'ACTIVE', notes: '',
  };
}

interface AllocForm {
  projectId: number | null;
  shaftId: number | null;
  fromDate: string;
  openingHours: number | null;
  reason: string;
}

/** SRS §20-21 — asset register and allocation history. */
@Component({
  selector: 'app-equipment-list',
  imports: [FormsModule, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Equipment</h1>
          <div class="page-sub">Plant and vehicle asset register</div>
        </div>
        @if (auth.has('equipment.create')) {
          <button class="btn" (click)="startCreate()">New equipment</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">{{ editingId() ? 'Edit equipment' : 'New equipment' }}</div></div>
          <div class="form-grid">
            <div class="field"><label class="req">Asset number</label><input class="input" [(ngModel)]="form.assetNumber"></div>
            <div class="field" style="grid-column: span 2"><label class="req">Name</label><input class="input" [(ngModel)]="form.name"></div>
            <div class="field"><label class="req">Type</label>
              <select class="select" [(ngModel)]="form.equipmentType">@for (t of types; track t) { <option [value]="t">{{ t }}</option> }</select>
            </div>
            <div class="field"><label>Ownership</label>
              <select class="select" [(ngModel)]="form.ownership">@for (o of ownership; track o) { <option [value]="o">{{ o }}</option> }</select>
            </div>
            <div class="field"><label>Manufacturer</label><input class="input" [(ngModel)]="form.manufacturer"></div>
            <div class="field"><label>Model</label><input class="input" [(ngModel)]="form.model"></div>
            <div class="field"><label>Serial number</label><input class="input" [(ngModel)]="form.serialNumber"></div>
            <div class="field"><label>Registration</label><input class="input" [(ngModel)]="form.registrationNumber"></div>
            <div class="field"><label>Purchase date</label><input class="input" type="date" [(ngModel)]="form.purchaseDate"></div>
            <div class="field"><label>Operating hours</label><input class="input" type="number" step="any" [(ngModel)]="form.operatingHours"></div>
            <div class="field"><label>Initial project</label>
              <select class="select" [(ngModel)]="form.projectId" (change)="onFormProject()">
                <option [ngValue]="null">None</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field"><label>Initial shaft</label>
              <select class="select" [(ngModel)]="form.shaftId" [disabled]="!form.projectId">
                <option [ngValue]="null">None</option>
                @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field"><label>Status</label>
              <select class="select" [(ngModel)]="form.status">@for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }</select>
            </div>
            <div class="field" style="grid-column: 1 / -1"><label>Notes</label><textarea class="textarea" [(ngModel)]="form.notes"></textarea></div>
          </div>
          <div class="muted" style="margin-top:8px">Setting an initial project opens the first allocation, so the placement history is complete from day one.</div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">@if (saving()) { <span class="spin"></span> Saving… } @else { Save }</button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      @if (detail(); as d) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div class="card-title">{{ d.assetNumber }} — {{ d.name }} <span [class]="d.status | statusClass">{{ d.status | statusLabel }}</span></div>
            <button class="btn btn-secondary btn-sm" (click)="detail.set(null)">Close</button>
          </div>
          <div class="muted" style="margin-bottom:10px">
            {{ d.equipmentType }} · {{ d.ownership }} · {{ d.operatingHours }} hrs
            @if (d.shaftId) { · currently on shaft #{{ d.shaftId }} } @else { · unallocated }
          </div>

          @if (auth.has('equipment.edit')) {
            <div class="card-title" style="margin:6px 0 8px">Re-allocate</div>
            <div class="form-grid">
              <div class="field"><label class="req">Project</label>
                <select class="select" [(ngModel)]="alloc.projectId" (change)="onAllocProject()">
                  <option [ngValue]="null">Select project…</option>
                  @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                </select>
              </div>
              <div class="field"><label>Shaft</label>
                <select class="select" [(ngModel)]="alloc.shaftId" [disabled]="!alloc.projectId">
                  <option [ngValue]="null">None</option>
                  @for (s of allocShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
                </select>
              </div>
              <div class="field"><label class="req">From date</label><input class="input" type="date" [(ngModel)]="alloc.fromDate"></div>
              <div class="field"><label>Opening hours</label><input class="input" type="number" step="any" [(ngModel)]="alloc.openingHours"></div>
              <div class="field" style="grid-column: span 2"><label>Reason</label><input class="input" [(ngModel)]="alloc.reason"></div>
            </div>
            <div class="row" style="margin-top:10px">
              <button class="btn btn-sm" [disabled]="saving()" (click)="doAllocate(d)">Allocate</button>
            </div>
          }

          <div class="card-title" style="margin:16px 0 8px">Allocation history</div>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Project</th><th>Shaft</th><th>From</th><th>To</th><th class="num">Opening hrs</th><th class="num">Closing hrs</th><th>Reason</th></tr></thead>
              <tbody>
                @for (a of history(); track a.id) {
                  <tr>
                    <td class="muted">#{{ a.projectId }}</td>
                    <td class="muted">{{ a.shaftId ? '#' + a.shaftId : '—' }}</td>
                    <td class="nowrap">{{ a.fromDate | shortDate }}</td>
                    <td class="nowrap">{{ a.toDate ? (a.toDate | shortDate) : 'current' }}</td>
                    <td class="num">{{ a.openingHours ?? '—' }}</td>
                    <td class="num">{{ a.closingHours ?? '—' }}</td>
                    <td>{{ a.reason ?? '—' }}</td>
                  </tr>
                } @empty { <tr><td class="empty" colspan="7">No allocation history.</td></tr> }
              </tbody>
            </table>
          </div>
        </div>
      }

      <div class="toolbar">
        <input class="input" placeholder="Search asset # or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="type" (change)="reload()">
          <option value="">All types</option>
          @for (t of types; track t) { <option [value]="t">{{ t }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of statuses; track s) { <option [value]="s">{{ s }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading equipment…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Asset #</th><th>Name</th><th>Type</th><th class="num">Hours</th><th>Shaft</th><th>Status</th></tr></thead>
              <tbody>
                @for (e of page()?.content ?? []; track e.id) {
                  <tr style="cursor:pointer" (click)="open(e.id)">
                    <td class="mono">{{ e.assetNumber }}</td>
                    <td>{{ e.name }}</td>
                    <td class="muted">{{ e.equipmentType }}</td>
                    <td class="num">{{ e.operatingHours }}</td>
                    <td class="muted">{{ e.shaftId ? '#' + e.shaftId : '—' }}</td>
                    <td><span [class]="e.status | statusClass">{{ e.status | statusLabel }}</span></td>
                  </tr>
                } @empty { <tr><td class="empty" colspan="6">No equipment matches these filters.</td></tr> }
              </tbody>
            </table>
          </div>
          @if (page(); as pg) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="pg.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ pg.page + 1 }} of {{ pg.totalPages || 1 }} · {{ pg.totalElements }} items</span>
              <button class="btn btn-secondary btn-sm" [disabled]="pg.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class EquipmentListPage {

  private readonly api = inject(EquipmentApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly types = TYPES;
  protected readonly statuses = STATUSES;
  protected readonly ownership = OWNERSHIP;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<EquipmentSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);
  protected readonly detail = signal<EquipmentDetail | null>(null);
  protected readonly history = signal<AllocationDetail[]>([]);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);
  protected readonly allocShafts = signal<ShaftSummary[]>([]);

  protected search = '';
  protected type = '';
  protected status = '';

  protected form: EquipForm = blankForm();
  protected alloc: AllocForm = { projectId: null, shaftId: null, fromDate: '', openingHours: null, reason: '' };

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.reload();
  }

  protected onFormProject(): void {
    this.form.shaftId = null;
    this.formShafts.set([]);
    if (this.form.projectId) this.shaftApi.options({ projectId: this.form.projectId }).subscribe({ next: s => this.formShafts.set(s), error: () => {} });
  }

  protected onAllocProject(): void {
    this.alloc.shaftId = null;
    this.allocShafts.set([]);
    if (this.alloc.projectId) this.shaftApi.options({ projectId: this.alloc.projectId }).subscribe({ next: s => this.allocShafts.set(s), error: () => {} });
  }

  protected setPage(page: number): void { this.pageNo.set(Math.max(0, page)); this.reload(); }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({ type: this.type || undefined, status: this.status || undefined, search: this.search || undefined, page: this.pageNo() })
      .subscribe({ next: p => { this.page.set(p); this.loading.set(false); }, error: () => this.loading.set(false) });
  }

  protected startCreate(): void { this.form = blankForm(); this.formShafts.set([]); this.editingId.set(null); this.detail.set(null); this.formOpen.set(true); }

  protected open(id: number): void {
    this.api.get(id).subscribe({ next: d => this.detail.set(d), error: () => {} });
    this.api.allocations(id).subscribe({ next: h => this.history.set(h), error: () => {} });
    this.alloc = { projectId: null, shaftId: null, fromDate: '', openingHours: null, reason: '' };
  }

  protected save(): void {
    if (!this.form.assetNumber || !this.form.name || !this.form.equipmentType) {
      this.toast.error('Asset number, name and type are required.');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    const req = id ? this.api.update(id, this.form) : this.api.create(this.form);
    req.subscribe({
      next: () => { this.saving.set(false); this.toast.success(id ? 'Equipment updated' : 'Equipment registered'); this.formOpen.set(false); this.reload(); },
      error: () => this.saving.set(false),
    });
  }

  protected doAllocate(d: EquipmentDetail): void {
    if (!this.alloc.projectId || !this.alloc.fromDate) {
      this.toast.error('Project and from-date are required to allocate.');
      return;
    }
    this.saving.set(true);
    this.api.allocate(d.id, this.alloc).subscribe({
      next: updated => {
        this.saving.set(false);
        this.toast.success('Equipment re-allocated');
        this.detail.set(updated);
        this.api.allocations(d.id).subscribe({ next: h => this.history.set(h), error: () => {} });
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
