import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { InventoryApi, ProjectApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ProjectSummary, ShaftSummary, StoreLocation } from '../../core/models/api.models';

const STORE_TYPES = ['GENERAL', 'FUEL_BAY', 'MAGAZINE'];

interface StoreFormModel {
  code: string;
  name: string;
  storeType: string;
  projectId: number | null;
  shaftId: number | null;
  isActive: boolean;
}

function blankModel(): StoreFormModel {
  return { code: '', name: '', storeType: 'GENERAL', projectId: null, shaftId: null, isActive: true };
}

/** SRS §19 — stores: general stores, fuel bays and magazines. */
@Component({
  selector: 'app-store-list',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Stores</h1>
          <div class="page-sub">General stores, fuel bays and magazines</div>
        </div>
        @if (auth.has('inventory.create')) {
          <button class="btn" (click)="startCreate()">New store</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">{{ editingId() ? 'Edit store' : 'New store' }}</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="s-code">Code</label>
              <input id="s-code" class="input" [(ngModel)]="form.code">
            </div>
            <div class="field" style="grid-column: span 2">
              <label class="req" for="s-name">Name</label>
              <input id="s-name" class="input" [(ngModel)]="form.name">
            </div>
            <div class="field">
              <label class="req" for="s-type">Type</label>
              <select id="s-type" class="select" [(ngModel)]="form.storeType">
                @for (t of storeTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="s-project">Project</label>
              <select id="s-project" class="select" [(ngModel)]="form.projectId" (change)="onProjectChange()">
                <option [ngValue]="null">None</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="s-shaft">Shaft</label>
              <select id="s-shaft" class="select" [(ngModel)]="form.shaftId" [disabled]="!form.projectId">
                <option [ngValue]="null">None</option>
                @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="check"><input type="checkbox" [(ngModel)]="form.isActive"> Active</label>
            </div>
          </div>
          @if (form.storeType === 'MAGAZINE') {
            <div class="muted" style="margin-top:8px">A magazine is the only store type from which licence-controlled items (explosives) may be issued.</div>
          }
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
            </button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      <div class="toolbar">
        <select class="select" [(ngModel)]="storeType" (change)="reload()">
          <option value="">All types</option>
          @for (t of storeTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
        <select class="select" [(ngModel)]="active" (change)="reload()">
          <option value="">Active &amp; inactive</option>
          <option value="true">Active only</option>
          <option value="false">Inactive only</option>
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading stores…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Code</th><th>Name</th><th>Type</th><th>Active</th><th></th></tr></thead>
              <tbody>
                @for (s of stores(); track s.id) {
                  <tr>
                    <td class="mono">{{ s.code }}</td>
                    <td>{{ s.name }}</td>
                    <td class="muted">{{ s.storeType }}</td>
                    <td>{{ s.isActive ? 'Yes' : 'No' }}</td>
                    <td class="num">
                      @if (auth.has('inventory.edit')) {
                        <button class="btn btn-secondary btn-sm" (click)="startEdit(s)">Edit</button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="5">No stores yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
})
export class StoreListPage {

  private readonly api = inject(InventoryApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly storeTypes = STORE_TYPES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly stores = signal<StoreLocation[]>([]);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);

  protected storeType = '';
  protected active = '';

  protected form: StoreFormModel = blankModel();

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.reload();
  }

  protected onProjectChange(): void {
    this.form.shaftId = null;
    this.formShafts.set([]);
    if (this.form.projectId) {
      this.shaftApi.options({ projectId: this.form.projectId }).subscribe({ next: s => this.formShafts.set(s), error: () => {} });
    }
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.stores({ storeType: this.storeType || undefined, active: this.active || undefined }).subscribe({
      next: s => { this.stores.set(s); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.formShafts.set([]);
    this.editingId.set(null);
    this.formOpen.set(true);
  }

  protected startEdit(s: StoreLocation): void {
    this.form = {
      code: s.code, name: s.name, storeType: s.storeType,
      projectId: s.projectId ?? null, shaftId: s.shaftId ?? null, isActive: s.isActive,
    };
    this.editingId.set(s.id);
    this.formOpen.set(true);
    if (s.projectId) {
      this.shaftApi.options({ projectId: s.projectId }).subscribe({ next: sh => this.formShafts.set(sh), error: () => {} });
    }
  }

  protected save(): void {
    if (!this.form.code || !this.form.name || !this.form.storeType) {
      this.toast.error('Code, name and type are required.');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    const req = id ? this.api.updateStore(id, this.form) : this.api.createStore(this.form);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(id ? 'Store updated' : 'Store created');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
