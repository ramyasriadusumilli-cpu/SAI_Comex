import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { OperationApi, PartnerApi, ProjectApi, ReferenceService, ShaftApi } from '../../core/services/domain.services';
import { ToastService } from '../../core/services/toast.service';
import { OperationSummary, PartnerSummary, ProjectSummary } from '../../core/models/api.models';

interface ShaftFormModel {
  code: string;
  name: string;
  shaftNumber: string;
  description: string;
  projectId: number | null;
  miningOperationId: number | null;
  ownerPartnerId: number | null;
  shaftManagerId: number | null;
  latitude: number | null;
  longitude: number | null;
  depthMetres: number | null;
  commissionedDate: string;
  startDate: string;
  status: string;
  productionTarget: number | null;
  productionTargetUnit: string;
  productionTargetPeriod: string;
  notes: string;
}

function blankModel(): ShaftFormModel {
  return {
    code: '', name: '', shaftNumber: '', description: '',
    projectId: null, miningOperationId: null, ownerPartnerId: null, shaftManagerId: null,
    latitude: null, longitude: null,
    depthMetres: null, commissionedDate: '', startDate: '', status: 'PLANNING',
    productionTarget: null, productionTargetUnit: '', productionTargetPeriod: 'MONTHLY',
    notes: '',
  };
}

/** SRS §8 — create and edit form, serving both /shafts/new and /shafts/:id/edit. */
@Component({
  selector: 'app-shaft-form',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="crumbs">
        <a routerLink="/shafts">Shafts</a><span class="sep">›</span>
        <span>{{ isEdit() ? 'Edit shaft' : 'New shaft' }}</span>
      </div>

      <div class="page-header">
        <div class="page-title-group">
          <h1>{{ isEdit() ? 'Edit shaft' : 'New shaft' }}</h1>
          <div class="page-sub">{{ isEdit() ? 'Update the shaft record' : 'Register a new shaft' }}</div>
        </div>
        <div class="row">
          <button class="btn btn-secondary" (click)="cancel()">Cancel</button>
          <button class="btn" [disabled]="saving()" (click)="save()">
            @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading shaft…</div>
      } @else {
        <div class="stack">
          <div class="card">
            <div class="card-header"><div class="card-title">Identification</div></div>
            <div class="form-grid">
              <div class="field">
                <label class="req" for="code">Code</label>
                <input id="code" class="input" [(ngModel)]="model.code" [disabled]="isEdit()" required>
              </div>
              <div class="field">
                <label class="req" for="name">Name</label>
                <input id="name" class="input" [(ngModel)]="model.name" required>
              </div>
              <div class="field">
                <label for="shaftNumber">Shaft number</label>
                <input id="shaftNumber" class="input" [(ngModel)]="model.shaftNumber">
              </div>
              <div class="field" style="grid-column: 1 / -1">
                <label for="description">Description</label>
                <textarea id="description" class="textarea" [(ngModel)]="model.description"></textarea>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Placement</div></div>
            <div class="form-grid">
              <div class="field">
                <label class="req" for="project">Project</label>
                <select id="project" class="select" [(ngModel)]="model.projectId" (change)="onProjectChange()">
                  <option [ngValue]="null">Select project…</option>
                  @for (p of projects(); track p.id) { <option [ngValue]="p.id">{{ p.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="operation">Mining operation</label>
                <select id="operation" class="select" [(ngModel)]="model.miningOperationId" [disabled]="!model.projectId">
                  <option [ngValue]="null">Select operation…</option>
                  @for (o of operations(); track o.id) { <option [ngValue]="o.id">{{ o.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="ownerPartner">Owner partner</label>
                <select id="ownerPartner" class="select" [(ngModel)]="model.ownerPartnerId">
                  <option [ngValue]="null">None</option>
                  @for (p of partners(); track p.id) { <option [ngValue]="p.id">{{ p.legalName }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="shaftManager">Shaft manager (user ID)</label>
                <input id="shaftManager" class="input" type="number" [(ngModel)]="model.shaftManagerId">
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Location</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="latitude">Latitude</label>
                <input id="latitude" class="input" type="number" step="any" [(ngModel)]="model.latitude">
              </div>
              <div class="field">
                <label for="longitude">Longitude</label>
                <input id="longitude" class="input" type="number" step="any" [(ngModel)]="model.longitude">
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Operations</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="depth">Depth (metres)</label>
                <input id="depth" class="input" type="number" step="any" [(ngModel)]="model.depthMetres">
              </div>
              <div class="field">
                <label for="commissioned">Commissioned date</label>
                <input id="commissioned" class="input" type="date" [(ngModel)]="model.commissionedDate">
              </div>
              <div class="field">
                <label for="start">Start date</label>
                <input id="start" class="input" type="date" [(ngModel)]="model.startDate">
              </div>
              <div class="field">
                <label for="status">Status</label>
                <select id="status" class="select" [(ngModel)]="model.status">
                  @for (s of reference.data()?.shaftStatuses; track s) { <option [value]="s">{{ s }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="target">Production target</label>
                <input id="target" class="input" type="number" step="any" [(ngModel)]="model.productionTarget">
              </div>
              <div class="field">
                <label for="targetUnit">Target unit</label>
                <select id="targetUnit" class="select" [(ngModel)]="model.productionTargetUnit">
                  <option value="">—</option>
                  @for (u of reference.data()?.productionUnits; track u.code) { <option [value]="u.code">{{ u.code }} — {{ u.name }}</option> }
                </select>
              </div>
              <div class="field">
                <label for="targetPeriod">Target period</label>
                <select id="targetPeriod" class="select" [(ngModel)]="model.productionTargetPeriod">
                  <option value="DAILY">Daily</option>
                  <option value="WEEKLY">Weekly</option>
                  <option value="MONTHLY">Monthly</option>
                </select>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Notes</div></div>
            <div class="field">
              <textarea class="textarea" [(ngModel)]="model.notes"></textarea>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: [],
})
export class ShaftFormPage implements OnInit {

  readonly id = input<string>();

  private readonly api = inject(ShaftApi);
  private readonly projectApi = inject(ProjectApi);
  private readonly operationApi = inject(OperationApi);
  private readonly partnerApi = inject(PartnerApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly reference = inject(ReferenceService);

  protected readonly isEdit = computed(() => !!this.id());
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected readonly projects = signal<ProjectSummary[]>([]);
  protected readonly operations = signal<OperationSummary[]>([]);
  protected readonly partners = signal<PartnerSummary[]>([]);

  protected model: ShaftFormModel = blankModel();

  ngOnInit(): void {
    this.projectApi.options().subscribe({ next: p => this.projects.set(p), error: () => {} });
    this.partnerApi.options().subscribe({ next: p => this.partners.set(p), error: () => {} });

    const id = this.id();
    if (id) {
      this.loading.set(true);
      this.api.get(Number(id)).subscribe({
        next: s => {
          this.model = {
            code: s.code, name: s.name, shaftNumber: s.shaftNumber ?? '', description: s.description ?? '',
            projectId: s.projectId, miningOperationId: s.miningOperationId ?? null,
            ownerPartnerId: s.ownerPartnerId ?? null, shaftManagerId: s.shaftManagerId ?? null,
            latitude: s.latitude ?? null, longitude: s.longitude ?? null,
            depthMetres: s.depthMetres ?? null, commissionedDate: s.commissionedDate ?? '', startDate: s.startDate ?? '',
            status: s.status,
            productionTarget: s.productionTarget ?? null, productionTargetUnit: s.productionTargetUnit ?? '',
            productionTargetPeriod: s.productionTargetPeriod ?? 'MONTHLY',
            notes: s.notes ?? '',
          };
          this.loading.set(false);
          if (s.projectId) {
            this.operationApi.options(s.projectId).subscribe({ next: o => this.operations.set(o), error: () => {} });
          }
        },
        error: () => this.loading.set(false),
      });
    }
  }

  /** A stale operation left over from a different project is exactly what the server rejects. */
  protected onProjectChange(): void {
    this.model.miningOperationId = null;
    this.operations.set([]);
    if (this.model.projectId) {
      this.operationApi.options(this.model.projectId).subscribe({ next: o => this.operations.set(o), error: () => {} });
    }
  }

  protected cancel(): void {
    const id = this.id();
    this.router.navigateByUrl(id ? '/shafts/' + id : '/shafts');
  }

  protected save(): void {
    if (!this.model.code || !this.model.name || !this.model.projectId) {
      this.toast.error('Code, name and project are required.');
      return;
    }
    this.saving.set(true);
    const id = this.id();
    const request = id ? this.api.update(Number(id), this.model) : this.api.create(this.model);
    request.subscribe({
      next: saved => {
        this.saving.set(false);
        this.toast.success(id ? 'Shaft updated' : 'Shaft created');
        this.router.navigateByUrl('/shafts/' + saved.id);
      },
      error: () => this.saving.set(false),
    });
  }
}
