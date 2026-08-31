# The Commercial Calculation Engine

This is the most important document in this repository. Every statement
below is true of `CommercialCalculationEngine.java` and
`SettlementService.java` as written — if you change the engine, update this
doc in the same commit, and if this doc and the code ever disagree, the
code is describing what actually runs and this doc is wrong.

## The requirement it exists to satisfy

SRS §60, quoted in the engine's class comment:

> "No hard-coded business rule should be introduced where the business
> requirement may vary by project, shaft or contract."

Consequently **there is no percentage, no expense category name, and no
ordering decision written into `CommercialCalculationEngine.java`.**
Everything the engine uses — every split, every deduction, every fee,
every cap, every floor — is a row in `agreement_rules`, resolved and
handed to the engine by `SettlementService` before it ever runs. The
engine itself takes no repositories and knows nothing about the database;
it is a pure function from `CalculationInput` to `CalculationResult`,
which is what lets `CommercialCalculationEngineTest` exercise it directly,
with no Spring context and no database.

The one thing that *is* fixed in code is the **shape** of the waterfall,
because SRS §12 fixes it:

```
Gross revenue
  − deductions (rules with stage DEDUCTION, in sequence_no order)
= net distributable
  × contractual allocation (stage ALLOCATION)
= SAIComex share + partner share
  ± adjustments (stage ADJUSTMENT)
= partner net payable
```

## Which agreement governs a settlement

`SettlementService.resolve()` looks up the contract `ACTIVE` on the
settlement's `periodEnd` (`ContractRepository.findActiveOn`), then the
commercial agreement effective on that same date
(`CommercialAgreementRepository.findEffectiveOn`), then the agreement's
rules effective on that date (`AgreementRuleRepository.findEffectiveOn`,
ordered by `sequence_no ASC`). It is deliberately **not** "the contract
active today" — a contract amended in October must never silently
re-price September. The resolved `agreement_id` and `contract_version_id`
are stored on the `settlements` row so a historical statement can always be
reproduced exactly.

## The rule model

Every `agreement_rules` row has a `rule_type` (from `agreement_rule_types`,
which fixes its `stage`), a `calculation_method`, and — depending on the
method — percentages, a fixed amount, a rate, guard rails, and flags that
decide how it lands.

### Rule types, by stage

| Stage | Rule types | Handled by the engine? |
|---|---|---|
| `DEDUCTION` | `OPEX_SHARE`, `FUEL_COST_SHARE`, `EXPLOSIVE_COST_SHARE`, `LABOUR_COST_SHARE`, `EQUIPMENT_COST_SHARE`, `PROCESSING_COST_SHARE`, `TRANSPORT_COST_SHARE`, `CAPEX_SHARE` | Yes — cost-share rules, see below |
| `DEDUCTION` | `MANAGEMENT_FEE`, `CAPITAL_RECOVERY` | Yes — computed from the pool, credited to SAIComex |
| `DEDUCTION` | `SPECIAL_DEDUCTION` | Yes — computed from the pool, beneficiary left to the contract (not assumed) |
| `ALLOCATION` | `PRODUCTION_SHARE`, `REVENUE_SHARE`, `PROFIT_SHARE` | Yes — exactly one is picked, by `settlement_basis` |
| `ADJUSTMENT` | `MINIMUM_PAYMENT` | Yes — floor on the partner's final payable, applied last |
| `ADJUSTMENT` | `ADVANCE_RECOVERY`, `PENALTY` | Yes — recovered from the partner's post-allocation share |
| `ADJUSTMENT` | `SETTLEMENT_RULE` | **No.** Seeded in `agreement_rule_types` ("Timing, rounding and payment-condition rules") but the engine never reads this rule type. Adding a `SETTLEMENT_RULE` row to an agreement currently has no effect on a calculated settlement. Treat this as a documented gap, not a working feature. |

### Calculation methods

`calculation_method` decides how a non-allocation rule's amount is derived
(`computeAmount`):

| Method | Amount |
|---|---|
| `PERCENTAGE` | `base × saicomex_percent / 100` (the default if unset) |
| `FIXED_AMOUNT` | `fixed_amount`, verbatim |
| `RATE_PER_UNIT` | `rate_amount × total_production` for the period |
| `FULL_AMOUNT` | the entire `base` passed in |
| `TIERED` | (allocation rules only) the matching row in `agreement_rule_tiers` — see Tiers below |

### `borneBy` and `deductBeforeSplit` — what each choice means in money terms

A cost-share rule (`*_COST_SHARE`, `OPEX_SHARE`, `CAPEX_SHARE`) can land in
either of two places, chosen by `deduct_before_split`:

- **`deductBeforeSplit = true` — before the split.** The matched cost
  comes off gross revenue before the allocation percentages are applied.
  Both parties absorb it in whatever ratio the allocation rule sets,
  because it shrinks the pool the split is taken from. This is the SRS
  §25 worked example: `110,000 gross − 35,000 costs = 75,000
  distributable`, then split 70/30 — the partner's 30% is 30% of a pool
  that already excludes the cost.
- **`deductBeforeSplit = false` — after the split.** The pool is left
  untouched by this rule; instead, `borne_by` decides who pays and the
  amount lands as a **stage-`ADJUSTMENT`** entry against that party's
  share:
  - `borne_by = 'SAICOMEX'` — the whole matched amount is a SAIComex
    adjustment.
  - `borne_by = 'PARTNER'` — the whole matched amount is a partner
    adjustment.
  - `borne_by = 'SHARED'` with the rule's own `saicomex_percent` /
    `partner_percent` set — the matched amount is split by **those**
    percentages, which need not match the revenue split at all. This is
    what expresses "revenue is 70/30 but diesel is 50/50" — see the
    second worked example below.
  - `borne_by = 'SHARED'` with no percentages set on the rule — the
    engine refuses to guess a ratio for a rule that gave it none; the
    only correct way to "share it the way the revenue is shared" is to
    deduct it from the pool before the split, i.e. use
    `deductBeforeSplit = true` instead.

`MANAGEMENT_FEE` and `CAPITAL_RECOVERY` always come off the pool
(`pool = pool.subtract(amount)`) and are always credited to SAIComex —
that is fixed by the rule type, not configurable per row.
`SPECIAL_DEDUCTION` also always comes off the pool, but its beneficiary is
left unassumed: the engine does not credit it to anyone, because SRS gives
no basis for assuming one.

## The category-consumption rule, and why `sequence_no` matters

Cost-share rules run in `sequence_no` order (ascending — set by the
operator on `agreement_rules`, defaulted from
`agreement_rule_types.default_sequence`). Each rule **consumes** the
expense categories it matches (`applyCostRule` adds them to a `consumed`
set), and every later rule in the same calculation only sees categories no
earlier rule has already claimed (`categoriesFor` subtracts `consumed`).

This is deliberate, and it is the operator's lever: put a specific rule
(e.g. `FUEL_COST_SHARE`, matching `DIESEL`/`PETROL`/`OIL` by default)
ahead of a general one (`OPEX_SHARE`, which by default matches every OPEX
category) and the general rule sees only whatever is left. Get the
ordering backwards and the general rule claims fuel first, and the fuel
rule finds nothing left to act on. `CommercialCalculationEngineTest
.specificRuleConsumesCategoriesFirst` exercises exactly this.

An explicitly-scoped rule (`scope = 'EXPENSE_CATEGORY'`, naming one
category in `scope_value`) is the one exception: it takes its named
category even if an earlier rule already consumed it, because the
operator asked for that category by name.

Any expense category with a nonzero cost that **no** rule ends up
claiming is not silently deducted and not silently dropped — it is
reported in `CalculationResult.warnings()`:

> "No agreement rule covers 8,000.00 of expenditure in [SECURITY]. It has
> NOT been deducted. Add a cost-share rule, or confirm SAIComex absorbs it
> outside this settlement."

Silently deducting an uncovered cost and silently ignoring it are, per the
engine's own doc comment, "the two ways a settlement quietly goes wrong" —
so the engine does neither.

## The rounding rule

Once `net_distributable` is known, SAIComex's share is computed by
percentage and rounded (`percentOf`, using the agreement's
`rounding_scale` / `rounding_mode`, default `2` / `HALF_UP`). The
**partner's share is then computed as the remainder** —
`netDistributable.subtract(saicomexShare)` — not as its own independently
rounded percentage.

This matters: rounding both sides independently can lose or invent a cent
whenever the split percentage does not divide the pool cleanly (e.g.
33.333333%/66.666667% of 100,000.01). Whichever side is computed second
takes the remainder, so the two shares always reconstitute the pool
exactly —
`CommercialCalculationEngineTest.roundingNeverLosesACent` asserts this
directly. In this engine SAIComex's share is always computed first and the
partner always takes the remainder, so in practice the partner absorbs the
rounding — never SAIComex, and never a rule that leaves an unowned cent
for someone to chase down at month end.

## Guard rails

Applied by `applyBounds`, in this order, to any non-cost-share deduction
or adjustment amount:

1. **`cap_percent`** — the amount is capped at this percentage of its
   base.
2. **`min_amount` / `max_amount`** — the amount is floored/ceilinged to
   these absolute values.
3. **Capital recovery ceiling** — for a `CAPITAL_RECOVERY` rule with
   `recoverable_total` set, the amount is capped at
   `recoverable_total − recovered_to_date` (floored at zero). Without
   this, a recovery rule would keep deducting for the life of the
   contract instead of stopping once the advance is repaid
   (`CommercialCalculationEngineTest.capitalRecoveryStopsAtRemainingBalance`).
4. **Never exceed the base** — if `base` is positive, the result can never
   exceed it. A fee or deduction that would push the pool negative on its
   own (as opposed to real costs doing so) is treated as a bug, not a
   valid settlement.
5. **Never negative** — the final result is floored at zero.

**`MINIMUM_PAYMENT`** is applied last, after every other adjustment, as a
floor on `partner_net_payable`: if the partner's computed payable is below
the rule's fixed amount, SAIComex's share absorbs the shortfall (moved
from `saicomexAdjustments` to `partnerAdjustments`) and a warning is
recorded — the shortfall is visible, never silent.

## What the engine refuses to do, and why

The engine throws `BusinessRuleException` (a 4xx at the API boundary, not
a defaulted guess) rather than proceed when:

- **No allocation rule and no default split exist on the agreement** —
  "The commercial agreement has no allocation rule and no default split.
  Add a REVENUE_SHARE (or PRODUCTION_SHARE / PROFIT_SHARE) rule, or set
  the default percentages." An agreement with nothing to split by is not
  a valid agreement to settle against.
- **The resolved split does not sum to exactly 100%** — "The allocation
  split is X%, not 100%. Correct the agreement before calculating a
  settlement." A partial or over-full split is a data error, not a
  10%-to-SAIComex-by-default decision.
- **A `TIERED` allocation rule has no tier covering the period's value** —
  "Rule '…' is tiered but no tier covers the period's value. Add an
  open-ended top tier to the agreement." A tier table with a gap is
  refused rather than falling through to an implicit 0/0 split.

It does **not** refuse — it warns and proceeds — when:

- Expenditure exists in a category no rule claims (see above).
- The distributable pool is negative (costs exceeded revenue for the
  period) — the settlement is still produced, with a warning that the
  shortfall needs a decision before approval, because refusing to produce
  a statement at all would hide the number the business most needs to see.

## How the audit trail is produced

`CommercialCalculationEngine.calculate()` appends one `CalculationStep`
per action, in execution order, starting at `stepNo = 1`. Every step
carries a human-readable `expression` (e.g. `"75,000.00 × 70.000000% =
52,500.00"`), the `stage` (`TOTAL` | `DEDUCTION` | `ALLOCATION` |
`ADJUSTMENT`), the `ruleId`/`ruleType`/`ruleName` that produced it (`null`
for the fixed TOTAL steps — gross revenue, net distributable, the two
closing totals), the input amount, percentage/rate applied, the result,
the running pool balance, and a `beneficiary`
(`SAICOMEX`/`PARTNER`/`NONE`).

`SettlementService.persistSteps()` writes each `CalculationStep` verbatim
into `settlement_calculations` — same step number, same stage, same rule
reference, same expression. Nothing in a partner statement is a number
without a parent row explaining exactly how it was produced; reading
`settlement_calculations` top to bottom for a given `settlement_id` *is*
the explanation.

`SettlementService` also computes a SHA-256 `calculation_hash` over the
inputs a settlement consumed (agreement id, period, gross revenue, costs
by category, total production, final payable). Recomputing later against
changed inputs produces a different hash — how a statement that has gone
stale relative to the underlying ledger is detected, rather than quietly
disagreeing with it.

## Worked example 1 — SRS §25

110,000 gross, 35,000 in costs (20,000 labour + 15,000 diesel) deducted
before the split via a single `OPEX_SHARE` rule (`deductBeforeSplit =
true`), then a `REVENUE_SHARE` rule splits 70% SAIComex / 30% partner.
This is `CommercialCalculationEngineTest.srsWorkedExample`.

| Step | Stage | Rule | Expression | Result |
|---|---|---|---|---|
| 1 | TOTAL | — | Confirmed sales for the period | 110,000.00 |
| 2 | DEDUCTION | OPEX_SHARE ("Operating costs") | 110,000.00 pool less 35,000.00 of Operating costs | 75,000.00 |
| 3 | TOTAL | — | 110,000.00 − 35,000.00 | Net distributable: 75,000.00 |
| 4 | ALLOCATION | REVENUE_SHARE | 75,000.00 × 70% | SAIComex share: 52,500.00 |
| 5 | ALLOCATION | REVENUE_SHARE | 75,000.00 − 52,500.00 (remainder) | Partner share: 22,500.00 (30%) |
| 6 | TOTAL | — | 22,500.00 share + 0.00 adjustments | Partner net payable: 22,500.00 |
| 7 | TOTAL | — | 52,500.00 share + 0.00 adjustments | SAIComex net position: 52,500.00 |

No warnings. `totalDeductions = 35,000.00`, `netDistributable =
75,000.00`, `saicomexShare = 52,500.00`, `partnerShare = 22,500.00`,
`partnerNetPayable = 22,500.00` — asserted directly in the test.

## Worked example 2 — a cost shared on different terms from the revenue

Revenue splits 70/30, but diesel is shared 50/50 — which a single global
percentage cannot express. 100,000 gross; 20,000 diesel, 10,000 labour.
Rule order: `FUEL_COST_SHARE` (sequence 10, `deductBeforeSplit = false`,
`borneBy = SHARED`, 50%/50%), then `OPEX_SHARE` (sequence 20,
`deductBeforeSplit = true`) picks up whatever the fuel rule left — labour
only, since diesel was already consumed — then `REVENUE_SHARE` 70/30. This
is `CommercialCalculationEngineTest.costSharedDifferentlyFromRevenue`.

| Step | Stage | Rule | Expression | Result |
|---|---|---|---|---|
| 1 | TOTAL | — | Confirmed sales for the period | 100,000.00 |
| 2 | ADJUSTMENT | FUEL_COST_SHARE ("Diesel 50/50") | SAIComex bears 10,000.00 of 20,000.00 of Diesel 50/50 | SAIComex adjustment: −10,000.00 |
| 3 | ADJUSTMENT | FUEL_COST_SHARE | Partner bears 10,000.00 of 20,000.00 of Diesel 50/50 | Partner adjustment: −10,000.00 |
| 4 | DEDUCTION | OPEX_SHARE ("All operating costs") | 100,000.00 pool less 10,000.00 of All operating costs (diesel already consumed) | 90,000.00 |
| 5 | TOTAL | — | 100,000.00 − 10,000.00 | Net distributable: 90,000.00 |
| 6 | ALLOCATION | REVENUE_SHARE | 90,000.00 × 70% | SAIComex share: 63,000.00 |
| 7 | ALLOCATION | REVENUE_SHARE | 90,000.00 − 63,000.00 (remainder) | Partner share: 27,000.00 (30%) |
| 8 | TOTAL | — | 27,000.00 share − 10,000.00 adjustments | Partner net payable: 17,000.00 |
| 9 | TOTAL | — | 63,000.00 share − 10,000.00 adjustments | SAIComex net position: 53,000.00 |

Diesel never touches the revenue pool at all — it is deducted from each
side's *share* after the 70/30 split has already happened, at its own
50/50 ratio. `partnerAdjustments = −10,000.00`, `partnerNetPayable =
17,000.00` — asserted directly in the test.

## Tiers

A `TIERED` allocation rule picks the row in `agreement_rule_tiers` whose
`[from_value, to_value]` band contains the relevant value —
`net_distributable` for `NET_REVENUE`/`GROSS_REVENUE`/`PROFIT` bases, or
`total_production` for a `PRODUCTION` basis (`resolveTier`,
`basisValueForTier`). The matched tier's own `saicomex_percent` /
`partner_percent` are used for that period's allocation, and the step's
rule name is annotated with the tier number
(`"Tiered split (tier 2)"`) so the audit trail shows which band applied.

## Test coverage

`saicomex-api/src/test/java/com/saicomex/engine/CommercialCalculationEngineTest.java`
runs the engine directly (no Spring context, no database) and covers: the
SRS §25 worked example; the same engine producing different splits for
different contracts with no code change; a cost shared on different terms
from the revenue; a specific rule consuming its category before a general
rule sees it; unclaimed expenditure producing a warning rather than a
silent deduction or drop; a management fee retained by SAIComex; capital
recovery stopping at the remaining balance; the minimum-payment floor;
tiered splits; a missing split being refused; a split not summing to 100%
being refused; rounding never losing a cent; the agreement's default
split being used when no allocation rule exists; every waterfall step
being recorded in order; and a negative distributable pool producing a
warning rather than a silent negative payout.
