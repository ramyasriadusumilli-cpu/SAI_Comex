import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PermissionDto, RoleDetail, RoleSummary } from '../../core/models/api.models';

interface ModuleGroup {
  module: string;
  permissions: PermissionDto[];
}

/**
 * SRS §37 — configurable roles and the permission matrix behind them.
 *
 * RoleService.java allows a system role's permission grants to be edited
 * freely; only its code (and its deletion) are locked. The matrix below is
 * therefore editable for every role, system or not — only the code field is
 * disabled for a system role.
 */
@Component({
  selector: 'app-role-list',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Roles &amp; permissions</h1>
          <div class="page-sub">Grant sets that control what each account can see and do</div>
        </div>
      </div>

      @if (loadingRoles()) {
        <div class="loading-block"><span class="spin"></span> Loading roles…</div>
      } @else {
        <div class="role-layout">
          <div class="card role-side">
            <div class="card-header"><div class="card-title">Roles</div></div>
            <div class="table-wrap">
              <table class="data">
                <thead><tr><th>Role</th><th class="num">Users</th><th class="num">Perms</th></tr></thead>
                <tbody>
                  @for (r of roles(); track r.id) {
                    <tr class="clickable" [class.selected]="selected()?.id === r.id" (click)="select(r)">
                      <td>
                        <div><strong>{{ r.name }}</strong> @if (r.isSystem) { <span class="pill pill-special">System</span> }</div>
                        <div class="muted mono" style="font-size:11px">{{ r.code }}</div>
                      </td>
                      <td class="num">{{ r.userCount ?? 0 }}</td>
                      <td class="num">{{ r.permissionCount ?? 0 }}</td>
                    </tr>
                  } @empty {
                    <tr><td class="empty" colspan="3">No roles defined.</td></tr>
                  }
                </tbody>
              </table>
            </div>
          </div>

          <div class="role-main">
            @if (loadingDetail()) {
              <div class="card"><div class="loading-block"><span class="spin"></span> Loading role…</div></div>
            } @else if (detail(); as d) {
              <div class="card" style="margin-bottom:14px">
                <div class="card-header">
                  <div class="card-title">{{ d.name }} @if (d.isSystem) { <span class="pill pill-special">System</span> }</div>
                  @if (auth.has('roles.edit')) {
                    <button class="btn" [disabled]="saving()" (click)="save()">
                      @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
                    </button>
                  }
                </div>
                <div class="form-grid">
                  <div class="field">
                    <label class="req" for="r-code">Code</label>
                    <input id="r-code" class="input" [(ngModel)]="editCode" [disabled]="d.isSystem">
                    @if (d.isSystem) { <span class="muted" style="font-size:11px">A system role's code cannot be changed.</span> }
                  </div>
                  <div class="field">
                    <label class="req" for="r-name">Name</label>
                    <input id="r-name" class="input" [(ngModel)]="editName">
                  </div>
                  <div class="field" style="grid-column: 1 / -1">
                    <label for="r-desc">Description</label>
                    <input id="r-desc" class="input" [(ngModel)]="editDescription">
                  </div>
                </div>
              </div>

              <div class="banner banner-warn" style="margin-bottom:14px">
                Permission changes take effect for every user holding this role on their next request.
              </div>

              <div class="card">
                <div class="card-header"><div class="card-title">Permission matrix</div></div>
                @for (group of catalogue(); track group.module) {
                  <div class="module-block">
                    <div class="module-head">
                      <label class="row" style="gap:8px; cursor:pointer">
                        <input type="checkbox" [checked]="allSelected(group)" (change)="toggleModule(group)">
                        <strong>{{ group.module }}</strong>
                      </label>
                    </div>
                    <div class="perm-grid">
                      @for (p of group.permissions; track p.code) {
                        <label class="perm-item">
                          <input type="checkbox" [checked]="selectedCodes().has(p.code)" (change)="togglePermission(p.code)">
                          <span>{{ p.action }}</span>
                          @if (p.description) { <span class="muted" style="font-size:11px">— {{ p.description }}</span> }
                        </label>
                      }
                    </div>
                  </div>
                }
              </div>
            }
          </div>
        </div>
      }
    </div>
  `,
  styles: [`
    .role-layout { display: flex; gap: 16px; align-items: flex-start; }
    .role-side { width: 300px; flex: 0 0 300px; padding: 0; }
    .role-side .card-header { padding: 16px 16px 0; margin-bottom: 10px; }
    .role-main { flex: 1; min-width: 0; }
    tr.selected { background: var(--brand-xl); }
    .module-block { padding: 12px 0; border-bottom: 1px solid var(--line-soft); }
    .module-block:last-child { border-bottom: none; }
    .module-head { margin-bottom: 8px; }
    .perm-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 6px 14px; padding-left: 22px; }
    .perm-item { display: flex; align-items: baseline; gap: 6px; font-size: 12.5px; cursor: pointer; }
    @media (max-width: 900px) { .role-layout { flex-direction: column; } .role-side { width: 100%; flex: none; } }
  `],
})
export class RoleListPage {

  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly loadingRoles = signal(true);
  protected readonly loadingDetail = signal(false);
  protected readonly saving = signal(false);

  protected readonly roles = signal<RoleSummary[]>([]);
  protected readonly catalogue = signal<ModuleGroup[]>([]);
  protected readonly selected = signal<RoleSummary | null>(null);
  protected readonly detail = signal<RoleDetail | null>(null);
  protected readonly selectedCodes = signal<Set<string>>(new Set());

  protected editCode = '';
  protected editName = '';
  protected editDescription = '';

  constructor() {
    this.api.permissionCatalogue().subscribe({ next: c => this.catalogue.set(c), error: () => {} });
    this.api.roles().subscribe({
      next: rs => {
        this.roles.set(rs);
        this.loadingRoles.set(false);
        if (rs.length) this.select(rs[0]);
      },
      error: () => this.loadingRoles.set(false),
    });
  }

  protected select(r: RoleSummary): void {
    this.selected.set(r);
    this.loadingDetail.set(true);
    this.api.role(r.id).subscribe({
      next: d => {
        this.detail.set(d);
        this.editCode = d.code;
        this.editName = d.name;
        this.editDescription = d.description ?? '';
        this.selectedCodes.set(new Set(d.permissionCodes));
        this.loadingDetail.set(false);
      },
      error: () => this.loadingDetail.set(false),
    });
  }

  protected allSelected(group: ModuleGroup): boolean {
    const codes = this.selectedCodes();
    return group.permissions.length > 0 && group.permissions.every(p => codes.has(p.code));
  }

  protected toggleModule(group: ModuleGroup): void {
    const turnOn = !this.allSelected(group);
    this.selectedCodes.update(codes => {
      const next = new Set(codes);
      for (const p of group.permissions) {
        if (turnOn) next.add(p.code); else next.delete(p.code);
      }
      return next;
    });
  }

  protected togglePermission(code: string): void {
    this.selectedCodes.update(codes => {
      const next = new Set(codes);
      if (next.has(code)) next.delete(code); else next.add(code);
      return next;
    });
  }

  protected save(): void {
    const d = this.detail();
    if (!d) return;
    if (!this.editCode.trim() || !this.editName.trim()) {
      this.toast.error('Code and name are required.');
      return;
    }
    this.saving.set(true);
    this.api.updateRole(d.id, {
      code: this.editCode.trim(),
      name: this.editName.trim(),
      description: this.editDescription || null,
      isActive: d.isActive,
      displayOrder: d.displayOrder ?? 0,
      permissionCodes: Array.from(this.selectedCodes()),
    }).subscribe({
      next: updated => {
        this.saving.set(false);
        this.toast.success('Role updated');
        this.detail.set(updated);
        this.selectedCodes.set(new Set(updated.permissionCodes));
        this.api.roles().subscribe({ next: rs => this.roles.set(rs), error: () => {} });
      },
      error: () => this.saving.set(false),
    });
  }
}
