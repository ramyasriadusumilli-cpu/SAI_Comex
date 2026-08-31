import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ContractApi, PartnerApi, ProjectApi, ShaftApi, ReferenceService } from '../../core/services/domain.services';
import { ToastService } from '../../core/services/toast.service';
import { ContractDetail, PartnerSummary, ProjectSummary, ShaftSummary } from '../../core/models/api.models';

const FREQUENCIES = ['WEEKLY', 'FORTNIGHTLY', 'MONTHLY', 'PER_SALE'];

function emptyForm(): Record<string, unknown> {
  return {
    contractNumber: '', title: '', contractTypeId: '', projectId: '', shaftId: '', partnerId: '',
    effectiveDate: '', expiryDate: '', renewalDate: '', signedDate: '',
    settlementCurrency: '', settlementFrequency: 'MONTHLY', specialConditions: '',
  };
}

/** SRS §10 — create or edit a contract. Serves both /contracts/new and /contracts/:id/edit. */
@Component({
  selector: 'app-contract-form',
  imports: [FormsModule],
  template: `
    <div class="page" style="max-width:900px">
      <div class="page-header">
        <div class="page-title-group">
          <h1>{{ isEdit() ? 'Edit contract' : 'New contract' }}</h1>
          <div class="page-sub">{{ isEdit() ? form['contractNumber'] : 'SRS §10 contract record' }}</div>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading contract…</div>
      } @else {
        <div class="card">
          <form (ngSubmit)="save()">
            <div class="form-grid">
              <div class="field">
                <label class="req" for="cf-number">Contract number</label>
                <input id="cf-number" class="input" [(ngModel)]="form['contractNumber']" name="contractNumber" required>
              </div>
              <div class="field">
                <label class="req" for="cf-title">Title</label>
                <input id="cf-title" class="input" [(ngModel)]="form['title']" name="title" required>
              </div>
              <div class="field">
                <label class="req" for="cf-type">Contract type</label>
                <select id="cf-type" class="select" [(ngModel)]="form['contractTypeId']" name="contractTypeId" required>
                  <option value="">Select type…</option>
                  @for (t of reference.data()?.contractTypes; track t.id) { <option [value]="t.id">{{ t.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label class="req" for="cf-project">Project</label>
                <select id="cf-project" class="select" [(ngModel)]="form['projectId']" name="projectId" (change)="onProjectChange()" required>
                  <option value="">Select project…</option>
                  @for (p of projects(); track p.id) { <option [value]="p.id">{{ p.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="cf-shaft">Shaft</label>
                <select id="cf-shaft" class="select" [(ngModel)]="form['shaftId']" name="shaftId" [disabled]="!form['projectId']">
                  <option value="">{{ form['projectId'] ? 'Select shaft…' : 'Select a project first' }}</option>
                  @for (s of shafts(); track s.id) { <option [value]="s.id">{{ s.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label class="req" for="cf-partner">Partner</label>
                <select id="cf-partner" class="select" [(ngModel)]="form['partnerId']" name="partnerId" required>
                  <option value="">Select partner…</option>
                  @for (p of partners(); track p.id) { <option [value]="p.id">{{ p.legalName }}</option> }
                </select>
              </div>
              <div class="field">
                <label class="req" for="cf-effective">Effective date</label>
                <input id="cf-effective" class="input" type="date" [(ngModel)]="form['effectiveDate']" name="effectiveDate" required>
              </div>
              <div class="field">
                <label for="cf-expiry">Expiry date</label>
                <input id="cf-expiry" class="input" type="date" [(ngModel)]="form['expiryDate']" name="expiryDate">
              </div>
              <div class="field">
                <label for="cf-renewal">Renewal date</label>
                <input id="cf-renewal" class="input" type="date" [(ngModel)]="form['renewalDate']" name="renewalDate">
              </div>
              <div class="field">
                <label for="cf-signed">Signed date</label>
                <input id="cf-signed" class="input" type="date" [(ngModel)]="form['signedDate']" name="signedDate">
              </div>
              <div class="field">
                <label class="req" for="cf-currency">Settlement currency</label>
                <select id="cf-currency" class="select" [(ngModel)]="form['settlementCurrency']" name="settlementCurrency" required>
                  <option value="">Select currency…</option>
                  @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }} — {{ c.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="cf-frequency">Settlement frequency</label>
                <select id="cf-frequency" class="select" [(ngModel)]="form['settlementFrequency']" name="settlementFrequency">
                  @for (f of frequencies; track f) { <option [value]="f">{{ f }}</option> }
                </select>
              </div>
              <div class="field" style="grid-column: 1 / -1">
                <label for="cf-conditions">Special conditions</label>
                <textarea id="cf-conditions" class="textarea" [(ngModel)]="form['specialConditions']" name="specialConditions"></textarea>
              </div>
            </div>

            <div class="row" style="margin-top:16px">
              <button class="btn" type="submit" [disabled]="saving()">
                @if (saving()) { <span class="spin"></span> Saving… } @else { {{ isEdit() ? 'Save changes' : 'Create contract' }} }
              </button>
              <button class="btn btn-secondary" type="button" (click)="cancel()">Cancel</button>
            </div>
          </form>
        </div>
      }
    </div>
  `,
  styles: [],
})
export class ContractFormPage implements OnInit {

  readonly id = input<string>();

  private readonly api = inject(ContractApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly partnerApi = inject(PartnerApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly router = inject(Router);

  protected readonly frequencies = FREQUENCIES;
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);
  protected readonly partners = signal<PartnerSummary[]>([]);

  protected form: Record<string, unknown> = emptyForm();
  protected isEdit = () => !!this.id();

  ngOnInit(): void {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.partnerApi.options().subscribe({ next: p => this.partners.set(p), error: () => {} });

    if (this.id()) {
      this.loading.set(true);
      this.api.get(Number(this.id())).subscribe({
        next: c => {
          this.form = toForm(c);
          if (c.projectId) {
            this.shaftApi.options({ projectId: c.projectId }).subscribe({ next: s => this.shafts.set(s), error: () => {} });
          }
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }
  }

  protected onProjectChange(): void {
    this.form['shaftId'] = '';
    const projectId = this.form['projectId'];
    this.shafts.set([]);
    if (projectId) {
      this.shaftApi.options({ projectId }).subscribe({ next: s => this.shafts.set(s), error: () => {} });
    }
  }

  protected save(): void {
    if (this.saving()) return;
    this.saving.set(true);
    const req = this.isEdit() ? this.api.update(Number(this.id()), this.form) : this.api.create(this.form);
    req.subscribe({
      next: c => {
        this.saving.set(false);
        this.toast.success(this.isEdit() ? 'Contract updated' : 'Contract created');
        this.router.navigateByUrl('/contracts/' + c.id);
      },
      error: () => this.saving.set(false),
    });
  }

  protected cancel(): void {
    this.router.navigateByUrl(this.isEdit() ? '/contracts/' + this.id() : '/contracts');
  }
}

function toForm(c: ContractDetail): Record<string, unknown> {
  return {
    contractNumber: c.contractNumber, title: c.title ?? '', contractTypeId: c.contractTypeId ?? '',
    projectId: c.projectId ?? '', shaftId: c.shaftId ?? '', partnerId: c.partnerId ?? '',
    effectiveDate: c.effectiveDate ?? '', expiryDate: c.expiryDate ?? '', renewalDate: c.renewalDate ?? '',
    signedDate: c.signedDate ?? '', settlementCurrency: c.settlementCurrency ?? '',
    settlementFrequency: c.settlementFrequency ?? 'MONTHLY', specialConditions: c.specialConditions ?? '',
  };
}
