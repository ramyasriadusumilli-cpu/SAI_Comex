import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';

import { ProjectApi, ReferenceService, ShaftApi, TransactionApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { ExpenseSummary, PageResponse, ProjectSummary, ShaftSummary } from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

interface ExpenseFormModel {
  projectId: number | null;
  shaftId: number | null;
  categoryId: number | null;
  expenseDate: string;
  description: string;
  amount: number | null;
  currency: string;
  quantity: number | null;
  unit: string;
  unitCost: number | null;
  reference: string;
  invoiceNumber: string;
  notes: string;
}

function blankModel(): ExpenseFormModel {
  return {
    projectId: null, shaftId: null, categoryId: null, expenseDate: '', description: '',
    amount: null, currency: '', quantity: null, unit: '', unitCost: null,
    reference: '', invoiceNumber: '', notes: '',
  };
}

/** SRS §15 — operating and capital expenses. */
@Component({
  selector: 'app-expense-list',
  imports: [FormsModule, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Expenses</h1>
          <div class="page-sub">Operating and capital expenditure across all shafts</div>
        </div>
        @if (auth.has('expenses.create')) {
          <button class="btn" (click)="startCreate()"> Record expense
          </button>
        }
      </div>

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Record expense</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-project">Project</label>
              <select id="f-project" class="select" [(ngModel)]="form.projectId" (change)="onFormProjectChange()">
                <option [ngValue]="null">Select project…</option>
                @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-shaft">Shaft</label>
              <select id="f-shaft" class="select" [(ngModel)]="form.shaftId" [disabled]="!form.projectId">
                <option [ngValue]="null">None</option>
                @for (s of formShafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="f-category">Category</label>
              <select id="f-category" class="select" [(ngModel)]="form.categoryId">
                <option [ngValue]="null">Select category…</option>
                @for (c of reference.data()?.expenseCategories; track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label class="req" for="f-date">Date</label>
              <input id="f-date" class="input" type="date" [(ngModel)]="form.expenseDate">
            </div>
            <div class="field" style="grid-column: span 2">
              <label class="req" for="f-desc">Description</label>
              <input id="f-desc" class="input" [(ngModel)]="form.description">
            </div>
            <div class="field">
              <label class="req" for="f-amount">Amount</label>
              <input id="f-amount" class="input" type="number" step="any" [(ngModel)]="form.amount">
            </div>
            <div class="field">
              <label class="req" for="f-currency">Currency</label>
              <select id="f-currency" class="select" [(ngModel)]="form.currency">
                <option value="">Select currency…</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }} — {{ c.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-qty">Quantity</label>
              <input id="f-qty" class="input" type="number" step="any" [(ngModel)]="form.quantity">
            </div>
            <div class="field">
              <label for="f-unit">Unit</label>
              <input id="f-unit" class="input" [(ngModel)]="form.unit">
            </div>
            <div class="field">
              <label for="f-unitcost">Unit cost</label>
              <input id="f-unitcost" class="input" type="number" step="any" [(ngModel)]="form.unitCost">
            </div>
            <div class="field">
              <label for="f-reference">Reference</label>
              <input id="f-reference" class="input" [(ngModel)]="form.reference">
            </div>
            <div class="field">
              <label for="f-invoice">Invoice number</label>
              <input id="f-invoice" class="input" [(ngModel)]="form.invoiceNumber">
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
        <input class="input" placeholder="Search description…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="projectId" (change)="onFilterProjectChange()">
          <option value="">All projects</option>
          @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="shaftId" (change)="reload()">
          <option value="">All shafts</option>
          @for (s of filterShafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="categoryId" (change)="reload()">
          <option value="">All categories</option>
          @for (c of reference.data()?.expenseCategories; track c.id) { <option [value]="c.id">{{ c.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="DRAFT">Draft</option>
          <option value="SUBMITTED">Submitted</option>
          <option value="PENDING_APPROVAL">Pending approval</option>
          <option value="APPROVED">Approved</option>
          <option value="REJECTED">Rejected</option>
          <option value="PAID">Paid</option>
          <option value="CANCELLED">Cancelled</option>
        </select>
        <input class="input" type="date" [(ngModel)]="from" (change)="reload()">
        <span class="muted">to</span>
        <input class="input" type="date" [(ngModel)]="to" (change)="reload()">
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading expenses…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Expense #</th><th>Date</th><th>Project</th><th>Shaft</th><th>Category</th>
                  <th>Description</th><th class="num">Amount</th><th class="num">Base amount</th><th>Status</th>
                </tr>
              </thead>
              <tbody>
                @for (e of page()?.content ?? []; track e.id) {
                  <tr>
                    <td class="mono">{{ e.expenseNumber }}</td>
                    <td class="nowrap">{{ e.expenseDate | shortDate }}</td>
                    <td class="muted">{{ e.projectName ?? '—' }}</td>
                    <td class="muted">{{ e.shaftName ?? '—' }}</td>
                    <td class="muted">{{ e.categoryName ?? '—' }}</td>
                    <td>{{ e.description }}</td>
                    <td class="num">{{ e.amount | money: e.currency }}</td>
                    <td class="num muted">{{ e.baseAmount | money }}</td>
                    <td><span [class]="e.status | statusClass">{{ e.status | statusLabel }}</span></td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No expenses match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} expenses</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class ExpenseListPage {

  private readonly api = inject(TransactionApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  private readonly route = inject(ActivatedRoute);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly page = signal<PageResponse<ExpenseSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly filterShafts = signal<ShaftSummary[]>([]);
  protected readonly formShafts = signal<ShaftSummary[]>([]);

  protected search = '';
  protected projectId = '';
  protected shaftId = '';
  protected categoryId = '';
  protected status = '';
  protected from = '';
  protected to = '';

  protected form: ExpenseFormModel = blankModel();

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.filterShafts.set(s), error: () => {} });

    // Landing point of the dashboard's cost-breakdown drill-down
    // (/expenses?shaftId=3&categoryId=7) — pre-filtering here is what keeps
    // the SRS §57 traceability chain unbroken; arriving unfiltered would
    // drop the operator back into the whole expense book.
    const params = this.route.snapshot.queryParamMap;
    this.shaftId = params.get('shaftId') ?? '';
    this.categoryId = params.get('categoryId') ?? '';

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
    this.api.expenses({
      status: this.status || undefined,
      projectId: this.projectId || undefined,
      shaftId: this.shaftId || undefined,
      categoryId: this.categoryId || undefined,
      from: this.from || undefined,
      to: this.to || undefined,
      search: this.search || undefined,
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
    if (!this.form.projectId || !this.form.categoryId || !this.form.expenseDate || !this.form.description
        || this.form.amount === null || !this.form.currency) {
      this.toast.error('Project, category, date, description, amount and currency are required.');
      return;
    }
    this.saving.set(true);
    // exchangeRate is not surfaced on this form — the API requires a value, and
    // 1 is correct whenever the expense is already booked in the base currency.
    this.api.createExpense({ ...this.form, exchangeRate: 1 }).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Expense recorded');
        this.formOpen.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }
}
