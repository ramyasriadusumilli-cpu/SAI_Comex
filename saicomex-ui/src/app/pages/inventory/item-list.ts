import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { InventoryApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { InventoryItem, PageResponse } from '../../core/models/api.models';

const ITEM_TYPES = ['FUEL', 'EXPLOSIVE', 'CONSUMABLE', 'SPARE', 'PPE', 'CHEMICAL', 'OTHER'];
const UNITS = ['litre', 'kg', 'each', 'box'];

interface ItemFormModel {
  code: string;
  name: string;
  itemType: string;
  categoryId: number | null;
  unit: string;
  isControlled: boolean;
  requiresPermit: boolean;
  minimumStock: number | null;
  reorderLevel: number | null;
  standardCost: number | null;
  costCurrency: string;
  isActive: boolean;
  notes: string;
}

function blankModel(): ItemFormModel {
  return {
    code: '', name: '', itemType: 'CONSUMABLE', categoryId: null, unit: 'each',
    isControlled: false, requiresPermit: false, minimumStock: null, reorderLevel: null,
    standardCost: null, costCurrency: '', isActive: true, notes: '',
  };
}

/** SRS §18 — inventory item master (fuel, explosives, consumables, spares, PPE). */
@Component({
  selector: 'app-inventory-item-list',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Inventory Items</h1>
          <div class="page-sub">Fuel, explosives, consumables, spares and PPE</div>
        </div>
        @if (auth.has('inventory.create')) {
          <button class="btn" (click)="startCreate()">New item</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">{{ editingId() ? 'Edit item' : 'New item' }}</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-code">Code</label>
              <input id="f-code" class="input" [(ngModel)]="form.code">
            </div>
            <div class="field" style="grid-column: span 2">
              <label class="req" for="f-name">Name</label>
              <input id="f-name" class="input" [(ngModel)]="form.name">
            </div>
            <div class="field">
              <label class="req" for="f-type">Type</label>
              <select id="f-type" class="select" [(ngModel)]="form.itemType" (change)="onTypeChange()">
                @for (t of itemTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="f-unit">Unit</label>
              <select id="f-unit" class="select" [(ngModel)]="form.unit">
                @for (u of units; track u) { <option [value]="u">{{ u }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-cat">Expense category</label>
              <select id="f-cat" class="select" [(ngModel)]="form.categoryId">
                <option [ngValue]="null">None</option>
                @for (c of reference.data()?.expenseCategories; track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-min">Minimum stock</label>
              <input id="f-min" class="input" type="number" step="any" [(ngModel)]="form.minimumStock">
            </div>
            <div class="field">
              <label for="f-reorder">Reorder level</label>
              <input id="f-reorder" class="input" type="number" step="any" [(ngModel)]="form.reorderLevel">
            </div>
            <div class="field">
              <label for="f-cost">Standard cost</label>
              <input id="f-cost" class="input" type="number" step="any" [(ngModel)]="form.standardCost">
            </div>
            <div class="field">
              <label for="f-ccy">Cost currency</label>
              <select id="f-ccy" class="select" [(ngModel)]="form.costCurrency">
                <option value="">—</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="check"><input type="checkbox" [(ngModel)]="form.isControlled"> Licence-controlled</label>
              <label class="check"><input type="checkbox" [(ngModel)]="form.requiresPermit"> Requires permit to issue</label>
            </div>
            <div class="field">
              <label class="check"><input type="checkbox" [(ngModel)]="form.isActive"> Active</label>
            </div>
            <div class="field" style="grid-column: 1 / -1">
              <label for="f-notes">Notes</label>
              <textarea id="f-notes" class="textarea" [(ngModel)]="form.notes"></textarea>
            </div>
          </div>
          @if (form.itemType === 'EXPLOSIVE' || form.isControlled) {
            <div class="muted" style="margin-top:8px">Controlled items can only be issued from a MAGAZINE store, against a permit reference and a named recipient.</div>
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
        <input class="input" placeholder="Search code or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="itemType" (change)="reload()">
          <option value="">All types</option>
          @for (t of itemTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
        <select class="select" [(ngModel)]="active" (change)="reload()">
          <option value="">Active &amp; inactive</option>
          <option value="true">Active only</option>
          <option value="false">Inactive only</option>
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading items…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr><th>Code</th><th>Name</th><th>Type</th><th>Unit</th><th>Controlled</th><th>Active</th><th></th></tr>
              </thead>
              <tbody>
                @for (i of page()?.content ?? []; track i.id) {
                  <tr>
                    <td class="mono">{{ i.code }}</td>
                    <td>{{ i.name }}</td>
                    <td class="muted">{{ i.itemType }}</td>
                    <td class="muted">{{ i.unit }}</td>
                    <td>{{ i.isControlled ? 'Yes' : '—' }}</td>
                    <td>{{ i.isActive ? 'Yes' : 'No' }}</td>
                    <td class="num">
                      @if (auth.has('inventory.edit')) {
                        <button class="btn btn-secondary btn-sm" (click)="startEdit(i)">Edit</button>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="7">No items match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} items</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class InventoryItemListPage {

  private readonly api = inject(InventoryApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly itemTypes = ITEM_TYPES;
  protected readonly units = UNITS;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<InventoryItem> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly editingId = signal<number | null>(null);

  protected search = '';
  protected itemType = '';
  protected active = '';

  protected form: ItemFormModel = blankModel();

  constructor() {
    this.reference.ensureLoaded();
    this.reload();
  }

  protected onTypeChange(): void {
    if (this.form.itemType === 'EXPLOSIVE') {
      this.form.isControlled = true;
      this.form.requiresPermit = true;
    }
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.items({
      itemType: this.itemType || undefined,
      active: this.active || undefined,
      search: this.search || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.editingId.set(null);
    this.formOpen.set(true);
  }

  protected startEdit(i: InventoryItem): void {
    this.form = {
      code: i.code, name: i.name, itemType: i.itemType, categoryId: i.categoryId ?? null, unit: i.unit,
      isControlled: i.isControlled, requiresPermit: i.requiresPermit,
      minimumStock: i.minimumStock ?? null, reorderLevel: i.reorderLevel ?? null,
      standardCost: i.standardCost ?? null, costCurrency: i.costCurrency ?? '',
      isActive: i.isActive, notes: i.notes ?? '',
    };
    this.editingId.set(i.id);
    this.formOpen.set(true);
  }

  protected save(): void {
    if (!this.form.code || !this.form.name || !this.form.itemType || !this.form.unit) {
      this.toast.error('Code, name, type and unit are required.');
      return;
    }
    this.saving.set(true);
    const id = this.editingId();
    const req = id ? this.api.updateItem(id, this.form) : this.api.createItem(this.form);
    req.subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success(id ? 'Item updated' : 'Item created');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
