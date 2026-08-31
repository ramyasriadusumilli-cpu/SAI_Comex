---
name: Plan First (opusplan)
description: Plan-first workflow to pair with opusplan model mode — scope and design before touching code
---

## Plan First (opusplan)

Companion to the `opusplan` **model mode** (Opus plans, Sonnet executes — set via `/model`,
this is the operator's toggle). This skill owns the *behavior* that goes with that mode:
never jump straight to edits — scope, design, and get the plan approved first.

### When to use
- Any non-trivial change: a new module, a schema/migration change, a cross-cutting refactor.
- **Use deepest reasoning (ultrathink) when building a NEW module**, not for small fixes.
- Skip for one-line fixes and pure lookups.

### Steps
1. **Ground yourself before scoping.** Read the relevant `docs/` (ARCHITECTURE, STABILITY_RULES,
   SESSIONS, CHANGELOG), the latest Flyway migrations, and CLAUDE.md — don't rely on the context
   summary alone.
2. **Enter plan mode** (`EnterPlanMode`). Do not edit files while planning.
3. **Map impact.** Use the code-review-graph tools (`get_impact_radius`, `get_affected_flows`)
   and grep for callers before proposing changes.
4. **Draft the plan**: files to touch, new migration version (`MAX(version::int)+1`), API/entity
   changes, and any `_aud` column mirroring required for @Audited entities.
5. **Surface risks explicitly**: Envers/`_aud` columns, Flyway auto-apply on prod, FK to
   `vehicles(vehicle_id)`, shared prod/UAT frontend folder, POPIA files in tree.
6. **Get approval** (`ExitPlanMode`) before writing any code.

### Project guardrails to fold into every plan
- **Deploy order:** local → prod → UAT. Never extrapolate a local data fix to prod/UAT.
- **Migrations:** Flyway auto-applies on restart; `REFERENCES vehicles(vehicle_id)`, not `id`.
- **Audited tables:** ADD COLUMN on Vehicle/Rental/Customer/Financial must also hit the `_aud` table.
- **No `git add -A`** — POPIA CSVs live in the working tree.
- **Insurance module stays UAT-only** until the operator says otherwise.
