import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { PartnerApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PartnerDetail } from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

const TYPES = ['COMPANY', 'INDIVIDUAL', 'COOPERATIVE', 'GOVERNMENT', 'TRUST'];
const STATUSES = ['ACTIVE', 'INACTIVE', 'BLACKLISTED'];

/** SRS §9 — a single partner: its record, banking, shafts and contracts. */
@Component({
  selector: 'app-partner-detail',
  imports: [FormsModule, RouterLink, MoneyPipe, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading partner…</div>
      } @else if (partner(); as p) {

        <div class="crumbs">
          <a routerLink="/partners">Partners</a><span class="sep">›</span><span>{{ p.legalName }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ p.legalName }} <span class="muted mono" style="font-weight:400; font-size:14px">{{ p.code }}</span></h1>
            <div class="row" style="gap:8px">
              <span [class]="p.status | statusClass">{{ p.status | statusLabel }}</span>
              <span class="page-sub">{{ p.partnerType }}</span>
            </div>
          </div>
          @if (auth.has('partners.edit')) {
            <button class="btn btn-secondary" (click)="toggleEdit()">
              {{ editing() ? 'Cancel edit' : 'Edit' }}
            </button>
          }
        </div>

        @if (auth.has('financial.view')) {
          <div class="kpi-grid" style="margin-bottom:16px">
            <div class="kpi">
              <span class="kpi-label">Total payable</span>
              <span class="kpi-value">{{ p.totalPayable | money }}</span>
            </div>
            <div class="kpi">
              <span class="kpi-label">Total paid</span>
              <span class="kpi-value">{{ p.totalPaid | money }}</span>
            </div>
            <div class="kpi kpi-accent">
              <span class="kpi-label">Outstanding</span>
              <span class="kpi-value">{{ p.outstanding | money }}</span>
            </div>
          </div>
        }

        @if (editing()) {
          <div class="card" style="margin-bottom:16px">
            <div class="card-header"><div class="card-title">Edit partner record</div></div>
            <form (ngSubmit)="save(p)">
              <div class="form-grid">
                <div class="field">
                  <label class="req" for="ep-name">Legal name</label>
                  <input id="ep-name" class="input" [(ngModel)]="form.legalName" name="legalName" required>
                </div>
                <div class="field">
                  <label for="ep-trading">Trading name</label>
                  <input id="ep-trading" class="input" [(ngModel)]="form.tradingName" name="tradingName">
                </div>
                <div class="field">
                  <label for="ep-type">Partner type</label>
                  <select id="ep-type" class="select" [(ngModel)]="form.partnerType" name="partnerType">
                    @for (t of types; track t) { <option [value]="t">{{ t }}</option> }
                  </select>
                </div>
                <div class="field">
                  <label for="ep-status">Status</label>
                  <select id="ep-status" class="select" [(ngModel)]="form.status" name="status">
                    @for (s of statuses; track s) { <option [value]="s">{{ s | statusLabel }}</option> }
                  </select>
                </div>
                <div class="field">
                  <label for="ep-contact">Contact person</label>
                  <input id="ep-contact" class="input" [(ngModel)]="form.contactPerson" name="contactPerson">
                </div>
                <div class="field">
                  <label for="ep-phone">Phone</label>
                  <input id="ep-phone" class="input" [(ngModel)]="form.phone" name="phone">
                </div>
                <div class="field">
                  <label for="ep-email">Email</label>
                  <input id="ep-email" class="input" type="email" [(ngModel)]="form.email" name="email">
                </div>
                <div class="field">
                  <label for="ep-address">Address</label>
                  <input id="ep-address" class="input" [(ngModel)]="form.address" name="address">
                </div>
                <div class="field">
                  <label for="ep-city">City</label>
                  <input id="ep-city" class="input" [(ngModel)]="form.city" name="city">
                </div>
                <div class="field">
                  <label for="ep-country">Country</label>
                  <input id="ep-country" class="input" [(ngModel)]="form.country" name="country">
                </div>
                <div class="field">
                  <label for="ep-reg">Registration number</label>
                  <input id="ep-reg" class="input" [(ngModel)]="form.registrationNumber" name="registrationNumber">
                </div>
                <div class="field">
                  <label for="ep-tax">Tax number</label>
                  <input id="ep-tax" class="input" [(ngModel)]="form.taxNumber" name="taxNumber">
                </div>
                @if (auth.has('partners.banking')) {
                  <div class="field">
                    <label for="ep-bankname">Bank name</label>
                    <input id="ep-bankname" class="input" [(ngModel)]="form.bankName" name="bankName">
                  </div>
                  <div class="field">
                    <label for="ep-bankbranch">Bank branch</label>
                    <input id="ep-bankbranch" class="input" [(ngModel)]="form.bankBranch" name="bankBranch">
                  </div>
                  <div class="field">
                    <label for="ep-bankaccname">Account name</label>
                    <input id="ep-bankaccname" class="input" [(ngModel)]="form.bankAccountName" name="bankAccountName">
                  </div>
                  <div class="field">
                    <label for="ep-bankaccno">Account number</label>
                    <input id="ep-bankaccno" class="input" [(ngModel)]="form.bankAccountNumber" name="bankAccountNumber">
                  </div>
                  <div class="field">
                    <label for="ep-swift">SWIFT</label>
                    <input id="ep-swift" class="input" [(ngModel)]="form.bankSwift" name="bankSwift">
                  </div>
                  <div class="field">
                    <label for="ep-paymethod">Payment method</label>
                    <input id="ep-paymethod" class="input" [(ngModel)]="form.paymentMethod" name="paymentMethod">
                  </div>
                }
                <div class="field" style="grid-column: 1 / -1">
                  <label for="ep-notes">Notes</label>
                  <textarea id="ep-notes" class="textarea" [(ngModel)]="form.notes" name="notes"></textarea>
                </div>
              </div>
              <div class="row" style="margin-top:14px">
                <button class="btn" type="submit" [disabled]="saving()">
                  @if (saving()) { <span class="spin"></span> Saving… } @else { Save changes }
                </button>
                <button class="btn btn-secondary" type="button" (click)="toggleEdit()">Cancel</button>
              </div>
            </form>
          </div>
        }

        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Partner record</div></div>
          <dl class="dl">
            <div><dt>Trading name</dt><dd>{{ p.tradingName ?? '—' }}</dd></div>
            <div><dt>Contact person</dt><dd>{{ p.contactPerson ?? '—' }}</dd></div>
            <div><dt>Phone</dt><dd>{{ p.phone ?? '—' }}</dd></div>
            <div><dt>Email</dt><dd>{{ p.email ?? '—' }}</dd></div>
            <div><dt>Address</dt><dd>{{ p.address ?? '—' }}</dd></div>
            <div><dt>City</dt><dd>{{ p.city ?? '—' }}</dd></div>
            <div><dt>Country</dt><dd>{{ p.country ?? '—' }}</dd></div>
            <div><dt>Registration number</dt><dd>{{ p.registrationNumber ?? '—' }}</dd></div>
            <div><dt>Tax number</dt><dd>{{ p.taxNumber ?? '—' }}</dd></div>
            <div><dt>ID number</dt><dd>{{ p.idNumber ?? '—' }}</dd></div>
            <div><dt>Onboarded</dt><dd>{{ p.onboardedDate | shortDate }}</dd></div>
            <div style="grid-column: 1 / -1"><dt>Notes</dt><dd>{{ p.notes ?? '—' }}</dd></div>
          </dl>
        </div>

        @if (auth.has('partners.banking') && hasBanking(p)) {
          <div class="card" style="margin-bottom:16px">
            <div class="card-header"><div class="card-title">Banking details</div></div>
            <dl class="dl">
              <div><dt>Bank name</dt><dd>{{ p.bankName ?? '—' }}</dd></div>
              <div><dt>Branch</dt><dd>{{ p.bankBranch ?? '—' }}</dd></div>
              <div><dt>Account name</dt><dd>{{ p.bankAccountName ?? '—' }}</dd></div>
              <div><dt>Account number</dt><dd>{{ p.bankAccountNumber ?? '—' }}</dd></div>
              <div><dt>SWIFT</dt><dd>{{ p.bankSwift ?? '—' }}</dd></div>
              <div><dt>Payment currency</dt><dd>{{ p.paymentCurrency ?? '—' }}</dd></div>
              <div><dt>Payment method</dt><dd>{{ p.paymentMethod ?? '—' }}</dd></div>
            </dl>
          </div>
        } @else if (!auth.has('partners.banking')) {
          <p class="muted" style="margin: 0 0 16px">Banking details are restricted to authorised roles.</p>
        }

        <div class="stack">
          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Shafts</div>
                <div class="card-sub">Shafts owned by this partner</div>
              </div>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Code</th><th>Name</th><th>Project</th><th>Status</th></tr></thead>
                <tbody>
                  @for (s of p.shafts ?? []; track s.shaftId) {
                    <tr class="clickable" (click)="router.navigateByUrl('/shafts/' + s.shaftId)">
                      <td class="mono">{{ s.shaftCode }}</td>
                      <td><strong>{{ s.shaftName }}</strong></td>
                      <td class="muted">{{ s.projectName ?? '—' }}</td>
                      <td><span [class]="s.status | statusClass">{{ s.status | statusLabel }}</span></td>
                    </tr>
                  } @empty {
                    <tr><td class="empty" colspan="4">No shafts recorded for this partner yet.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <div class="card">
            <div class="card-header">
              <div>
                <div class="card-title">Contracts</div>
                <div class="card-sub">Contracts held by this partner</div>
              </div>
            </div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Contract</th><th>Shaft</th><th>Status</th><th>Effective</th><th>Expiry</th></tr></thead>
                <tbody>
                  @for (c of p.contracts ?? []; track c.contractId) {
                    <tr class="clickable" (click)="router.navigateByUrl('/contracts/' + c.contractId)">
                      <td class="mono">{{ c.contractNumber }}</td>
                      <td class="muted">{{ c.shaftName ?? '—' }}</td>
                      <td><span [class]="c.status | statusClass">{{ c.status | statusLabel }}</span></td>
                      <td class="muted">{{ c.effectiveDate | shortDate }}</td>
                      <td class="muted">{{ c.expiryDate | shortDate }}</td>
                    </tr>
                  } @empty {
                    <tr><td class="empty" colspan="5">No contracts recorded for this partner yet.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [],
})
export class PartnerDetailPage implements OnInit {

  readonly id = input.required<string>();

  private readonly api = inject(PartnerApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly types = TYPES;
  protected readonly statuses = STATUSES;

  protected readonly loading = signal(true);
  protected readonly partner = signal<PartnerDetail | null>(null);
  protected readonly editing = signal(false);
  protected readonly saving = signal(false);

  protected form: Record<string, unknown> = {};

  ngOnInit(): void {
    this.reload();
  }

  protected hasBanking(p: PartnerDetail): boolean {
    return !!(p.bankName || p.bankAccountNumber || p.bankAccountName || p.bankSwift
      || p.bankBranch || p.paymentCurrency || p.paymentMethod);
  }

  protected toggleEdit(): void {
    this.editing.update(v => !v);
    const p = this.partner();
    if (this.editing() && p) {
      this.form = {
        code: p.code, legalName: p.legalName, tradingName: p.tradingName, partnerType: p.partnerType,
        contactPerson: p.contactPerson, phone: p.phone, email: p.email, address: p.address,
        city: p.city, country: p.country, registrationNumber: p.registrationNumber, taxNumber: p.taxNumber,
        idNumber: p.idNumber, status: p.status, notes: p.notes,
        bankName: p.bankName, bankBranch: p.bankBranch, bankAccountName: p.bankAccountName,
        bankAccountNumber: p.bankAccountNumber, bankSwift: p.bankSwift,
        paymentCurrency: p.paymentCurrency, paymentMethod: p.paymentMethod,
      };
    }
  }

  protected save(p: PartnerDetail): void {
    if (this.saving()) return;
    this.saving.set(true);
    this.api.update(p.id, this.form).subscribe({
      next: () => {
        this.saving.set(false);
        this.toast.success('Partner updated');
        this.editing.set(false);
        this.reload();
      },
      error: () => this.saving.set(false),
    });
  }

  private reload(): void {
    this.loading.set(true);
    this.api.get(Number(this.id())).subscribe({
      next: p => { this.partner.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
