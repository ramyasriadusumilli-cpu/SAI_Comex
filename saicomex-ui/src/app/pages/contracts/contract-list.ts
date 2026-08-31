import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ContractApi, PartnerApi, ProjectApi, ShaftApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ContractSummary, PageResponse, PartnerSummary, ProjectSummary, ShaftSummary } from '../../core/models/api.models';
import { ShortDatePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

/**
 * SRS §10 — contracts. The "Commercial terms" column exists because an
 * ACTIVE contract with no ACTIVE agreement cannot settle (the engine and the
 * API both refuse it) — it is the most common setup mistake, so the list
 * surfaces it rather than leaving it to be discovered at settlement time.
 */
@Component({
  selector: 'app-contract-list',
  imports: [FormsModule, ShortDatePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Contracts</h1>
          <div class="page-sub">Mining and settlement contracts with partners</div>
        </div>
        @if (auth.has('contracts.create')) {
          <button class="btn" (click)="router.navigateByUrl('/contracts/new')"> New contract
          </button>
        }
      </div>

      @if (expiringCount() > 0) {
        <div class="banner banner-warn" style="margin-bottom:14px">
          {{ expiringCount() }} contract{{ expiringCount() === 1 ? '' : 's' }} expire within 30 days.
        </div>
      }

      <div class="toolbar">
        <input class="input" placeholder="Search contract number or title…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          @for (s of reference.data()?.contractStatuses; track s) {
            <option [value]="s">{{ s | statusLabel }}</option>
          }
        </select>
        <select class="select" [(ngModel)]="projectId" (change)="onProjectChange()">
          <option value="">All projects</option>
          @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="shaftId" (change)="reload()">
          <option value="">All shafts</option>
          @for (s of shafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
        </select>
        <select class="select" [(ngModel)]="partnerId" (change)="reload()">
          <option value="">All partners</option>
          @for (p of partners(); track p.id) { <option [value]="p.id">{{ p.legalName }}</option> }
        </select>
        <div class="spacer"></div>
        <button class="btn btn-secondary btn-sm" (click)="reset()">Clear filters</button>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading contracts…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Contract number</th><th>Project</th><th>Shaft</th><th>Partner</th>
                  <th>Type</th><th>Status</th><th>Effective</th><th>Expiry</th><th>Commercial terms</th>
                </tr>
              </thead>
              <tbody>
                @for (c of page()?.content ?? []; track c.id) {
                  <tr class="clickable" (click)="router.navigateByUrl('/contracts/' + c.id)">
                    <td class="mono">{{ c.contractNumber }}</td>
                    <td class="muted">{{ c.projectName ?? '—' }}</td>
                    <td class="muted">{{ c.shaftName ?? '—' }}</td>
                    <td class="muted">{{ c.partnerName ?? '—' }}</td>
                    <td class="muted">{{ c.contractTypeName ?? '—' }}</td>
                    <td><span [class]="c.status | statusClass">{{ c.status | statusLabel }}</span></td>
                    <td class="muted">{{ c.effectiveDate | shortDate }}</td>
                    <td class="muted">{{ c.expiryDate | shortDate }}</td>
                    <td>
                      @if (c.hasActiveAgreement) {
                        <span class="pill pill-active">✓ Agreement active</span>
                      } @else if (c.status === 'ACTIVE') {
                        <span class="pill pill-suspended">⚠ No agreement — cannot settle</span>
                      } @else {
                        <span class="muted">—</span>
                      }
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No contracts match these filters. Try clearing them, or create a new contract.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} contracts</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class ContractListPage {

  private readonly api = inject(ContractApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly partnerApi = inject(PartnerApi);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly loading = signal(true);
  protected readonly page = signal<PageResponse<ContractSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly expiringCount = signal(0);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);
  protected readonly partners = signal<PartnerSummary[]>([]);

  protected search = '';
  protected status = '';
  protected projectId = '';
  protected shaftId = '';
  protected partnerId = '';

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.partnerApi.options().subscribe({ next: p => this.partners.set(p), error: () => {} });
    this.api.expiring(30).subscribe({ next: list => this.expiringCount.set(list.length), error: () => {} });
    this.reload();
  }

  protected onProjectChange(): void {
    this.shaftId = '';
    this.shaftApi.options(this.projectId ? { projectId: this.projectId } : undefined)
      .subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.reload();
  }

  protected reset(): void {
    this.search = '';
    this.status = '';
    this.projectId = '';
    this.shaftId = '';
    this.partnerId = '';
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
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
      projectId: this.projectId || undefined,
      shaftId: this.shaftId || undefined,
      partnerId: this.partnerId || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }
}
