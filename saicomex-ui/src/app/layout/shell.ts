import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { Icon } from '../shared/icon';

import { AuthService } from '../core/services/auth.service';
import { ReferenceService } from '../core/services/domain.services';

interface NavItem {
  label: string;
  icon: string;
  route: string;
  permission: string;
}

interface NavGroup {
  label: string;
  items: NavItem[];
}

/**
 * SRS §44 — the application shell and main navigation.
 *
 * Nav items are filtered by permission, so a Storekeeper sees Inventory and
 * Fuel and simply does not see Settlements. That is presentation only; the
 * route guard and the API both enforce the same rule independently.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon],
  template: `
    <div class="shell" [class.nav-open]="navOpen()">
      <aside class="sidebar">
        <div class="brand">
          <span class="brand-mark">SC</span>
          <span class="brand-text">
            <strong>SAIComex</strong>
            <small>Mining Platform</small>
          </span>
        </div>

        <nav class="nav">
          @for (group of visibleGroups(); track group.label) {
            <div class="nav-group">
              <div class="nav-group-label">{{ group.label }}</div>
              @for (item of group.items; track item.route) {
                <a class="nav-item"
                   [routerLink]="item.route"
                   routerLinkActive="active"
                   (click)="navOpen.set(false)">
                  <app-icon [name]="item.icon" />
                  <span>{{ item.label }}</span>
                </a>
              }
            </div>
          }
        </nav>
      </aside>

      <div class="main">
        <header class="topbar">
          <button class="icon-btn nav-toggle" (click)="navOpen.set(!navOpen())" aria-label="Toggle navigation">
            <app-icon name="menu" [size]="18" />
          </button>
          <div class="company">{{ auth.user()?.companyName }}</div>
          <div class="spacer"></div>
          <div class="who">
            <div class="who-name">{{ auth.user()?.fullName }}</div>
            <div class="who-role">{{ auth.user()?.roleName }}</div>
          </div>
          <button class="icon-btn" (click)="auth.logout()" title="Sign out" aria-label="Sign out">
            <app-icon name="logout" [size]="18" />
          </button>
        </header>

        <main class="content">
          <router-outlet />
        </main>
      </div>

      @if (navOpen()) {
        <div class="scrim" (click)="navOpen.set(false)"></div>
      }
    </div>
  `,
  styles: [`
    .shell { display: flex; min-height: 100vh; }

    .sidebar {
      width: 224px; flex: 0 0 224px;
      background: var(--ink); color: #C9CDD2;
      display: flex; flex-direction: column;
      position: sticky; top: 0; height: 100vh; overflow-y: auto;
    }
    .brand { display: flex; gap: 10px; align-items: center; padding: 16px 16px 14px; }
    .brand-mark {
      width: 30px; height: 30px; border-radius: 8px; flex: 0 0 30px;
      background: var(--brand); color: #fff;
      display: grid; place-items: center; font-weight: 800; font-size: 12px; letter-spacing: .02em;
    }
    .brand-text { display: flex; flex-direction: column; line-height: 1.2; }
    .brand-text strong { color: #fff; font-size: 14px; letter-spacing: -0.01em; }
    .brand-text small { color: #8A9098; font-size: 10.5px; }

    .nav { padding: 4px 8px 24px; }
    .nav-group { margin-bottom: 14px; }
    .nav-group-label {
      padding: 6px 10px; font-size: 9.5px; font-weight: 700;
      letter-spacing: .1em; text-transform: uppercase; color: #6C737B;
    }
    .nav-item {
      display: flex; align-items: center; gap: 10px;
      padding: 8px 10px; margin-bottom: 1px; border-radius: 7px;
      color: #C9CDD2; font-size: 12.5px; font-weight: 500; text-decoration: none;
      transition: background .13s, color .13s;
    }
    .nav-item:hover { background: rgba(255,255,255,.06); color: #fff; text-decoration: none; }
    .nav-item.active { background: var(--brand); color: #fff; font-weight: 600; }
    .nav-item app-icon { flex: 0 0 17px; opacity: .85; }
    .nav-item.active app-icon { opacity: 1; }

    .main { flex: 1; min-width: 0; display: flex; flex-direction: column; }
    .topbar {
      display: flex; align-items: center; gap: 12px;
      padding: 0 18px; height: 52px;
      background: var(--card); border-bottom: 1px solid var(--line);
      position: sticky; top: 0; z-index: 20;
    }
    .company { font-weight: 650; font-size: 13px; }
    .spacer { flex: 1; }
    .who { text-align: right; line-height: 1.25; }
    .who-name { font-size: 12.5px; font-weight: 600; }
    .who-role { font-size: 10.5px; color: var(--mut); }
    .icon-btn {
      display: grid; place-items: center; width: 32px; height: 32px;
      border: 0; border-radius: 8px; background: transparent; color: var(--ink-soft);
      cursor: pointer; transition: background .13s, color .13s;
    }
    .icon-btn:hover { background: var(--line-soft); color: var(--ink); }
    .nav-toggle { display: none; }
    .content { flex: 1; min-width: 0; }
    .scrim { display: none; }

    @media (max-width: 900px) {
      .sidebar {
        position: fixed; z-index: 60; left: 0; top: 0;
        transform: translateX(-100%); transition: transform .2s ease;
      }
      .nav-open .sidebar { transform: none; }
      .nav-toggle { display: grid; }
      .scrim {
        display: block; position: fixed; inset: 0; z-index: 50;
        background: rgba(24,26,28,.45);
      }
    }
  `],
})
export class Shell {

  protected readonly auth = inject(AuthService);
  private readonly reference = inject(ReferenceService);

  protected readonly navOpen = signal(false);

  private readonly groups: NavGroup[] = [
    {
      label: 'Overview',
      items: [
        { label: 'Dashboard', icon: 'dashboard', route: '/dashboard', permission: 'dashboard.view' },
        { label: 'Alerts', icon: 'alert', route: '/alerts', permission: 'alerts.view' },
      ],
    },
    {
      label: 'Operations',
      items: [
        { label: 'Projects', icon: 'folder', route: '/projects', permission: 'projects.view' },
        { label: 'Mining Operations', icon: 'tree', route: '/operations', permission: 'operations.view' },
        { label: 'Shafts', icon: 'shaft', route: '/shafts', permission: 'shafts.view' },
        { label: 'Production', icon: 'production', route: '/production', permission: 'production.view' },
        { label: 'Expenses', icon: 'receipt', route: '/expenses', permission: 'expenses.view' },
      ],
    },
    {
      label: 'Commercial',
      items: [
        { label: 'Partners', icon: 'handshake', route: '/partners', permission: 'partners.view' },
        { label: 'Contracts', icon: 'contract', route: '/contracts', permission: 'contracts.view' },
        { label: 'Revenue & Sales', icon: 'sell', route: '/sales', permission: 'sales.view' },
        { label: 'Settlements', icon: 'balance', route: '/settlements', permission: 'settlements.view' },
      ],
    },
    {
      label: 'Administration',
      items: [
        { label: 'Users', icon: 'group', route: '/users', permission: 'users.view' },
        { label: 'Roles & Permissions', icon: 'shield', route: '/roles', permission: 'roles.view' },
        { label: 'Audit Trail', icon: 'history', route: '/audit', permission: 'audit.view' },
        { label: 'Settings', icon: 'settings', route: '/settings', permission: 'settings.view' },
      ],
    },
  ];

  protected readonly visibleGroups = computed<NavGroup[]>(() =>
    this.groups
      .map(group => ({ ...group, items: group.items.filter(item => this.auth.has(item.permission)) }))
      .filter(group => group.items.length > 0));

  constructor() {
    this.reference.ensureLoaded();
    // Re-read the session on entry: a permission change made by an
    // administrator while this tab was open should take effect on navigation,
    // not only after the operator happens to sign in again.
    this.auth.refresh().subscribe({ error: () => {} });
  }
}
