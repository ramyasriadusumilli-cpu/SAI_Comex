import { Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AgreementApi, ContractApi, ReferenceService } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import {
  AgreementDetail, AgreementRuleDto, AgreementRuleTypeDto, ContractDetail,
} from '../../core/models/api.models';
import { StatusClassPipe, StatusLabelPipe } from '../../shared/format';

const ROUNDING_MODES = ['HALF_UP', 'HALF_DOWN', 'HALF_EVEN', 'UP', 'DOWN', 'CEILING', 'FLOOR'];
const ALLOCATION_TYPES = ['REVENUE_SHARE', 'PRODUCTION_SHARE', 'PROFIT_SHARE'];
const STAGE_ORDER = ['DEDUCTION', 'ALLOCATION', 'ADJUSTMENT'];

type AgreementFormModel = Partial<AgreementDetail> & { rules: AgreementRuleDto[] };

/**
 * SRS §11/§12 — the commercial agreement builder.
 *
 * The rule editor and the waterfall preview mirror
 * {@code CommercialCalculationEngine} exactly: stage order (DEDUCTION →
 * ALLOCATION → ADJUSTMENT), the deductBeforeSplit split point, and the
 * activation checks (percentages summing to 100, an allocation rule or
 * default split, every TIERED rule ending open-ended) are all enforced
 * server-side — this screen exists so the operator sees the problem before
 * they hit the 422, not instead of the server check.
 */
@Component({
  selector: 'app-agreement-builder',
  imports: [FormsModule, RouterLink, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading agreement…</div>
      } @else {

        <div class="crumbs">
          <a routerLink="/contracts">Contracts</a><span class="sep">›</span>
          @if (linkedContractId(); as cid) {
            <a [routerLink]="['/contracts', cid]">{{ contractSummaryLine() }}</a><span class="sep">›</span>
          }
          <span>{{ model.name || 'New agreement' }}</span>
        </div>

        <div class="page-header">
          <div class="page-title-group">
            <h1>{{ model.name || 'New commercial agreement' }}</h1>
            <div class="row" style="gap:8px">
              @if (model.status) { <span [class]="model.status | statusClass">{{ model.status | statusLabel }}</span> }
              <span class="page-sub">{{ contractSummaryLine() }}</span>
            </div>
          </div>
          <div class="row">
            @if (!readOnly()) {
              <button class="btn btn-secondary" [disabled]="saving()" (click)="save()">
                @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
              </button>
            }
            @if (!isNew() && model.status !== 'ACTIVE' && auth.has('agreements.approve')) {
              <button class="btn" [disabled]="activating()" (click)="activateAgreement()">
                @if (activating()) { <span class="spin"></span> Activating… } @else { Activate }
              </button>
            }
          </div>
        </div>

        @if (readOnly()) {
          <div class="banner banner-info" style="margin-bottom:16px">
            This agreement is active and cannot be edited in place. To change its terms, create a new agreement
            version for this contract and activate it — activating supersedes this one.
          </div>
        }

        @if (!readOnly() && validationMessages().length > 0) {
          <div class="banner banner-warn validation-banner">
            <div>
              <strong>Before this agreement can be activated:</strong>
              <ul>
                @for (m of validationMessages(); track m) { <li>{{ m }}</li> }
              </ul>
            </div>
          </div>
        }

        <div class="stack">
          <div class="card">
            <div class="card-header"><div class="card-title">Agreement</div></div>
            <div class="form-grid">
              <div class="field">
                <label class="req">Name</label>
                <input class="input" [(ngModel)]="model.name" [disabled]="readOnly()">
              </div>
              <div class="field" style="grid-column: span 2">
                <label>Description</label>
                <input class="input" [(ngModel)]="model.description" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label>Settlement basis</label>
                <select class="select" [(ngModel)]="model.settlementBasis" [disabled]="readOnly()">
                  <option value="NET_REVENUE">Net revenue</option>
                  <option value="GROSS_REVENUE">Gross revenue</option>
                  <option value="PRODUCTION">Production</option>
                  <option value="PROFIT">Profit</option>
                </select>
              </div>
              <div class="field">
                <label class="req">Effective from</label>
                <input class="input" type="date" [(ngModel)]="model.effectiveFrom" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label>Effective to</label>
                <input class="input" type="date" [(ngModel)]="model.effectiveTo" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label class="req">Currency</label>
                <select class="select" [(ngModel)]="model.currency" [disabled]="readOnly()">
                  <option value="">Select currency…</option>
                  @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
                </select>
              </div>
              <div class="field">
                <label>Default SAIComex %</label>
                <input class="input" type="number" step="0.01" [(ngModel)]="model.defaultSaicomexPercent" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label>Default partner %</label>
                <input class="input" type="number" step="0.01" [(ngModel)]="model.defaultPartnerPercent" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label>Rounding scale</label>
                <input class="input" type="number" [(ngModel)]="model.roundingScale" [disabled]="readOnly()">
              </div>
              <div class="field">
                <label>Rounding mode</label>
                <select class="select" [(ngModel)]="model.roundingMode" [disabled]="readOnly()">
                  @for (m of roundingModes; track m) { <option [value]="m">{{ m }}</option> }
                </select>
              </div>
              <div class="field" style="grid-column: 1 / -1">
                <label>Notes</label>
                <textarea class="textarea" [(ngModel)]="model.notes" [disabled]="readOnly()"></textarea>
              </div>
            </div>
            @if (defaultPercentWarning(); as w) {
              <div class="field-error" style="margin-top:8px">{{ w }}</div>
            }
          </div>

          <div class="builder-cols">
            <div class="stack">
              <div class="card">
                <div class="card-header">
                  <div>
                    <div class="card-title">Rules</div>
                    <div class="card-sub">Applied in sequence order within each stage</div>
                  </div>
                  @if (!readOnly()) {
                    <button class="btn btn-secondary btn-sm" type="button" (click)="addRule()"> Add rule
                    </button>
                  }
                </div>

                @if (model.rules.length === 0) {
                  <p class="muted">No rules yet. Add a rule, or set the default split above.</p>
                }

                <div class="stack">
                  @for (rule of model.rules; track $index; let i = $index) {
                    <div class="rule-card">
                      <div class="rule-card-head">
                        <span class="muted mono">#{{ rule.sequenceNo }}</span>
                        <div class="field" style="flex:1">
                          <label>Rule type</label>
                          <select class="select" [(ngModel)]="rule.ruleType" (change)="onRuleTypeChange(rule)" [disabled]="readOnly()">
                            <option value="">Select rule type…</option>
                            @for (g of ruleTypeGroups(); track g.stage) {
                              <optgroup [label]="stageLabel(g.stage)">
                                @for (t of g.types; track t.code) { <option [value]="t.code">{{ t.name }}</option> }
                              </optgroup>
                            }
                          </select>
                        </div>
                        @if (!readOnly()) {
                          <div class="row" style="gap:2px">
                            <button class="btn btn-ghost btn-sm" type="button" [disabled]="i === 0" (click)="moveRule(i, -1)" title="Move up">
                            </button>
                            <button class="btn btn-ghost btn-sm" type="button" [disabled]="i === model.rules.length - 1" (click)="moveRule(i, 1)" title="Move down">
                            </button>
                            <button class="btn btn-ghost btn-sm" type="button" (click)="removeRule(i)" title="Delete rule">
                            </button>
                          </div>
                        }
                      </div>

                      <div class="form-grid">
                        <div class="field"><label>Name</label><input class="input" [(ngModel)]="rule.name" [disabled]="readOnly()"></div>
                        <div class="field"><label>Sequence no.</label><input class="input" type="number" [(ngModel)]="rule.sequenceNo" [disabled]="readOnly()"></div>
                        <div class="field">
                          <label>Calculation method</label>
                          <select class="select" [(ngModel)]="rule.calculationMethod" [disabled]="readOnly()">
                            <option value="PERCENTAGE">Percentage</option>
                            <option value="FIXED_AMOUNT">Fixed amount</option>
                            <option value="RATE_PER_UNIT">Rate per unit</option>
                            <option value="TIERED">Tiered</option>
                            <option value="FULL_AMOUNT">Full amount</option>
                          </select>
                        </div>

                        @if (rule.calculationMethod === 'PERCENTAGE') {
                          <div class="field"><label>SAIComex %</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.saicomexPercent" [disabled]="readOnly()"></div>
                          <div class="field"><label>Partner %</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.partnerPercent" [disabled]="readOnly()"></div>
                        }
                        @if (rule.calculationMethod === 'FIXED_AMOUNT') {
                          <div class="field"><label>Fixed amount</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.fixedAmount" [disabled]="readOnly()"></div>
                        }
                        @if (rule.calculationMethod === 'RATE_PER_UNIT') {
                          <div class="field"><label>Rate amount</label><input class="input" type="number" step="0.0001" [(ngModel)]="rule.rateAmount" [disabled]="readOnly()"></div>
                          <div class="field"><label>Rate unit</label><input class="input" [(ngModel)]="rule.rateUnit" [disabled]="readOnly()"></div>
                        }

                        <div class="field">
                          <label>Borne by</label>
                          <select class="select" [(ngModel)]="rule.borneBy" [disabled]="readOnly()">
                            <option value="SAICOMEX">SAIComex</option>
                            <option value="PARTNER">Partner</option>
                            <option value="SHARED">Shared</option>
                          </select>
                        </div>
                        <div class="field">
                          <label>Scope</label>
                          <select class="select" [(ngModel)]="rule.scope" [disabled]="readOnly()">
                            <option value="ALL">All</option>
                            <option value="EXPENSE_CATEGORY">Expense category</option>
                          </select>
                        </div>
                        @if (rule.scope === 'EXPENSE_CATEGORY') {
                          <div class="field">
                            <label>Category</label>
                            <select class="select" [(ngModel)]="rule.scopeValue" [disabled]="readOnly()">
                              <option value="">Select category…</option>
                              @for (cat of reference.data()?.expenseCategories; track cat.id) { <option [value]="cat.code">{{ cat.name }}</option> }
                            </select>
                          </div>
                        }

                        <div class="field"><label>Min amount</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.minAmount" [disabled]="readOnly()"></div>
                        <div class="field"><label>Max amount</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.maxAmount" [disabled]="readOnly()"></div>
                        <div class="field"><label>Cap %</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.capPercent" [disabled]="readOnly()"></div>
                        <div class="field"><label>Recoverable total</label><input class="input" type="number" step="0.01" [(ngModel)]="rule.recoverableTotal" [disabled]="readOnly()"></div>
                        <div class="field"><label>Effective from</label><input class="input" type="date" [(ngModel)]="rule.effectiveFrom" [disabled]="readOnly()"></div>
                        <div class="field"><label>Effective to</label><input class="input" type="date" [(ngModel)]="rule.effectiveTo" [disabled]="readOnly()"></div>

                        <div class="field checkbox-field">
                          <input type="checkbox" [(ngModel)]="rule.deductBeforeSplit" [disabled]="readOnly()">
                          <label class="checkbox-label">Deduct before split</label>
                        </div>
                        <div class="field checkbox-field">
                          <input type="checkbox" [(ngModel)]="rule.isActive" [disabled]="readOnly()">
                          <label class="checkbox-label">Active</label>
                        </div>
                      </div>

                      <p class="muted deduct-note">
                        @if (rule.deductBeforeSplit) {
                          This cost comes off gross revenue before the split, so both parties bear it in the allocation ratio.
                        } @else {
                          This cost is charged after the split, to whoever "borne by" names — use this when a cost is shared on different terms from the revenue.
                        }
                      </p>

                      <div class="field" style="margin-top:8px"><label>Notes</label><input class="input" [(ngModel)]="rule.notes" [disabled]="readOnly()"></div>

                      @if (rule.calculationMethod === 'TIERED') {
                        <div class="tier-block">
                          <div class="row" style="justify-content:space-between">
                            <strong style="font-size:12px">Tiers</strong>
                            @if (!readOnly()) {
                              <button class="btn btn-secondary btn-sm" type="button" (click)="addTier(rule)">Add tier</button>
                            }
                          </div>
                          <div class="table-wrap">
                            <table class="data">
                              <thead>
                                <tr>
                                  <th class="num">#</th><th>From</th><th>To (blank = open-ended)</th>
                                  <th>SAIComex %</th><th>Partner %</th><th>Fixed</th><th>Rate</th>
                                  @if (!readOnly()) { <th></th> }
                                </tr>
                              </thead>
                              <tbody>
                                @for (tier of rule.tiers ?? []; track $index; let ti = $index) {
                                  <tr>
                                    <td class="num">{{ tier.tierNo }}</td>
                                    <td><input class="input" type="number" step="0.01" [(ngModel)]="tier.fromValue" [disabled]="readOnly()"></td>
                                    <td><input class="input" type="number" step="0.01" [(ngModel)]="tier.toValue" [disabled]="readOnly()"></td>
                                    <td><input class="input" type="number" step="0.01" [(ngModel)]="tier.saicomexPercent" [disabled]="readOnly()"></td>
                                    <td><input class="input" type="number" step="0.01" [(ngModel)]="tier.partnerPercent" [disabled]="readOnly()"></td>
                                    <td><input class="input" type="number" step="0.01" [(ngModel)]="tier.fixedAmount" [disabled]="readOnly()"></td>
                                    <td><input class="input" type="number" step="0.0001" [(ngModel)]="tier.rateAmount" [disabled]="readOnly()"></td>
                                    @if (!readOnly()) {
                                      <td><button class="btn btn-ghost btn-sm" type="button" (click)="removeTier(rule, ti)"></button></td>
                                    }
                                  </tr>
                                } @empty {
                                  <tr><td class="empty" [attr.colspan]="readOnly() ? 7 : 8">No tiers yet — add at least one, with an open-ended top tier.</td></tr>
                                }
                              </tbody>
                            </table>
                          </div>
                        </div>
                      }
                    </div>
                  }
                </div>
              </div>
            </div>

            <div class="card waterfall-card">
              <div class="card-header">
                <div>
                  <div class="card-title">Waterfall preview</div>
                  <div class="card-sub">Order the engine applies rules in</div>
                </div>
              </div>
              @for (g of waterfallStages(); track g.stage) {
                <div class="waterfall-stage">
                  <div class="waterfall-stage-title">{{ stageLabel(g.stage) }}</div>
                  @for (r of g.rules; track r.sequenceNo) {
                    <div class="waterfall-rule">
                      <span class="mono muted">{{ r.sequenceNo }}</span>
                      <span>{{ r.name || r.ruleType }}</span>
                    </div>
                  }
                </div>
              } @empty {
                <p class="muted">Add rules to see the settlement order here.</p>
              }
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .builder-cols { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; align-items: flex-start; }
    @media (max-width: 980px) { .builder-cols { grid-template-columns: 1fr; } }
    .waterfall-card { position: sticky; top: 16px; }
    .rule-card { border: 1px solid var(--line); border-radius: var(--radius-btn); padding: 12px 14px; background: var(--line-soft); }
    .rule-card-head { display: flex; align-items: flex-end; gap: 12px; margin-bottom: 10px; }
    .checkbox-field { flex-direction: row; align-items: center; gap: 8px; }
    .checkbox-label { text-transform: none; font-size: 12.5px; font-weight: 600; color: var(--ink); }
    .deduct-note { margin: 8px 0 0; font-size: 11.5px; }
    .tier-block { margin-top: 12px; padding-top: 12px; border-top: 1px dashed var(--line); }
    .waterfall-stage { margin-bottom: 14px; }
    .waterfall-stage-title { font-size: 10.5px; font-weight: 700; letter-spacing: .05em; text-transform: uppercase; color: var(--mut); margin-bottom: 6px; }
    .waterfall-rule { display: flex; gap: 8px; padding: 5px 0; font-size: 12.5px; border-bottom: 1px solid var(--line-soft); }
    .waterfall-rule:last-child { border-bottom: 0; }
    .validation-banner { flex-direction: column; align-items: flex-start; margin-bottom: 16px; }
    .validation-banner ul { margin: 6px 0 0 18px; padding: 0; }
  `],
})
export class AgreementBuilderPage implements OnInit {

  readonly contractId = input<string>();
  readonly id = input<string>();

  private readonly agreementApi = inject(AgreementApi);
  private readonly contractApi = inject(ContractApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);
  protected readonly router = inject(Router);

  protected readonly roundingModes = ROUNDING_MODES;
  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly activating = signal(false);
  protected readonly ruleTypes = signal<AgreementRuleTypeDto[]>([]);
  protected readonly contract = signal<ContractDetail | null>(null);

  protected model: AgreementFormModel = defaultModel();

  ngOnInit(): void {
    this.reference.ensureLoaded();
    this.agreementApi.ruleTypes().subscribe({ next: t => this.ruleTypes.set(t), error: () => {} });

    const existingId = this.id();
    if (existingId) {
      this.agreementApi.get(Number(existingId)).subscribe({
        next: a => { this.model = toModel(a); this.loading.set(false); },
        error: () => this.loading.set(false),
      });
    } else {
      const cId = this.contractId();
      if (cId) {
        this.contractApi.get(Number(cId)).subscribe({
          next: c => {
            this.contract.set(c);
            this.model = defaultModel(Number(cId));
            this.model.name = c.contractNumber + ' commercial agreement';
            this.model.currency = c.settlementCurrency ?? '';
            this.loading.set(false);
          },
          error: () => this.loading.set(false),
        });
      } else {
        this.loading.set(false);
      }
    }
  }

  protected isNew(): boolean { return !this.id(); }
  protected readOnly(): boolean { return this.model.status === 'ACTIVE'; }

  protected linkedContractId(): number | null {
    return this.model.contractId ?? (this.contractId() ? Number(this.contractId()) : null);
  }

  protected contractSummaryLine(): string {
    const c = this.contract();
    if (c) return [c.contractNumber, c.shaftName, c.partnerName].filter(Boolean).join(' · ');
    return [this.model.contractNumber, this.model.shaftName, this.model.partnerName].filter(Boolean).join(' · ') || '—';
  }

  protected stageLabel(stage: string): string {
    switch (stage) {
      case 'DEDUCTION': return 'Deductions';
      case 'ALLOCATION': return 'Allocation';
      case 'ADJUSTMENT': return 'Adjustments';
      default: return stage;
    }
  }

  protected ruleTypeGroups(): { stage: string; types: AgreementRuleTypeDto[] }[] {
    const stages = [...new Set([...STAGE_ORDER, ...this.ruleTypes().map(t => t.stage)])];
    return stages
      .map(stage => ({ stage, types: this.ruleTypes().filter(t => t.stage === stage) }))
      .filter(g => g.types.length > 0);
  }

  protected stageOf(ruleType: string): string {
    return this.ruleTypes().find(t => t.code === ruleType)?.stage ?? 'DEDUCTION';
  }

  protected waterfallStages(): { stage: string; rules: AgreementRuleDto[] }[] {
    return STAGE_ORDER
      .map(stage => ({
        stage,
        rules: this.model.rules
          .filter(r => this.stageOf(r.ruleType) === stage)
          .slice()
          .sort((a, b) => a.sequenceNo - b.sequenceNo),
      }))
      .filter(g => g.rules.length > 0);
  }

  protected onRuleTypeChange(rule: AgreementRuleDto): void {
    if (rule.name) return;
    const type = this.ruleTypes().find(t => t.code === rule.ruleType);
    if (type) rule.name = type.name;
  }

  protected addRule(): void {
    this.model.rules.push({
      ruleType: '', name: '', sequenceNo: this.model.rules.length + 1, scope: 'ALL',
      calculationMethod: 'PERCENTAGE', borneBy: 'SHARED', deductBeforeSplit: false, isActive: true, tiers: [],
    });
  }

  protected removeRule(index: number): void {
    this.model.rules.splice(index, 1);
    this.renumberRules();
  }

  protected moveRule(index: number, direction: -1 | 1): void {
    const target = index + direction;
    if (target < 0 || target >= this.model.rules.length) return;
    const rules = this.model.rules;
    [rules[index], rules[target]] = [rules[target], rules[index]];
    this.renumberRules();
  }

  private renumberRules(): void {
    this.model.rules.forEach((r, i) => r.sequenceNo = i + 1);
  }

  protected addTier(rule: AgreementRuleDto): void {
    rule.tiers = rule.tiers ?? [];
    rule.tiers.push({ tierNo: rule.tiers.length + 1, fromValue: 0 });
  }

  protected removeTier(rule: AgreementRuleDto, index: number): void {
    rule.tiers = rule.tiers ?? [];
    rule.tiers.splice(index, 1);
    rule.tiers.forEach((t, i) => t.tierNo = i + 1);
  }

  protected defaultPercentWarning(): string | null {
    const s = this.model.defaultSaicomexPercent;
    const p = this.model.defaultPartnerPercent;
    if (s == null || p == null) return null;
    const total = Number(s) + Number(p);
    if (Math.abs(total - 100) > 0.0001) return `Default split totals ${total}%, not 100%.`;
    return null;
  }

  /**
   * Mirrors the server checks in CommercialAgreementService so the operator
   * sees the problem here, not as a 422 after Activate.
   */
  protected validationMessages(): string[] {
    const messages: string[] = [];

    for (const r of this.model.rules) {
      const label = r.name || r.ruleType || 'unnamed rule';
      if (r.calculationMethod === 'PERCENTAGE') {
        if (r.saicomexPercent == null || r.partnerPercent == null) {
          messages.push(`Rule "${label}" is Percentage but its SAIComex and partner percentages are not both set.`);
        } else if (Math.abs(Number(r.saicomexPercent) + Number(r.partnerPercent) - 100) > 0.0001) {
          messages.push(`Rule "${label}" percentages total ${Number(r.saicomexPercent) + Number(r.partnerPercent)}%, not 100%.`);
        }
      }
      if (r.calculationMethod === 'TIERED') {
        const tiers = r.tiers ?? [];
        if (tiers.length === 0) {
          messages.push(`Rule "${label}" is Tiered but has no tiers defined.`);
        } else {
          const top = [...tiers].sort((a, b) => a.tierNo - b.tierNo).at(-1)!;
          if (top.toValue !== null && top.toValue !== undefined) {
            messages.push(`Rule "${label}" needs an open-ended top tier — leave "to" blank on the last tier.`);
          }
        }
      }
    }

    const hasAllocationRule = this.model.rules.some(r => ALLOCATION_TYPES.includes(r.ruleType));
    const hasDefaults = this.model.defaultSaicomexPercent != null && this.model.defaultPartnerPercent != null;
    if (!hasAllocationRule && !hasDefaults) {
      messages.push('Add an allocation rule (revenue/production/profit share) or set both default percentages — otherwise settlement has nothing to split the pool with.');
    }

    return messages;
  }

  protected save(): void {
    if (this.saving() || this.readOnly()) return;
    if (!this.model.name || !this.model.effectiveFrom || !this.model.currency) {
      this.toast.error('Name, effective-from date and currency are required.');
      return;
    }
    this.saving.set(true);
    const body = { ...this.model, contractId: this.linkedContractId() };
    const req = this.isNew() ? this.agreementApi.create(body) : this.agreementApi.update(Number(this.id()), body);
    req.subscribe({
      next: a => {
        this.saving.set(false);
        this.toast.success('Agreement saved');
        if (this.isNew()) this.router.navigateByUrl('/agreements/' + a.id);
        else this.model = toModel(a);
      },
      error: () => this.saving.set(false),
    });
  }

  protected activateAgreement(): void {
    if (this.activating() || !this.id()) return;
    this.activating.set(true);
    this.agreementApi.activate(Number(this.id())).subscribe({
      next: a => { this.activating.set(false); this.model = toModel(a); this.toast.success('Agreement activated'); },
      error: () => this.activating.set(false),
    });
  }
}

function defaultModel(contractId?: number): AgreementFormModel {
  return {
    contractId, name: '', description: '', status: 'DRAFT', settlementBasis: 'NET_REVENUE',
    effectiveFrom: '', effectiveTo: undefined, currency: '',
    defaultSaicomexPercent: undefined, defaultPartnerPercent: undefined,
    roundingScale: 2, roundingMode: 'HALF_UP', notes: '', rules: [],
  };
}

function toModel(a: AgreementDetail): AgreementFormModel {
  return {
    ...a,
    rules: (a.rules ?? []).map(r => ({ ...r, tiers: (r.tiers ?? []).map(t => ({ ...t })) })),
  };
}
