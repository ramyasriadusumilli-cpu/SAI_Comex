import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { SettlementApi, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PreviewResult, ShaftSummary } from '../../core/models/api.models';
import { MoneyPipe, ShortDatePipe } from '../../shared/format';

interface RunFormModel {
  shaftId: number | null;
  periodStart: string;
  periodEnd: string;
  notes: string;
}

function blankModel(): RunFormModel {
  return { shaftId: null, periodStart: '', periodEnd: '', notes: '' };
}

/**
 * SRS §12, §25 — the settlement calculation screen.
 *
 * "Calculate & save" writes a statement that drives real partner payables, so
 * it stays disabled until "Preview" has produced a result for the current
 * inputs — an operator must see the waterfall and the full derivation trail
 * before they can commit to it. Changing any input after a preview clears
 * that preview, so a save can never be made against numbers the operator
 * has not actually looked at.
 */
@Component({
  selector: 'app-settlement-run',
  imports: [FormsModule, RouterLink, MoneyPipe, ShortDatePipe],
  template: `
    <div class="page">
      <div class="crumbs">
        <a routerLink="/settlements">Settlements</a><span class="sep">›</span><span>Run a settlement</span>
      </div>

      <div class="page-header">
        <div class="page-title-group">
          <h1>Run a settlement</h1>
          <div class="page-sub">Preview the calculation before committing a statement</div>
        </div>
      </div>

      <div class="card" style="margin-bottom:16px">
        <div class="card-header"><div class="card-title">Period</div></div>
        <div class="form-grid">
          <div class="field">
            <label class="req" for="shaft">Shaft</label>
            <select id="shaft" class="select" [(ngModel)]="model.shaftId" (change)="invalidatePreview()">
              <option [ngValue]="null">Select shaft…</option>
              @for (s of shafts(); track s.id) { <option [ngValue]="s.id">{{ s.name }} ({{ s.projectName }})</option> }
            </select>
          </div>
          <div class="field">
            <label class="req" for="from">Period start</label>
            <input id="from" class="input" type="date" [(ngModel)]="model.periodStart" (change)="invalidatePreview()">
          </div>
          <div class="field">
            <label class="req" for="to">Period end</label>
            <input id="to" class="input" type="date" [(ngModel)]="model.periodEnd" (change)="invalidatePreview()">
          </div>
          <div class="field" style="grid-column: 1 / -1">
            <label for="notes">Notes</label>
            <textarea id="notes" class="textarea" [(ngModel)]="model.notes" (change)="invalidatePreview()"></textarea>
          </div>
        </div>
        <div class="row" style="margin-top:14px">
          <button class="btn btn-secondary" [disabled]="previewing()" (click)="preview()">
            @if (previewing()) { <span class="spin"></span> Calculating… } @else { Preview }
          </button>
          @if (auth.has('settlements.create')) {
            <button class="btn" [disabled]="!hasPreviewed() || saving()" (click)="calculateAndSave()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Calculate &amp; save }
            </button>
          }
          @if (!hasPreviewed()) {
            <span class="muted">Run a preview first — a statement cannot be saved unseen.</span>
          }
        </div>
      </div>

      @if (result(); as r) {
        @for (w of r.warnings; track w) {
          <div class="banner banner-warn" style="margin-bottom:10px">{{ w }}
          </div>
        }

        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Summary</div></div>
          <dl class="dl">
            <div><dt>Shaft</dt><dd>{{ r.shaftName }}</dd></div>
            <div><dt>Partner</dt><dd>{{ r.partnerName }}</dd></div>
            <div><dt>Contract</dt><dd>{{ r.contractNumber }}</dd></div>
            <div><dt>Agreement</dt><dd>{{ r.agreementName }}</dd></div>
            <div><dt>Period</dt><dd>{{ r.periodStart | shortDate }} – {{ r.periodEnd | shortDate }}</dd></div>
            <div><dt>Currency</dt><dd>{{ r.currency }}</dd></div>
          </dl>
        </div>

        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">Waterfall</div></div>
          <div class="waterfall">
            <div class="wf-row"><span>Gross revenue</span><span class="num mono">{{ r.grossRevenue | money: r.currency }}</span></div>
            <div class="wf-row wf-sub"><span>Less deductions</span><span class="num mono">−{{ r.totalDeductions | money: r.currency }}</span></div>
            <div class="wf-row wf-total"><span>Net distributable</span><span class="num mono">{{ r.netDistributable | money: r.currency }}</span></div>
            <div class="wf-row wf-sub"><span>SAIComex share</span><span class="num mono">{{ r.saicomexShare | money: r.currency }}</span></div>
            <div class="wf-row wf-sub"><span>Partner share</span><span class="num mono">{{ r.partnerShare | money: r.currency }}</span></div>
            <div class="wf-row wf-sub"><span>Partner adjustments</span><span class="num mono">{{ r.partnerAdjustments | money: r.currency }}</span></div>
            <div class="wf-row wf-total"><span>Partner net payable</span><span class="num mono">{{ r.partnerNetPayable | money: r.currency }}</span></div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <div>
              <div class="card-title">Derivation trail</div>
              <div class="card-sub">Every calculation step — the SRS §12 audit trail</div>
            </div>
          </div>
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr><th class="num">#</th><th>Stage</th><th>Rule</th><th>Expression</th><th class="num">Amount</th><th>Beneficiary</th></tr>
              </thead>
              <tbody>
                @for (step of r.steps; track step.stepNo) {
                  <tr>
                    <td class="num">{{ step.stepNo }}</td>
                    <td><span class="pill pill-info">{{ step.stage }}</span></td>
                    <td>{{ step.ruleName ?? '—' }}</td>
                    <td class="mono">{{ step.expression }}</td>
                    <td class="num">{{ step.resultAmount | money: r.currency }}</td>
                    <td class="muted">{{ step.beneficiary ?? '—' }}</td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="6">No calculation steps were produced for this period.</td></tr>
                }
              </tbody>
            </table>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .waterfall { display: flex; flex-direction: column; }
    .wf-row { display: flex; justify-content: space-between; padding: 8px 4px; border-bottom: 1px solid var(--line-soft); font-variant-numeric: tabular-nums; }
    .wf-row:last-child { border-bottom: none; }
    .wf-sub { color: var(--ink-soft); font-size: 12.5px; }
    .wf-total { font-weight: 700; border-top: 1px solid var(--line); margin-top: 2px; }
  `],
})
export class SettlementRunPage {

  private readonly api = inject(SettlementApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly shafts = signal<ShaftSummary[]>([]);
  protected readonly previewing = signal(false);
  protected readonly saving = signal(false);
  protected readonly hasPreviewed = signal(false);
  protected readonly result = signal<PreviewResult | null>(null);

  protected model: RunFormModel = blankModel();

  constructor() {
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
  }

  protected invalidatePreview(): void {
    this.hasPreviewed.set(false);
  }

  protected preview(): void {
    if (!this.model.shaftId || !this.model.periodStart || !this.model.periodEnd) {
      this.toast.error('Shaft, period start and period end are required.');
      return;
    }
    this.previewing.set(true);
    this.api.preview(this.model).subscribe({
      next: r => {
        this.result.set(r);
        this.hasPreviewed.set(true);
        this.previewing.set(false);
      },
      error: () => this.previewing.set(false),
    });
  }

  protected calculateAndSave(): void {
    if (!this.hasPreviewed() || this.saving()) return;
    this.saving.set(true);
    this.api.calculate(this.model).subscribe({
      next: saved => {
        this.saving.set(false);
        this.toast.success('Settlement calculated and saved');
        this.router.navigateByUrl('/settlements/' + saved.id);
      },
      error: () => this.saving.set(false),
    });
  }
}
