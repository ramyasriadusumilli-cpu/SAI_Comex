import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi } from '../../core/services/domain.services';
import { AuthService } from '../../core/services/auth.service';
import { ToastService } from '../../core/services/toast.service';
import { SystemConfigDto } from '../../core/models/api.models';

interface CategoryGroup {
  category: string;
  settings: SystemConfigDto[];
}

/** SRS §41 — system configuration values, grouped by category. */
@Component({
  selector: 'app-settings',
  imports: [FormsModule],
  template: `
    <div class="page">
      <div class="page-header">
        <div class="page-title-group">
          <h1>System settings</h1>
          <div class="page-sub">Platform-wide configuration</div>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading settings…</div>
      } @else {
        <div class="stack">
          @for (group of groups(); track group.category) {
            <div class="card">
              <div class="card-header"><div class="card-title">{{ group.category }}</div></div>
              <div class="stack" style="gap:10px">
                @for (s of group.settings; track s.key) {
                  <div class="setting-row">
                    <div class="setting-label">
                      <div><strong>{{ s.label ?? s.key }}</strong></div>
                      @if (s.description) { <div class="card-sub">{{ s.description }}</div> }
                    </div>
                    <div class="setting-input">
                      @switch (s.valueType) {
                        @case ('BOOLEAN') {
                          <select class="select" [(ngModel)]="edits[s.key]" [disabled]="!s.editable || !auth.has('settings.edit')">
                            <option value="true">True</option>
                            <option value="false">False</option>
                          </select>
                        }
                        @case ('NUMBER') {
                          <!-- text + inputmode, not type="number": the value round-trips through
                               the API as a string (SystemConfigDto.configValue), and NumberValueAccessor
                               would otherwise coerce edits[key] to a JS number. -->
                          <input class="input" type="text" inputmode="decimal" [(ngModel)]="edits[s.key]" [disabled]="!s.editable || !auth.has('settings.edit')">
                        }
                        @default {
                          <input class="input" [(ngModel)]="edits[s.key]" [disabled]="!s.editable || !auth.has('settings.edit')">
                        }
                      }
                    </div>
                    @if (auth.has('settings.edit')) {
                      <button class="btn btn-secondary btn-sm" [disabled]="!s.editable || savingKey() === s.key" (click)="save(s)">
                        @if (savingKey() === s.key) { <span class="spin"></span> } @else { Save }
                      </button>
                    }
                  </div>
                }
              </div>
            </div>
          } @empty {
            <div class="card"><div class="empty" style="text-align:center; padding:34px; color:var(--mut)">No settings configured.</div></div>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .setting-row { display: flex; align-items: center; gap: 14px; padding: 10px 0; border-bottom: 1px solid var(--line-soft); }
    .setting-row:last-child { border-bottom: none; }
    .setting-label { flex: 1; min-width: 0; }
    .setting-input { width: 220px; flex: 0 0 220px; }
    @media (max-width: 640px) {
      .setting-row { flex-wrap: wrap; }
      .setting-input { width: 100%; flex: 1 1 100%; }
    }
  `],
})
export class SettingsPage {

  private readonly api = inject(AdminApi);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  protected readonly loading = signal(true);
  protected readonly savingKey = signal<string | null>(null);
  protected readonly grouped = signal<Record<string, SystemConfigDto[]>>({});

  /** Plain object rather than a signal per row — every input binds directly with [(ngModel)]. */
  protected edits: Record<string, string> = {};

  // The API already groups by category, so this only fixes the display order.
  protected readonly groups = computed<CategoryGroup[]>(() =>
    Object.entries(this.grouped())
      .sort(([a], [b]) => a.localeCompare(b))
      .map(([category, settings]) => ({ category, settings })));

  constructor() {
    this.reload();
  }

  private reload(): void {
    this.loading.set(true);
    this.api.settings().subscribe({
      next: byCategory => {
        this.grouped.set(byCategory);
        this.edits = {};
        for (const list of Object.values(byCategory)) {
          for (const s of list) this.edits[s.key] = s.value ?? '';
        }
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  protected save(s: SystemConfigDto): void {
    this.savingKey.set(s.key);
    this.api.updateSetting(s.key, this.edits[s.key] ?? '').subscribe({
      next: updated => {
        this.savingKey.set(null);
        this.grouped.update(groups => ({
          ...groups,
          [updated.category]: (groups[updated.category] ?? []).map(x => x.key === updated.key ? updated : x),
        }));
        this.toast.success('Setting updated');
      },
      error: () => this.savingKey.set(null),
    });
  }
}
