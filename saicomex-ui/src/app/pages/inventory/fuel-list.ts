import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { FuelApi, InventoryApi, ProjectApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  FuelTransaction, InventoryItem, PageResponse, ProjectSummary, ShaftSummary, StoreLocation,
} from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe } from '../../shared/format';

const FUEL_TYPES = ['DIESEL', 'PETROL', 'OIL'];

interface PurchaseForm {
  itemId: number | null;
  storeId: number | null;
  fuelType: string;
  quantityLitres: number | null;
  unitCost: number | null;
  currency: string;
  reference: string;
}

interface IssueForm {
  itemId: number | null;
  storeId: number | null;
  fuelType: string;
  quantityLitres: number | null;
  currency: string;
  projectId: number | null;
  shaftId: number | null;
  expenseCategoryId: number | null;
  recipientName: string;
  odometerReading: number | null;
  hourMeterReading: number | null;
  reference: string;
}

/** SRS §17 — fuel purchases and issues. An issue books stock, expense and fuel record at once. */
@Component({
  selector: 'app-fuel-list',
  imports: [FormsModule, MoneyPipe, ShortDatePipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Fuel</h1>
          <div class="page-sub">Purchases into store, and issues to shafts and equipment</div>
        </div>
        @if (auth.has('fuel.create')) {
          <div class="row">
            <button class="btn btn-secondary" (click)="openPurchase()">Record purchase</button>
            <button class="btn" (click)="openIssue()">Issue fuel</button>
          </div>
        }
      </div>

      @if (purchaseOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Record fuel purchase</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req">Fuel item</label>
              <select class="select" [(ngModel)]="purchase.itemId">
                <option [ngValue]="null">Select item…</option>
                @for (i of fuelItems(); track i.id) { <option [ngValue]="i.id">{{ i.code }} — {{ i.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Store</label>
              <select class="select" [(ngModel)]="purchase.storeId">
                <option [ngValue]="null">Select store…</option>
                @for (s of stores(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Fuel type</label>
              <select class="select" [(ngModel)]="purchase.fuelType">
                @for (t of fuelTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Litres</label>
              <input class="input" type="number" step="any" [(ngModel)]="purchase.quantityLitres">
            </div>
            <div class="field">
              <label class="req">Unit cost</label>
              <input class="input" type="number" step="any" [(ngModel)]="purchase.unitCost">
            </div>
            <div class="field">
              <label class="req">Currency</label>
              <select class="select" [(ngModel)]="purchase.currency">
                <option value="">Select…</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Reference</label>
              <input class="input" [(ngModel)]="purchase.reference">
            </div>
          </div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="savePurchase()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Save purchase }
            </button>
            <button class="btn btn-secondary" (click)="purchaseOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      @if (issueOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Issue fuel</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req">Fuel item</label>
              <select class="select" [(ngModel)]="issue.itemId">
                <option [ngValue]="null">Select item…</option>
                @for (i of fuelItems(); track i.id) { <option [ngValue]="i.id">{{ i.code }} — {{ i.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Store</label>
              <select class="select" [(ngModel)]="issue.storeId">
                <option [ngValue]="null">Select store…</option>
                @for (s of stores(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Fuel type</label>
              <select class="select" [(ngModel)]="issue.fuelType">
                @for (t of fuelTypes; track t) { <option [value]="t">{{ t }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Litres</label>
              <input class="input" type="number" step="any" [(ngModel)]="issue.quantityLitres">
            </div>
            <div class="field">
              <label class="req">Project</label>
              <select class="select" [(ngModel)]="issue.projectId" (change)="onProjectChange()">
                <option [ngValue]="null">Select project…</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Shaft</label>
              <select class="select" [(ngModel)]="issue.shaftId" [disabled]="!issue.projectId">
                <option [ngValue]="null">Select shaft…</option>
                @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req">Expense category</label>
              <select class="select" [(ngModel)]="issue.expenseCategoryId">
                <option [ngValue]="null">Select…</option>
                @for (c of reference.data()?.expenseCategories; track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label>Recipient</label>
              <input class="input" [(ngModel)]="issue.recipientName">
            </div>
            <div class="field">
              <label>Odometer</label>
              <input class="input" type="number" step="any" [(ngModel)]="issue.odometerReading">
            </div>
            <div class="field">
              <label>Hour meter</label>
              <input class="input" type="number" step="any" [(ngModel)]="issue.hourMeterReading">
            </div>
            <div class="field">
              <label>Reference</label>
              <input class="input" [(ngModel)]="issue.reference">
            </div>
          </div>
          <div class="muted" style="margin-top:8px">The issue is valued at the store's running average cost and booked as a direct expense on the shaft.</div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="saveIssue()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Issue fuel }
            </button>
            <button class="btn btn-secondary" (click)="issueOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      <div class="toolbar">
        <select class="select" [(ngModel)]="fuelType" (change)="reload()">
          <option value="">All fuel types</option>
          @for (t of fuelTypes; track t) { <option [value]="t">{{ t }}</option> }
        </select>
        <select class="select" [(ngModel)]="type" (change)="reload()">
          <option value="">All movements</option>
          <option value="PURCHASE">Purchase</option>
          <option value="ISSUE">Issue</option>
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading fuel movements…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr><th>Date</th><th>Type</th><th>Fuel</th><th class="num">Litres</th><th class="num">Cost</th><th class="num">Closing stock</th><th class="num">Odometer</th></tr>
              </thead>
              <tbody>
                @for (f of page()?.content ?? []; track f.id) {
                  <tr>
                    <td class="nowrap">{{ f.transactionDate | shortDate }}</td>
                    <td class="muted">{{ f.transactionType }}</td>
                    <td>{{ f.fuelType }}</td>
                    <td class="num">{{ f.quantityLitres }}</td>
                    <td class="num">{{ f.totalCost | money: f.currency }}</td>
                    <td class="num">{{ f.closingStock }}</td>
                    <td class="num muted">{{ f.odometerReading ?? '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="7">No fuel movements match these filters.</td></tr>
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
export class FuelListPage {

  private readonly fuel = inject(FuelApi);
  private readonly inventory = inject(InventoryApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly fuelTypes = FUEL_TYPES;

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<FuelTransaction> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly purchaseOpen = signal(false);
  protected readonly issueOpen = signal(false);

  protected readonly fuelItems = signal<InventoryItem[]>([]);
  protected readonly stores = signal<StoreLocation[]>([]);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);

  protected fuelType = '';
  protected type = '';

  protected purchase: PurchaseForm = this.blankPurchase();
  protected issue: IssueForm = this.blankIssue();

  constructor() {
    this.reference.ensureLoaded();
    this.inventory.items({ itemType: 'FUEL', active: 'true', size: 1000 })
      .subscribe({ next: p => this.fuelItems.set(p.content), error: () => {} });
    this.inventory.stores({ active: 'true' }).subscribe({ next: s => this.stores.set(s), error: () => {} });
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.reload();
  }

  private blankPurchase(): PurchaseForm {
    return { itemId: null, storeId: null, fuelType: 'DIESEL', quantityLitres: null, unitCost: null, currency: '', reference: '' };
  }

  private blankIssue(): IssueForm {
    return {
      itemId: null, storeId: null, fuelType: 'DIESEL', quantityLitres: null, currency: '',
      projectId: null, shaftId: null, expenseCategoryId: null, recipientName: '',
      odometerReading: null, hourMeterReading: null, reference: '',
    };
  }

  protected onProjectChange(): void {
    this.issue.shaftId = null;
    this.formShafts.set([]);
    if (this.issue.projectId) {
      this.shaftApi.options({ projectId: this.issue.projectId }).subscribe({ next: s => this.formShafts.set(s), error: () => {} });
    }
  }

  protected openPurchase(): void { this.purchase = this.blankPurchase(); this.issueOpen.set(false); this.purchaseOpen.set(true); }
  protected openIssue(): void { this.issue = this.blankIssue(); this.formShafts.set([]); this.purchaseOpen.set(false); this.issueOpen.set(true); }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.fuel.list({ fuelType: this.fuelType || undefined, type: this.type || undefined, page: this.pageNo() }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected savePurchase(): void {
    const p = this.purchase;
    if (!p.itemId || !p.storeId || !p.fuelType || p.quantityLitres === null || p.unitCost === null || !p.currency) {
      this.toast.error('Item, store, fuel type, litres, unit cost and currency are required.');
      return;
    }
    this.saving.set(true);
    this.fuel.purchase(p).subscribe({
      next: () => { this.saving.set(false); this.toast.success('Fuel purchase recorded'); this.purchaseOpen.set(false); this.reload(); },
      error: () => this.saving.set(false),
    });
  }

  protected saveIssue(): void {
    const i = this.issue;
    if (!i.itemId || !i.storeId || !i.fuelType || i.quantityLitres === null || !i.projectId || !i.shaftId || !i.expenseCategoryId) {
      this.toast.error('Item, store, fuel type, litres, project, shaft and expense category are required.');
      return;
    }
    this.saving.set(true);
    this.fuel.issue({ ...i, exchangeRate: 1 }).subscribe({
      next: () => { this.saving.set(false); this.toast.success('Fuel issued'); this.issueOpen.set(false); this.reload(); },
      error: () => this.saving.set(false),
    });
  }
}
