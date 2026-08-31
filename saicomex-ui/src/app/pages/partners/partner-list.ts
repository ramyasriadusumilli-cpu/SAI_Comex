import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { PartnerApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PageResponse, PartnerSummary } from '../../core/models/api.models';
import { MoneyPipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

const STATUSES = ['ACTIVE', 'INACTIVE', 'BLACKLISTED'];
const TYPES = ['COMPANY', 'INDIVIDUAL', 'COOPERATIVE', 'GOVERNMENT', 'TRUST'];

function emptyForm(): Record<string, unknown> {
  return {
    code: '', legalName: '', tradingName: '', partnerType: 'COMPANY',
    contactPerson: '', phone: '', email: '', address: '', city: '', country: '',
    registrationNumber: '', taxNumber: '', idNumber: '', status: 'ACTIVE', notes: '',
  };
}

/** SRS §9 — partners: shaft owners / claim holders SAIComex contracts with. */
@Component({
  selector: 'app-partner-list',
  imports: [FormsModule, MoneyPipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Partners</h1>
          <div class="page-sub">Shaft owners and claim holders SAIComex contracts with</div>
        </div>
        @if (auth.has('partners.create')) {
          <button class="btn" (click)="toggleCreate()">
            {{ creating() ? 'Cancel' : 'New partner' }}
          </button>
        }
      </div>

      @if (creating()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header">
            <div class="card-title">New partner</div>
          </div>
          <form (ngSubmit)="create()">
            <div class="form-grid">
              <div class="field">
                <label class="req" for="np-code">Code</label>
                <input id="np-code" class="input" [(ngModel)]="form.code" name="code" required>
              </div>
              <div class="field">
                <label class="req" for="np-name">Legal name</label>
                <input id="np-name" class="input" [(ngModel)]="form.legalName" name="legalName" required>
              </div>
              <div class="field">
                <label for="np-trading">Trading name</label>
                <input id="np-trading" class="input" [(ngModel)]="form.tradingName" name="tradingName">
              </div>
              <div class="field">
                <label for="np-type">Partner type</label>
                <select id="np-type" class="select" [(ngModel)]="form.partnerType" name="partnerType">
                  @for (t of types; track t) { <option [value]="t">{{ t }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="np-contact">Contact person</label>
                <input id="np-contact" class="input" [(ngModel)]="form.contactPerson" name="contactPerson">
              </div>
              <div class="field">
                <label for="np-phone">Phone</label>
                <input id="np-phone" class="input" [(ngModel)]="form.phone" name="phone">
              </div>
              <div class="field">
                <label for="np-email">Email</label>
                <input id="np-email" class="input" type="email" [(ngModel)]="form.email" name="email">
              </div>
              <div class="field">
                <label for="np-status">Status</label>
                <select id="np-status" class="select" [(ngModel)]="form.status" name="status">
                  @for (s of statuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="np-address">Address</label>
                <input id="np-address" class="input" [(ngModel)]="form.address" name="address">
              </div>
              <div class="field">
                <label for="np-city">City</label>
                <input id="np-city" class="input" [(ngModel)]="form.city" name="city">
              </div>
              <div class="field">
                <label for="np-country">Country</label>
                <input id="np-country" class="input" [(ngModel)]="form.country" name="country">
              </div>
              <div class="field">
                <label for="np-reg">Registration number</label>
                <input id="np-reg" class="input" [(ngModel)]="form.registrationNumber" name="registrationNumber">
              </div>
              <div class="field">
                <label for="np-tax">Tax number</label>
                <input id="np-tax" class="input" [(ngModel)]="form.taxNumber" name="taxNumber">
              </div>
              <div class="field">
                <label for="np-idnum">ID number</label>
                <input id="np-idnum" class="input" [(ngModel)]="form.idNumber" name="idNumber">
              </div>
              <div class="field" style="grid-column: 1 / -1">
                <label for="np-notes">Notes</label>
                <textarea id="np-notes" class="textarea" [(ngModel)]="form.notes" name="notes"></textarea>
              </div>
            </div>
            <div class="row" style="margin-top:14px">
              <button class="btn" type="submit" [disabled]="saving()">
                @if (saving()) { <span class="spin"></span> Saving… } @else { Create partner }
              </button>
              <button class="btn btn-secondary" type="button" (click)="toggleCreate()">Cancel</button>
            </div>
          </form>
        </div>
      }

      <div class="toolbar">
        <input class="input" placeholder="Search code, name or contact…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of statuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
        </select>
        <div class="spacer"></div>
        <button class="btn btn-secondary btn-sm" (click)="reset()">Clear filters</button>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading partners…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Code</th><th>Legal name</th><th>Type</th><th>Contact</th>
                  <th>Phone</th><th>Email</th><th>Status</th><th class="num">Shafts</th>
                  @if (auth.has('financial.view')) { <th class="num">Outstanding</th> }
                </tr>
              </thead>
              <tbody>
                @for (p of page()?.content ?? []; track p.id) {
                  <tr class="clickable" (click)="router.navigateByUrl('/partners/' + p.id)">
                    <td class="mono">{{ p.code }}</td>
                    <td><strong>{{ p.legalName }}</strong> @if (p.tradingName) { <span class="muted">({{ p.tradingName }})</span> }</td>
                    <td class="muted">{{ p.partnerType }}</td>
                    <td class="muted">{{ p.contactPerson ?? '—' }}</td>
                    <td class="muted">{{ p.phone ?? '—' }}</td>
                    <td class="muted">{{ p.email ?? '—' }}</td>
                    <td><span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span></td>
                    <td class="num">{{ p.shaftCount ?? 0 }}</td>
                    @if (auth.has('financial.view')) {
                      <td class="num">{{ p.outstanding | money }}</td>
                    }
                  </tr>
                } @empty {
                  <tr><td class="empty" [attr.colspan]="auth.has('financial.view') ? 9 : 8">No partners match these filters. Try clearing them, or create a new partner.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} partners</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class PartnerListPage {

  private readonly api = inject(PartnerApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly statuses = STATUSES;
  protected readonly types = TYPES;

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<PartnerSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly creating = signal(false);
  protected readonly saving = signal(false);

  protected search = '';
  protected status = '';
  protected form = emptyForm();

  constructor() {
    this.reload();
  }

  protected toggleCreate(): void {
    this.creating.update(v => !v);
    if (this.creating()) this.form = emptyForm();
  }

  protected create(): void {
    if (this.saving()) return;
    if (!this.form['code'] || !this.form['legalName']) {
      this.toast.error('Code and legal name are required.');
      return;
    }
    this.saving.set(true);
    this.api.create(this.form).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Partner created');
        this.creating.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }

  protected reset(): void {
    this.search = '';
    this.status = '';
    this.setPage(0);
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.list({
      search: this.search || undefined,
      status: this.status || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
