import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { ReferenceService, SupplierApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PageResponse, SupplierDetail } from '../../core/models/api.models';

const SUPPLIER_TYPES = ['FUEL', 'EXPLOSIVES', 'SPARES', 'SERVICES', 'GENERAL'];

interface SupplierFormModel {
  code: string;
  name: string;
  supplierType: string;
  contactPerson: string;
  phone: string;
  email: string;
  defaultCurrency: string;
  status: string;
  notes: string;
}

function blankModel(): SupplierFormModel {
  return { code: '', name: '', supplierType: 'GENERAL', contactPerson: '', phone: '', email: '', defaultCurrency: '', status: 'ACTIVE', notes: '' };
}

/** SRS §19 — supplier master data. */
@Component({
  selector: 'app-supplier-list',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Suppliers</h1>
          <div class="page-sub">Fuel, explosives, spares and services vendors</div>
        </div>
        @if (auth.has('suppliers.create')) {
          <button class="btn" (click)="startCreate()">New supplier</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">{{ editingId() ? 'Edit supplier' : 'New supplier' }}</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req">Code</label>
              <input class="input" [(ngModel)]="form.code">
            </div>
            <div class="field" style="grid-column: span 2">
              <label class="req">Name</label>
              <input class="input" [(ngModel)]="form.name">
            </div>
            <div class="field">
              <label>Type</label>
              <select class="select" [(ngModel)]="form.supplierType">
                @for (t of supplierTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Contact person</label>
              <input class="input" [(ngModel)]="form.contactPerson">
            </div>
            <div class="field">
              <label>Phone</label>
              <input class="input" [(ngModel)]="form.phone">
            </div>
            <div class="field">
              <label>Email</label>
              <input class="input" [(ngModel)]="form.email">
            </div>
            <div class="field">
              <label>Default currency</label>
              <select class="select" [(ngModel)]="form.defaultCurrency">
                <option value="">—</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Status</label>
              <select class="select" [(ngModel)]="form.status">
                <option value="ACTIVE">Active</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </div>
            <div class="field" style="grid-column: 1 / -1">
              <label>Notes</label>
              <textarea class="textarea" [(ngModel)]="form.notes"></textarea>
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
        <select class="select" [(ngModel)]="type" (change)="reload()">
          <option value="">All types</option>
          @for (t of supplierTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading suppliers…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Code</th><th>Name</th><th>Type</th><th>Contact</th><th>Status</th><th></th></tr></thead>
              <tbody>
                @for (s of page()?.content ?? []; track s.id) {
                  <tr>
                    <td class="mono">{{ s.code }}</td>
                    <td>{{ s.name }}</td>
                    <td class="muted">{{ s.supplierType ?? '—' }}</td>
                    <td class="muted">{{ s.contactPerson ?? '—' }}</td>
                    <td>{{ s.status }}</td>
                    <td class="num">
                      @if (auth.has('suppliers.edit')) {
                        <button class="btn btn-secondary btn-sm" (click)="startEdit(s)">Edit</button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="6">No suppliers yet.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>
    </div>
  `,
})
export class SupplierListPage {

  private readonly api = inject(SupplierApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly supplierTypes = SUPPLIER_TYPES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<SupplierDetail> | null>(null);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);

  protected search = '';
  protected type = '';

  protected form: SupplierFormModel = blankModel();

  constructor() {
    this.reference.ensureLoaded();
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({ search: this.search || undefined, type: this.type || undefined }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.editingId.set(null);
    this.formOpen.set(true);
  }

  protected startEdit(s: SupplierDetail): void {
    this.form = {
      code: s.code, name: s.name, supplierType: s.supplierType ?? 'GENERAL',
      contactPerson: s.contactPerson ?? '', phone: s.phone ?? '', email: s.email ?? '',
      defaultCurrency: s.defaultCurrency ?? '', status: s.status, notes: s.notes ?? '',
    };
    this.editingId.set(s.id);
    this.formOpen.set(true);
  }

  protected save(): void {
    if (!this.form.code || !this.form.name) {
      this.toast.error('Code and name are required.');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    const req = id ? this.api.update(id, this.form) : this.api.create(this.form);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(id ? 'Supplier updated' : 'Supplier created');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
