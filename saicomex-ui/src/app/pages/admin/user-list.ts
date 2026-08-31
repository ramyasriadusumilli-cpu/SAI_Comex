import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi, ProjectApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { PageResponse, ProjectSummary, ShaftSummary, UserSummary } from '../../core/models/api.models';
import { DateTimePipe, StatusClassPipe, StatusLabelPipe } from '../../shared/format';

interface UserFormModel {
  id: number | null;
  email: string;
  firstName: string;
  lastName: string;
  phone: string;
  jobTitle: string;
  department: string;
  roleId: number | null;
  preferredCurrency: string;
  projectIds: number[];
  shaftIds: number[];
}

function blankModel(): UserFormModel {
  return {
    id: null, email: '', firstName: '', lastName: '', phone: '', jobTitle: '', department: '',
    roleId: null, preferredCurrency: '', projectIds: [], shaftIds: [],
  };
}

/** SRS §36 — platform users and their project/shaft data-scoping assignments. */
@Component({
  selector: 'app-user-list',
  imports: [FormsModule, DateTimePipe, StatusClassPipe, StatusLabelPipe],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>Users</h1>
          <div class="page-sub">Platform accounts and their access scope</div>
        </div>
        @if (auth.has('users.create')) {
          <button class="btn" (click)="startCreate()"> New user
          </button>
        }
      </div>

      @if (revealedPassword(); as pw) {
        <div class="banner banner-info" style="margin-bottom:16px">
          <div>
            <strong>Initial password: <span class="mono">{{ pw }}</span></strong>
            <div>This will not be shown again. Hand it to the user through a secure channel now.</div>
          </div>
        </div>
      }

      @if (formOpen()) {
        <div class="card" style="margin-bottom:16px">
          <div class="card-header"><div class="card-title">{{ form.id ? 'Edit user' : 'New user' }}</div></div>
          <div class="form-grid">
            <div class="field">
              <label class="req" for="f-email">Email</label>
              <input id="f-email" class="input" type="email" [(ngModel)]="form.email" [disabled]="!!form.id">
            </div>
            <div class="field">
              <label class="req" for="f-first">First name</label>
              <input id="f-first" class="input" [(ngModel)]="form.firstName">
            </div>
            <div class="field">
              <label class="req" for="f-last">Last name</label>
              <input id="f-last" class="input" [(ngModel)]="form.lastName">
            </div>
            <div class="field">
              <label for="f-phone">Phone</label>
              <input id="f-phone" class="input" [(ngModel)]="form.phone">
            </div>
            <div class="field">
              <label for="f-title">Job title</label>
              <input id="f-title" class="input" [(ngModel)]="form.jobTitle">
            </div>
            <div class="field">
              <label for="f-dept">Department</label>
              <input id="f-dept" class="input" [(ngModel)]="form.department">
            </div>
            <div class="field">
              <label class="req" for="f-role">Role</label>
              <select id="f-role" class="select" [(ngModel)]="form.roleId">
                <option [ngValue]="null">Select role…</option>
                @for (r of reference.data()?.roles; track r.id) { <option [ngValue]="r.id">{{ r.name }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-currency">Preferred currency</label>
              <select id="f-currency" class="select" [(ngModel)]="form.preferredCurrency">
                <option value="">—</option>
                @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }}</option> }
              </select>
            </div>
            <div class="field">
              <label for="f-projects">Assigned projects</label>
              <select id="f-projects" class="select" multiple style="min-height:96px" (change)="onProjectsSelect($event)">
                @for (p of projects(); track p.id) {
                  <option [value]="p.id" [selected]="form.projectIds.includes(p.id)">{{ p.name }}</option>
                }
              </select>
            </div>
            <div class="field">
              <label for="f-shafts">Assigned shafts</label>
              <select id="f-shafts" class="select" multiple style="min-height:96px" (change)="onShaftsSelect($event)">
                @for (s of shafts(); track s.id) {
                  <option [value]="s.id" [selected]="form.shaftIds.includes(s.id)">{{ s.name }}</option>
                }
              </select>
            </div>
          </div>
          <div class="row" style="margin-top:14px">
            <button class="btn" [disabled]="saving()" (click)="save()">
              @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
            </button>
            <button class="btn btn-secondary" (click)="formOpen.set(false)">Cancel</button>
          </div>
        </div>
      }

      <div class="toolbar">
        <input class="input" placeholder="Search email or name…" [(ngModel)]="search" (keyup.enter)="reload()" (change)="reload()">
        <select class="select" [(ngModel)]="status" (change)="reload()">
          <option value="">All statuses</option>
          <option value="ACTIVE">Active</option>
          <option value="SUSPENDED">Suspended</option>
          <option value="PENDING">Pending</option>
          <option value="DISABLED">Disabled</option>
        </select>
        <select class="select" [(ngModel)]="roleId" (change)="reload()">
          <option value="">All roles</option>
          @for (r of reference.data()?.roles; track r.id) { <option [value]="r.id">{{ r.name }}</option> }
        </select>
      </div>

      <div class="card">
        @if (loading()) {
          <div class="loading-block"><span class="spin"></span> Loading users…</div>
        } @else {
          <div class="table-wrap">
            <table class="data">
              <thead>
                <tr>
                  <th>Email</th><th>Full name</th><th>Role</th><th>Department</th><th>Status</th>
                  <th>Last login</th><th class="num">Projects</th><th class="num">Shafts</th><th></th>
                </tr>
              </thead>
              <tbody>
                @for (u of page()?.content ?? []; track u.id) {
                  <tr>
                    <td class="mono">{{ u.email }}</td>
                    <td><strong>{{ u.fullName }}</strong></td>
                    <td class="muted">{{ u.roleName }}</td>
                    <td class="muted">{{ u.department ?? '—' }}</td>
                    <td><span [class]="u.status | statusClass">{{ u.status | statusLabel }}</span></td>
                    <td class="muted nowrap">{{ u.lastLoginAt | dateTime }}</td>
                    <td class="num">{{ u.assignedProjectCount ?? 0 }}</td>
                    <td class="num">{{ u.assignedShaftCount ?? 0 }}</td>
                    <td>
                      <div class="row" style="flex-wrap:nowrap">
                        @if (auth.has('users.edit')) {
                          <button class="btn btn-ghost btn-sm" (click)="startEdit(u)">Edit</button>
                          @if (u.status === 'ACTIVE') {
                            <button class="btn btn-ghost btn-sm" [disabled]="busyId() === u.id" (click)="toggleStatus(u, 'SUSPENDED')">Suspend</button>
                          } @else {
                            <button class="btn btn-ghost btn-sm" [disabled]="busyId() === u.id" (click)="toggleStatus(u, 'ACTIVE')">Activate</button>
                          }
                          <button class="btn btn-ghost btn-sm" [disabled]="busyId() === u.id" (click)="resetPassword(u)">Reset password</button>
                        }
                      </div>
                    </td>
                  </tr>
                } @empty {
                  <tr><td class="empty" colspan="9">No users match these filters.</td></tr>
                }
              </tbody>
            </table>
          </div>

          @if (page(); as p) {
            <div class="row" style="margin-top:12px">
              <button class="btn btn-secondary btn-sm" [disabled]="p.first" (click)="setPage(pageNo() - 1)">Previous</button>
              <span class="muted">Page {{ p.page + 1 }} of {{ p.totalPages || 1 }} · {{ p.totalElements }} users</span>
              <button class="btn btn-secondary btn-sm" [disabled]="p.last" (click)="setPage(pageNo() + 1)">Next</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [],
})
export class UserListPage {

  private readonly api = inject(AdminApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly shaftApi = inject(ShaftApi);
  private readonly toast = inject(ToastService);
  protected readonly reference = inject(ReferenceService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly saving = signal(false);
  protected readonly busyId = signal<number | null>(null);
  protected readonly page = signal<PageResponse<UserSummary> | null>(null);
  protected readonly pageNo = signal(0);
  protected readonly formOpen = signal(false);
  protected readonly revealedPassword = signal<string | null>(null);

  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly shafts = signal<ShaftSummary[]>([]);

  protected search = '';
  protected status = '';
  protected roleId = '';

  protected form: UserFormModel = blankModel();

  constructor() {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.shaftApi.options().subscribe({ next: s => this.shafts.set(s), error: () => {} });
    this.reload();
  }

  protected onProjectsSelect(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.form.projectIds = Array.from(select.selectedOptions).map(o => Number(o.value));
  }

  protected onShaftsSelect(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.form.shaftIds = Array.from(select.selectedOptions).map(o => Number(o.value));
  }

  protected setPage(page: number): void {
    this.pageNo.set(Math.max(0, page));
    this.reload();
  }

  protected reload(): void {
    this.loading.set(true);
    this.api.users({
      status: this.status || undefined,
      roleId: this.roleId || undefined,
      search: this.search || undefined,
      page: this.pageNo(),
    }).subscribe({
      next: p => { this.page.set(p); this.loading.set(false); },
      error: () => this.loading.set(false),
    });
  }

  protected startCreate(): void {
    this.form = blankModel();
    this.revealedPassword.set(null);
    this.formOpen.set(true);
  }

  protected startEdit(u: UserSummary): void {
    this.revealedPassword.set(null);
    this.api.user(u.id).subscribe({
      next: detail => {
        this.form = {
          id: detail.id, email: detail.email, firstName: detail.firstName ?? '', lastName: detail.lastName ?? '',
          phone: detail.phone ?? '', jobTitle: detail.jobTitle ?? '', department: detail.department ?? '',
          roleId: detail.roleId ?? null, preferredCurrency: detail.preferredCurrency ?? '',
          projectIds: detail.projectIds ?? [], shaftIds: detail.shaftIds ?? [],
        };
        this.formOpen.set(true);
      },
      error: () => {},
    });
  }

  protected save(): void {
    if (!this.form.email || !this.form.firstName || !this.form.lastName || !this.form.roleId) {
      this.toast.error('Email, first name, last name and role are required.');
      return;
    }
    this.saving.set(true);
    const { id, ...body } = this.form;
    if (id) {
      this.api.updateUser(id, body).subscribe({
        next: () => {
          this.saving.set(false);
          this.toast.success('User updated');
          this.formOpen.set(false);
          this.reload();
        },
        error: () => this.saving.set(false),
      });
    } else {
      this.api.createUser(body).subscribe({
        next: result => {
          this.saving.set(false);
          this.toast.success('User created');
          this.formOpen.set(false);
          this.revealedPassword.set(result.initialPassword);
          this.reload();
        },
        error: () => this.saving.set(false),
      });
    }
  }

  protected toggleStatus(u: UserSummary, status: string): void {
    const reason = prompt(`Reason for ${status === 'ACTIVE' ? 'reactivating' : 'suspending'} ${u.fullName} (required):`);
    if (!reason) return;
    this.busyId.set(u.id);
    this.api.setUserStatus(u.id, status, reason).subscribe({
      next: () => {
        this.busyId.set(null);
        this.toast.success('User status updated');
        this.reload();
      },
      error: () => this.busyId.set(null),
    });
  }

  protected resetPassword(u: UserSummary): void {
    if (!confirm(`Reset the password for ${u.fullName}? A new temporary password will be generated.`)) return;
    this.busyId.set(u.id);
    this.revealedPassword.set(null);
    this.api.resetPassword(u.id).subscribe({
      next: result => {
        this.busyId.set(null);
        this.revealedPassword.set(result.initialPassword);
        this.toast.success('Password reset');
      },
      error: () => this.busyId.set(null),
    });
  }
}
