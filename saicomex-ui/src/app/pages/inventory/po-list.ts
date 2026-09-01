import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import {
  InventoryApi, ProjectApi, PurchaseOrderApi, ReferenceService, ShaftApi, SupplierApi,
} from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  InventoryItem, PageResponse, PoDetail, PoSummary, ProjectSummary,
  ShaftSummary, StoreLocation, SupplierOption,
} from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

interface LineRow {
  itemId: number | null;
  description: string;
  quantity: number | null;
  unit: string;
  unitCost: number | null;
}

interface PoFormModel {
  supplierId: number | null;
  storeId: number | null;
  projectId: number | null;
  orderDate: string;
  currency: string;
  taxAmount: number | null;
  notes: string;
  lines: LineRow[];
}

function blankLine(): LineRow {
  return { itemId: null, description: '', quantity: null, unit: '', unitCost: null };
}

function blankModel(): PoFormModel {
  return { supplierId: null, storeId: null, projectId: null, orderDate: '', currency: '', taxAmount: null, notes: '', lines: [blankLine()] };
}

/** SRS §19 — purchase orders and goods receipt. */
@Component({
  selector: 'app-po-list',
  imports: [FormsModule, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Purchase Orders</h1>
          <div class="page-sub">Order goods and receive them into store</div>
        </div>
        @if (auth.has('inventory.create')) {
          <button class="btn" (click)="startCreate()">New purchase order</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">New purchase order</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req">Supplier</label>
              <select class="select" [(ngModel)]="form.supplierId">
                <option [ngValue]="null">Select supplier…</option>
                @for (s of suppliers(); track s.id) { <option [ngValue]="s.id">{{ s.code }} — {{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Deliver to store</label>
              <select class="select" [(ngModel)]="form.storeId">
                <option [ngValue]="null">Select store…</option>
                @for (s of stores(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Project</label>
              <select class="select" [(ngModel)]="form.projectId">
                <option [ngValue]="null">None</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Order date</label>
              <input class="input" type="date" [(ngModel)]="form.orderDate">
            </div>
            <div class="field">
              <label class="req">Currency</label>
              <select class="select" [(ngModel)]="form.currency">
                <option value="">Select…</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Tax amount</label>
              <input class="input" type="number" step="any" [(ngModel)]="form.taxAmount">
            </div>
          </div>

          <div class="card-title" style="margin:14px 0 8px">Lines</div>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Item</th><th>Description</th><th class="num">Qty</th><th>Unit</th><th class="num">Unit cost</th><th class="num">Line total</th><th></th></tr></thead>
              <tbody>
                @for (ln of form.lines; track $index) {
                  <tr>
                    <td>
                      <select class="select" [(ngModel)]="ln.itemId" (change)="onLineItem(ln)">
                        <option [ngValue]="null">— free text —</option>
                        @for (i of items(); track i.id) { <option [ngValue]="i.id">{{ i.code }} — {{ i.name }}</option> }
                      </select>
                    </td>
                    <td><input class="input" [(ngModel)]="ln.description"></td>
                    <td class="num"><input class="input" type="number" step="any" [(ngModel)]="ln.quantity" style="width:90px"></td>
                    <td><input class="input" [(ngModel)]="ln.unit" style="width:70px"></td>
                    <td class="num"><input class="input" type="number" step="any" [(ngModel)]="ln.unitCost" style="width:110px"></td>
                    <td class="num muted">{{ lineTotal(ln) | money: form.currency }}</td>
                    <td class="num"><button class="btn btn-secondary btn-sm" (click)="removeLine($index)" [disabled]="form.lines.length === 1">✕</button></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="row" style="margin-top:8px">
            <button class="btn btn-secondary btn-sm" (click)="addLine()">Add line</button>
            <span class="muted">Subtotal {{ subtotal() | money: form.currency }} · Total {{ total() | money: form.currency }}</span>
          </div>

          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Create order }
            </button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      @if (detail(); as d) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div class="card-title">{{ d.poNumber }} <span [class]="d.status | statusClass">{{ d.status | statusLabel }}</span></div>
            <button class="btn btn-secondary btn-sm" (click)="detail.set(null)">Close</button>
          </div>
          <div class="muted" style="margin-bottom:10px">
            Order date {{ d.orderDate | shortDate }} · Total {{ d.totalAmount | money: d.currency }}
            @if (d.approvedBy) { · approved by {{ d.approvedBy }} }
          </div>
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>#</th><th>Description</th><th class="num">Ordered</th><th class="num">Received</th><th class="num">Unit cost</th>
                @if (canReceive(d)) { <th class="num">Receive now</th> }
              </tr></thead>
              <tbody>
                @for (l of d.lines; track l.id) {
                  <tr>
                    <td class="muted">{{ l.lineNo }}</td>
                    <td>{{ l.description }}</td>
                    <td class="num">{{ l.quantity }}</td>
                    <td class="num">{{ l.receivedQuantity }}</td>
                    <td class="num muted">{{ l.unitCost | money: d.currency }}</td>
                    @if (canReceive(d)) {
                      <td class="num"><input class="input" type="number" step="any" min="0" [(ngModel)]="receiveQty[l.id]" style="width:100px"></td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <div class="row" style="margin-top:12px">
            @if (d.status === 'DRAFT' && auth.has('inventory.edit')) {
              <button class="btn btn-secondary btn-sm" (click)="act(d.id, 'submit')">Submit</button>
            }
            @if (d.status === 'SUBMITTED' && auth.has('inventory.edit')) {
              <button class="btn btn-sm" (click)="act(d.id, 'approve')">Approve</button>
            }
            @if (canReceive(d) && auth.has('inventory.create')) {
              <button class="btn btn-sm" [disabled]="saving()" (click)="doReceive(d)">Receive goods</button>
            }
            @if (d.status !== 'RECEIVED' && d.status !== 'CANCELLED' && auth.has('inventory.edit')) {
              <button class="btn btn-secondary btn-sm" (click)="act(d.id, 'cancel')">Cancel order</button>
            }
          </div>
        </div>
      }

      <div class="toolbar">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="SUBMITTED">Submitted</option>
          <option value="APPROVED">Approved</option>
          <option value="PARTIALLY_RECEIVED">Partially received</option>
          <option value="RECEIVED">Received</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading purchase orders…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>PO #</th><th>Date</th><th>Supplier</th><th class="num">Total</th><th>Status</th></tr></thead>
              <tbody>
                @for (p of page()?.content ?? []; track p.id) {
                  <tr style="cursor:pointer" (click)="open(p.id)">
                    <td class="mono">{{ p.poNumber }}</td>
                    <td class="nowrap">{{ p.orderDate | shortDate }}</td>
                    <td class="muted">{{ supplierName(p.supplierId) }}</td>
                    <td class="num">{{ p.totalAmount | money: p.currency }}</td>
                    <td><span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span></td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="5">No purchase orders match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (page(); as pg) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="pg.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ pg.page + 1 }} of {{ pg.totalPages || 1 }} · {{ pg.totalElements }} orders</span>
              <button class="btn btn-secondary btn-sm" [disabled]="pg.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class PurchaseOrderListPage {

  private readonly api = inject(PurchaseOrderApi);
  private readonly supplierApi = inject(SupplierApi);
  private readonly inventory = inject(InventoryApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<PoSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly detail = signal<PoDetail | null>(null);

  protected readonly suppliers = signal<SupplierOption[]>([]);
  protected readonly stores = signal<StoreLocation[]>([]);
  protected readonly items = signal<InventoryItem[]>([]);
  protected readonly projects = signal<ProjectSummary[]>([]);

  protected status = '';
  protected form: PoFormModel = blankModel();
  protected receiveQty: Record<number, number | null> = {};

  constructor() {
    this.reference.ensureLoaded();
    this.supplierApi.options().subscribe({ next: s => this.suppliers.set(s), error: () => {} });
    this.inventory.stores({ active: 'true' }).subscribe({ next: s => this.stores.set(s), error: () => {} });
    this.inventory.items({ active: 'true', size: 1000 }).subscribe({ next: p => this.items.set(p.content), error: () => {} });
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.reload();
  }

  protected supplierName(id: number): string {
    return this.suppliers().find(s => s.id === id)?.name ?? '#' + id;
  }

  protected canReceive(d: PoDetail): boolean {
    return d.status === 'APPROVED' || d.status === 'PARTIALLY_RECEIVED';
  }

  protected lineTotal(ln: LineRow): number {
    return (ln.quantity ?? 0) * (ln.unitCost ?? 0);
  }

  protected subtotal(): number {
    return this.form.lines.reduce((sum, ln) => sum + this.lineTotal(ln), 0);
  }

  protected total(): number {
    return this.subtotal() + (this.form.taxAmount ?? 0);
  }

  protected onLineItem(ln: LineRow): void {
    if (ln.itemId) {
      const it = this.items().find(i => i.id === ln.itemId);
      if (it) { ln.description = ln.description || it.name; ln.unit = ln.unit || it.unit; }
    }
  }

  protected addLine(): void { this.form.lines.push(blankLine()); }
  protected removeLine(i: number): void { this.form.lines.splice(i, 1); }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({ status: this.status || undefined, page: this.pageNo() }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.detail.set(null);
    this.formOpen.set(true);
  }

  protected open(id: number): void {
    this.receiveQty = {};
    this.api.get(id).subscribe({ next: d => this.detail.set(d), error: () => {} });
  }

  protected save(): void {
    const f = this.form;
    const lines = f.lines.filter(l => (l.description || l.itemId) && l.quantity && l.unitCost !== null);
    if (!f.supplierId || !f.storeId || !f.orderDate || !f.currency || lines.length === 0) {
      this.toast.error('Supplier, store, order date, currency and at least one complete line are required.');
      return;
    }
    this.saving.set(true);
    this.api.create({ ...f, lines }).subscribe({
      next: () => { this.saving.set(false); this.toast.success('Purchase order created'); this.formOpen.set(false); this.reload(); },
      error: () => this.saving.set(false),
    });
  }

  protected act(id: number, action: 'submit' | 'approve' | 'cancel'): void {
    const call = action === 'submit' ? this.api.submit(id)
      : action === 'approve' ? this.api.approve(id)
      : this.api.cancel(id);
    call.subscribe({
      next: d => { this.detail.set(d); this.toast.success('Order ' + d.status.toLowerCase()); this.reload(); },
      error: () => {},
    });
  }

  protected doReceive(d: PoDetail): void {
    const lines = d.lines
      .map(l => ({ lineId: l.id, quantity: this.receiveQty[l.id] }))
      .filter(x => x.quantity && x.quantity > 0);
    if (lines.length === 0) {
      this.toast.error('Enter a quantity to receive on at least one line.');
      return;
    }
    this.saving.set(true);
    this.api.receive(d.id, { lines }).subscribe({
      next: updated => {
        this.saving.set(false);
        this.toast.success('Goods received — order now ' + updated.status.toLowerCase());
        this.receiveQty = {};
        this.detail.set(updated);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
