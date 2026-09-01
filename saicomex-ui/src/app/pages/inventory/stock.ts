import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { InventoryApi, ProjectApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  InventoryItem, PageResponse, ProjectSummary, ShaftSummary,
  StockBalance, StockTransaction, StoreLocation,
} from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe } from '../../shared/format';

const MOVEMENT_TYPES = ['RECEIPT', 'ISSUE', 'RETURN', 'TRANSFER_OUT', 'TRANSFER_IN'];

interface MovementForm {
  itemId: number | null;
  storeId: number | null;
  transactionType: string;
  quantity: number | null;
  unitCost: number | null;
  currency: string;
  projectId: number | null;
  shaftId: number | null;
  transferStoreId: number | null;
  permitReference: string;
  recipientName: string;
  reason: string;
  reference: string;
}

function blankModel(): MovementForm {
  return {
    itemId: null, storeId: null, transactionType: 'RECEIPT', quantity: null, unitCost: null,
    currency: '', projectId: null, shaftId: null, transferStoreId: null,
    permitReference: '', recipientName: '', reason: '', reference: '',
  };
}

/** SRS §19 — stock movements and running balances. */
@Component({
  selector: 'app-stock',
  imports: [FormsModule, MoneyPipe, ShortDatePipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Stock</h1>
          <div class="page-sub">Movements and running balances (weighted-average cost)</div>
        </div>
        @if (auth.has('inventory.create')) {
          <button class="btn" (click)="startCreate()">Record movement</button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Record stock movement</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="m-type">Movement</label>
              <select id="m-type" class="select" [(ngModel)]="form.transactionType">
                @for (t of movementTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="m-item">Item</label>
              <select id="m-item" class="select" [(ngModel)]="form.itemId">
                <option [ngValue]="null">Select item…</option>
                @for (i of items(); track i.id) { <option [ngValue]="i.id">{{ i.code }} — {{ i.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="m-store">Store</label>
              <select id="m-store" class="select" [(ngModel)]="form.storeId">
                <option [ngValue]="null">Select store…</option>
                @for (s of stores(); track s.id) { <option [ngValue]="s.id">{{ s.name }} ({{ s.storeType }})</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="m-qty">Quantity</label>
              <input id="m-qty" class="input" type="number" step="any" [(ngModel)]="form.quantity">
            </div>
            @if (form.transactionType === 'RECEIPT' || form.transactionType === 'TRANSFER_IN') {
              <div class="field">
                <label for="m-cost">Unit cost</label>
                <input id="m-cost" class="input" type="number" step="any" [(ngModel)]="form.unitCost">
              </div>
              <div class="field">
                <label for="m-ccy">Currency</label>
                <select id="m-ccy" class="select" [(ngModel)]="form.currency">
                  <option value="">—</option>
                  @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
                </select>
              </div>
            }
            @if (form.transactionType === 'TRANSFER_OUT') {
              <div class="field">
                <label for="m-tstore">Transfer to store</label>
                <select id="m-tstore" class="select" [(ngModel)]="form.transferStoreId">
                  <option [ngValue]="null">Select store…</option>
                  @for (s of stores(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
                </select>
              </div>
            }
            @if (form.transactionType === 'ISSUE') {
              <div class="field">
                <label for="m-project">Project</label>
                <select id="m-project" class="select" [(ngModel)]="form.projectId" (change)="onProjectChange()">
                  <option [ngValue]="null">None</option>
                  @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="m-shaft">Shaft</label>
                <select id="m-shaft" class="select" [(ngModel)]="form.shaftId" [disabled]="!form.projectId">
                  <option [ngValue]="null">None</option>
                  @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="m-recipient">Recipient</label>
                <input id="m-recipient" class="input" [(ngModel)]="form.recipientName">
              </div>
              <div class="field">
                <label for="m-permit">Permit reference</label>
                <input id="m-permit" class="input" [(ngModel)]="form.permitReference" placeholder="Required for explosives">
              </div>
            }
            <div class="field" style="grid-column: span 2">
              <label class="req" for="m-reason">Reason</label>
              <input id="m-reason" class="input" [(ngModel)]="form.reason">
            </div>
            <div class="field">
              <label for="m-ref">Reference</label>
              <input id="m-ref" class="input" [(ngModel)]="form.reference">
            </div>
          </div>
          <div class="muted" style="margin-top:8px">Issuing a licence-controlled item needs a permit reference, a recipient, and a MAGAZINE store.</div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spin"></span> Posting… } @else { Post movement }
            </button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div class="card-title">Balances</div></div>
        <div class="toolbar">
          <select class="select" [(ngModel)]="balanceStoreId" (change)="loadBalances()">
            <option value="">Select a store…</option>
            @for (s of stores(); track s.id) { <option [value]="s.id">{{ s.name }} ({{ s.storeType }})</option> }
          </select>
        </div>
        @if (balanceStoreId) {
          <div class="table-wrap">
            <table class="data">
              <thead><tr><th>Item</th><th>Name</th><th class="num">On hand</th><th>Unit</th><th class="num">Avg cost</th><th class="num">Value</th></tr></thead>
              <tbody>
                @for (b of balances(); track b.itemId) {
                  <tr>
                    <td class="mono">{{ b.itemCode }}</td>
                    <td>{{ b.itemName }}</td>
                    <td class="num">{{ b.quantity }}</td>
                    <td class="muted">{{ b.unit }}</td>
                    <td class="num">{{ b.averageCost | money: b.costCurrency }}</td>
                    <td class="num">{{ (b.quantity * b.averageCost) | money: b.costCurrency }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="6">No stock in this store.</td></tr>
                }
              </tbody>
            </table>
          </div>
        }
      </div>

      <div class="card">
        <div class="card-header"><div class="card-title">Movements</div></div>
        <div class="toolbar">
          <select class="select" [(ngModel)]="filterStoreId" (change)="reload()">
            <option value="">All stores</option>
            @for (s of stores(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
          </select>
          <select class="select" [(ngModel)]="filterType" (change)="reload()">
            <option value="">All types</option>
            @for (t of movementTypes; track t) { <option [value]="t">{{ t }}</option> }
          </select>
        </div>
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading movements…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr><th>Txn #</th><th>Date</th><th>Item</th><th>Store</th><th>Type</th><th class="num">Qty</th><th class="num">Value</th><th class="num">Balance</th><th>Reason</th></tr>
              </thead>
              <tbody>
                @for (t of page()?.content ?? []; track t.id) {
                  <tr>
                    <td class="mono">{{ t.transactionNumber }}</td>
                    <td class="nowrap">{{ t.transactionDate | shortDate }}</td>
                    <td>{{ t.itemCode }}</td>
                    <td class="muted">{{ t.storeName }}</td>
                    <td class="muted">{{ t.transactionType }}</td>
                    <td class="num">{{ t.quantity }}</td>
                    <td class="num muted">{{ t.totalCost | money: t.currency }}</td>
                    <td class="num">{{ t.balanceAfter }}</td>
                    <td>{{ t.reason }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No movements match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>
          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} movements</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
})
export class StockPage {

  private readonly api = inject(InventoryApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly movementTypes = MOVEMENT_TYPES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<StockTransaction> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);

  protected readonly items = signal<InventoryItem[]>([]);
  protected readonly stores = signal<StoreLocation[]>([]);
  protected readonly balances = signal<StockBalance[]>([]);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);

  protected balanceStoreId = '';
  protected filterStoreId = '';
  protected filterType = '';

  protected form: MovementForm = blankModel();

  constructor() {
    this.reference.ensureLoaded();
    this.api.items({ active: 'true', size: 1000 }).subscribe({ next: p => this.items.set(p.content), error: () => {} });
    this.api.stores({ active: 'true' }).subscribe({ next: s => this.stores.set(s), error: () => {} });
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

  protected loadBalances(): void {
    if (!this.balanceStoreId) { this.balances.set([]); return; }
    this.api.balancesForStore(Number(this.balanceStoreId)).subscribe({ next: b => this.balances.set(b), error: () => {} });
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.transactions({
      storeId: this.filterStoreId || undefined,
      type: this.filterType || undefined,
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
    if (!this.form.itemId || !this.form.storeId || this.form.quantity === null || !this.form.reason) {
      this.toast.error('Item, store, quantity and reason are required.');
      return;
    }
    this.saving.set(true);
    this.api.postTransaction(this.form).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Movement posted');
        this.formOpen.set(false);
        this.reload();
        this.loadBalances();
      },
      error: () => this.saving.set(false),
    });
  }
}
