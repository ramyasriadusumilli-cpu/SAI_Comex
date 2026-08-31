import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { FormsModule } from '@angular/forms';

import { AgreementApi, ContractApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { AgreementSummary, ContractDetail, ContractVersionDto } from '../../core/models/api.models';
import { ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

type Tab = 'details' | 'agreement' | 'versions';

/** SRS §10 — a single contract: its record, commercial agreement and version history. */
@Component({
  selector: 'app-contract-detail',
  imports: [FormsModule, RouterLink, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading contract…</div>
      } @else if (contract(); as c) {

        <div class="crumbs">
          <a routerLink="/contracts">Contracts</a><span class="sep">›</span><span>{{ c.contractNumber }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ c.contractNumber }} <span class="muted" style="font-weight:400; font-size:14px">{{ c.title }}</span></h1>
            <div class="row" style="gap:8px">
              <span [class]="c.status | statusClass">{{ c.status | statusLabel }}</span>
              <span class="page-sub">{{ c.partnerName ?? '—' }} · {{ c.shaftName ?? c.projectName ?? '—' }}</span>
            </div>
          </div>
          <div class="row">
            @if (auth.has('contracts.edit') && (c.status === 'DRAFT' || c.status === 'PENDING_APPROVAL')) {
              <button class="btn btn-secondary" (click)="router.navigateByUrl('/contracts/' + c.id + '/edit')">Edit</button>
            }
            @if (auth.has('contracts.approve') && c.status !== 'ACTIVE') {
              <button class="btn" [disabled]="busy()" (click)="activate(c)">Activate</button>
            }
            @if (auth.has('contracts.approve') && c.status === 'ACTIVE') {
              <button class="btn btn-danger" [disabled]="busy()" (click)="terminate(c)">Terminate</button>
            }
            @if (auth.has('contracts.edit') && c.status === 'ACTIVE') {
              <button class="btn btn-secondary" (click)="toggleAmend()">{{ amending() ? 'Cancel amend' : 'Amend' }}</button>
            }
          </div>
        </div>

        @if (amending()) {
          <div class="card" style="margin-bottom:16px">
            <div class="card-header"><div class="card-title">Amend contract</div><div class="card-sub">Creates a new version — SRS §10 versioning</div></div>
            <form (ngSubmit)="submitAmend(c)">
              <div class="form-grid">
                <div class="field">
                  <label class="req" for="am-from">Effective from</label>
                  <input id="am-from" class="input" type="date" [(ngModel)]="amendForm.effectiveFrom" name="effectiveFrom" required>
                </div>
                <div class="field" style="grid-column: span 2">
                  <label class="req" for="am-reason">Change reason</label>
                  <input id="am-reason" class="input" [(ngModel)]="amendForm.changeReason" name="changeReason" required>
                </div>
              </div>
              <div class="row" style="margin-top:12px">
                <button class="btn" type="submit" [disabled]="busy()">
                  @if (busy()) { <span class="spin"></span> Submitting… } @else { Submit amendment }
                </button>
                <button class="btn btn-secondary" type="button" (click)="toggleAmend()">Cancel</button>
              </div>
            </form>
          </div>
        }

        <div class="tabs">
          <button class="tab" [class.active]="tab() === 'details'" (click)="tab.set('details')">Details</button>
          <button class="tab" [class.active]="tab() === 'agreement'" (click)="tab.set('agreement')">Commercial agreement</button>
          <button class="tab" [class.active]="tab() === 'versions'" (click)="tab.set('versions')">Versions</button>
        </div>

        @switch (tab()) {
          @case ('details') {
            <div class="card">
              <dl class="dl">
                <div><dt>Project</dt><dd>{{ c.projectName ?? '—' }}</dd></div>
                <div><dt>Shaft</dt><dd>{{ c.shaftName ?? '—' }}</dd></div>
                <div><dt>Partner</dt><dd>{{ c.partnerName ?? '—' }}</dd></div>
                <div><dt>Contract type</dt><dd>{{ c.contractTypeName ?? '—' }}</dd></div>
                <div><dt>Current version</dt><dd>{{ c.currentVersion ?? 1 }}</dd></div>
                <div><dt>Effective date</dt><dd>{{ c.effectiveDate | shortDate }}</dd></div>
                <div><dt>Expiry date</dt><dd>{{ c.expiryDate | shortDate }}</dd></div>
                <div><dt>Renewal date</dt><dd>{{ c.renewalDate | shortDate }}</dd></div>
                <div><dt>Signed date</dt><dd>{{ c.signedDate | shortDate }}</dd></div>
                <div><dt>Settlement currency</dt><dd>{{ c.settlementCurrency ?? '—' }}</dd></div>
                <div><dt>Settlement frequency</dt><dd>{{ c.settlementFrequency ?? '—' }}</dd></div>
                <div><dt>Approved by</dt><dd>{{ c.approvedBy ?? '—' }}</dd></div>
                <div><dt>Approved at</dt><dd>{{ c.approvedAt | shortDate }}</dd></div>
                @if (c.terminationNotes) {
                  <div style="grid-column: 1 / -1"><dt>Termination notes</dt><dd>{{ c.terminationNotes }}</dd></div>
                }
                <div style="grid-column: 1 / -1"><dt>Special conditions</dt><dd>{{ c.specialConditions ?? '—' }}</dd></div>
              </dl>
            </div>
          }
          @case ('agreement') {
            @if (activeAgreement(); as a) {
              <div class="card">
                <div class="card-header">
                  <div class="card-title">{{ a.name }}</div>
                  <span [class]="a.status | statusClass">{{ a.status | statusLabel }}</span>
                </div>
                <dl class="dl">
                  <div><dt>Settlement basis</dt><dd>{{ a.settlementBasis }}</dd></div>
                  <div><dt>Currency</dt><dd>{{ a.currency }}</dd></div>
                  <div><dt>Rules</dt><dd>{{ a.ruleCount ?? 0 }}</dd></div>
                  <div><dt>Effective from</dt><dd>{{ a.effectiveFrom | shortDate }}</dd></div>
                </dl>
                <div class="row" style="margin-top:12px">
                  <a class="btn btn-secondary btn-sm" [routerLink]="['/agreements', a.id]">Open agreement</a>
                </div>
              </div>
            } @else {
              <div class="banner banner-warn">
                This contract cannot settle until a commercial agreement is added and activated.
              </div>
              @if (auth.has('agreements.create')) {
                <div class="row" style="margin-top:12px">
                  <button class="btn" (click)="router.navigateByUrl('/contracts/' + c.id + '/agreements/new')">Add commercial agreement</button>
                </div>
              }
            }
            @if (agreements().length > 1) {
              <div class="card" style="margin-top:14px">
                <div class="card-header"><div class="card-title">All agreement versions</div></div>
                <div class="table-wrap">
                  <table class="data">
                    <thead><tr><th>Name</th><th>Status</th><th>Basis</th><th class="num">Rules</th><th>Effective from</th></tr></thead>
                    <tbody>
                      @for (a of agreements(); track a.id) {
                        <tr class="clickable" (click)="router.navigateByUrl('/agreements/' + a.id)">
                          <td>{{ a.name }}</td>
                          <td><span [class]="a.status | statusClass">{{ a.status | statusLabel }}</span></td>
                          <td class="muted">{{ a.settlementBasis }}</td>
                          <td class="num">{{ a.ruleCount ?? 0 }}</td>
                          <td class="muted">{{ a.effectiveFrom | shortDate }}</td>
                        </tr>
                      }
                    </tbody>
                  </table>
                </div>
              </div>
            }
          }
          @case ('versions') {
            <div class="card">
              <div class="table-wrap">
                <table class="data">
                  <thead>
                    <tr><th class="num">#</th><th>Effective from</th><th>Effective to</th><th>Reason</th><th>Status</th><th>Created</th></tr>
                  </thead>
                  <tbody>
                    @for (v of versions(); track v.id) {
                      <tr>
                        <td class="num">{{ v.versionNumber }}</td>
                        <td class="muted">{{ v.effectiveFrom | shortDate }}</td>
                        <td class="muted">{{ v.effectiveTo | shortDate }}</td>
                        <td>{{ v.changeReason }}</td>
                        <td><span [class]="v.status | statusClass">{{ v.status | statusLabel }}</span></td>
                        <td class="muted">{{ v.createdAt | shortDate }}</td>
                      </tr>
                    } @empty {
                      <tr><td class="empty" colspan="6">No version history recorded for this contract yet.</td></tr>
                    }
                  </tbody>
                </table>
              </div>
            </div>
          }
        }
      }
    </div>
  `,
  styles: [],
})
export class ContractDetailPage implements OnInit {

  readonly id = input.required<string>();

  private readonly api = inject(ContractApi);
  private readonly agreementApi = inject(AgreementApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly busy = signal(false);
  protected readonly contract = signal<ContractDetail | null>(null);
  protected readonly agreements = signal<AgreementSummary[]>([]);
  protected readonly versions = signal<ContractVersionDto[]>([]);
  protected readonly tab = signal<Tab>('details');
  protected readonly amending = signal(false);

  protected readonly activeAgreement = computed(() => this.agreements().find(a => a.status === 'ACTIVE') ?? null);

  protected amendForm = { effectiveFrom: '', changeReason: '' };

  ngOnInit(): void {
    this.reload();
  }

  protected activate(c: ContractDetail): void {
    if (this.busy()) return;
    this.busy.set(true);
    this.api.activate(c.id).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Contract activated'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  protected terminate(c: ContractDetail): void {
    if (this.busy()) return;
    const reason = prompt('Reason for terminating this contract (required):');
    if (!reason) return;
    this.busy.set(true);
    this.api.terminate(c.id, reason).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Contract terminated'); this.reload(); },
      error: () => this.busy.set(false),
    });
  }

  protected toggleAmend(): void {
    this.amending.update(v => !v);
    if (this.amending()) this.amendForm = { effectiveFrom: '', changeReason: '' };
  }

  protected submitAmend(c: ContractDetail): void {
    if (this.busy()) return;
    if (!this.amendForm.effectiveFrom || !this.amendForm.changeReason) {
      this.toast.error('Effective-from date and a change reason are both required.');
      return;
    }
    this.busy.set(true);
    this.api.amend(c.id, this.amendForm).subscribe({
      next: () => {
        this.busy.set(false);
        this.toast.success('Amendment recorded');
        this.amending.set(false);
        this.tab.set('versions');
        this.reload();
      },
      error: () => this.busy.set(false),
    });
  }

  private reload(): void {
    this.loading.set(true);
    const id = Number(this.id());
    this.api.get(id).subscribe({
      next: c => { this.contract.set(c); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
    this.agreementApi.byContract(id).subscribe({ next: a => this.agreements.set(a), error: () => {} });
    this.api.versions(id).subscribe({ next: v => this.versions.set(v), error: () => {} });
  }
}
