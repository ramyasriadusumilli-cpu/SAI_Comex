import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ProjectApi, ReferenceService } from '../../core/services/domain.services';
import { ToastService } from '../../core/services/toast.service';

interface ProjectFormModel {
  code: string;
  name: string;
  projectType: string;
  status: string;
  description: string;
  locationName: string;
  latitude: number | null;
  longitude: number | null;
  startDate: string;
  plannedCompletionDate: string;
  actualCompletionDate: string;
  budgetAmount: number | null;
  budgetCurrency: string;
  licenceNumber: string;
  licenceExpiryDate: string;
  permitNumber: string;
  permitExpiryDate: string;
  notes: string;
}

function blankModel(): ProjectFormModel {
  return {
    code: '', name: '', projectType: 'OPEN_PIT', status: 'PLANNING', description: '',
    locationName: '', latitude: null, longitude: null,
    startDate: '', plannedCompletionDate: '', actualCompletionDate: '',
    budgetAmount: null, budgetCurrency: 'USD',
    licenceNumber: '', licenceExpiryDate: '', permitNumber: '', permitExpiryDate: '',
    notes: '',
  };
}

/** SRS §6 — create and edit form, serving both /projects/new and /projects/:id/edit. */
@Component({
  selector: 'app-project-form',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="page">
      <div class="crumbs">
        <a routerLink="/projects">Projects</a><span class="sep">›</span>
        <span>{{ isEdit() ? 'Edit project' : 'New project' }}</span>
      </div>

      <div class="page-header">
        <div class="page-title-group">
          <h1>{{ isEdit() ? 'Edit project' : 'New project' }}</h1>
          <div class="page-sub">{{ isEdit() ? 'Update the project record' : 'Register a new mining project' }}</div>
        </div>
        <div class="row">
          <button class="btn btn-secondary" (click)="cancel()">Cancel</button>
          <button class="btn" [disabled]="saving()" (click)="save()">
            @if (saving()) { <span class="spin"></span> Saving… } @else { Save }
          </button>
        </div>
      </div>

      @if (loading()) {
        <div class="loading-block"><span class="spin"></span> Loading project…</div>
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
                <label for="type">Type</label>
                <select id="type" class="select" [(ngModel)]="model.projectType">
                  <option value="OPEN_PIT">Open pit</option>
                  <option value="UNDERGROUND">Underground</option>
                  <option value="ALLUVIAL">Alluvial</option>
                  <option value="MIXED">Mixed</option>
                </select>
              </div>
              <div class="field">
                <label for="status">Status</label>
                <select id="status" class="select" [(ngModel)]="model.status">
                  @for (s of reference.data()?.projectStatuses; track s) { <option [value]="s">{{ s }}</option> }
                </select>
              </div>
              <div class="field" style="grid-column: 1 / -1">
                <label for="description">Description</label>
                <textarea id="description" class="textarea" [(ngModel)]="model.description"></textarea>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Location</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="locationName">Location name</label>
                <input id="locationName" class="input" [(ngModel)]="model.locationName">
              </div>
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
            <div class="card-header"><div class="card-title">Schedule</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="startDate">Start date</label>
                <input id="startDate" class="input" type="date" [(ngModel)]="model.startDate">
              </div>
              <div class="field">
                <label for="plannedCompletionDate">Planned completion</label>
                <input id="plannedCompletionDate" class="input" type="date" [(ngModel)]="model.plannedCompletionDate">
              </div>
              <div class="field">
                <label for="actualCompletionDate">Actual completion</label>
                <input id="actualCompletionDate" class="input" type="date" [(ngModel)]="model.actualCompletionDate">
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Commercial</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="budgetAmount">Budget amount</label>
                <input id="budgetAmount" class="input" type="number" step="0.01" [(ngModel)]="model.budgetAmount">
              </div>
              <div class="field">
                <label for="budgetCurrency">Budget currency</label>
                <select id="budgetCurrency" class="select" [(ngModel)]="model.budgetCurrency">
                  @for (c of reference.data()?.currencies; track c.code) { <option [value]="c.code">{{ c.code }} — {{ c.name }}</option> }
                </select>
              </div>
            </div>
          </div>

          <div class="card">
            <div class="card-header"><div class="card-title">Licensing</div></div>
            <div class="form-grid">
              <div class="field">
                <label for="licenceNumber">Licence number</label>
                <input id="licenceNumber" class="input" [(ngModel)]="model.licenceNumber">
              </div>
              <div class="field">
                <label for="licenceExpiryDate">Licence expiry</label>
                <input id="licenceExpiryDate" class="input" type="date" [(ngModel)]="model.licenceExpiryDate">
              </div>
              <div class="field">
                <label for="permitNumber">Permit number</label>
                <input id="permitNumber" class="input" [(ngModel)]="model.permitNumber">
              </div>
              <div class="field">
                <label for="permitExpiryDate">Permit expiry</label>
                <input id="permitExpiryDate" class="input" type="date" [(ngModel)]="model.permitExpiryDate">
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
export class ProjectFormPage implements OnInit {

  readonly id = input<string>();

  private readonly api = inject(ProjectApi);
  private readonly toast = inject(ToastService);
  private readonly router = inject(Router);
  protected readonly reference = inject(ReferenceService);

  protected readonly isEdit = computed(() => !!this.id());
  protected readonly loading = signal(false);
  protected readonly saving = signal(false);
  protected model: ProjectFormModel = blankModel();

  ngOnInit(): void {
    const id = this.id();
    if (id) {
      this.loading.set(true);
      this.api.get(Number(id)).subscribe({
        next: p => {
          this.model = {
            code: p.code, name: p.name, projectType: p.projectType ?? 'OPEN_PIT', status: p.status,
            description: p.description ?? '',
            locationName: p.locationName ?? '', latitude: p.latitude ?? null, longitude: p.longitude ?? null,
            startDate: p.startDate ?? '', plannedCompletionDate: p.plannedCompletionDate ?? '',
            actualCompletionDate: p.actualCompletionDate ?? '',
            budgetAmount: p.budgetAmount ?? null, budgetCurrency: p.budgetCurrency ?? 'USD',
            licenceNumber: p.licenceNumber ?? '', licenceExpiryDate: p.licenceExpiryDate ?? '',
            permitNumber: p.permitNumber ?? '', permitExpiryDate: p.permitExpiryDate ?? '',
            notes: p.notes ?? '',
          };
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
    }
  }

  protected cancel(): void {
    const id = this.id();
    this.router.navigateByUrl(id ? '/projects/' + id : '/projects');
  }

  protected save(): void {
    if (!this.model.code || !this.model.name) {
      this.toast.error('Code and name are required.');
      return;
    }
    this.saving.set(true);
    const id = this.id();
    const request = id ? this.api.update(Number(id), this.model) : this.api.create(this.model);
    request.subscribe({
      next: saved => {
        this.saving.set(false);
        this.toast.success(id ? 'Project updated' : 'Project created');
        this.router.navigateByUrl('/projects/' + saved.id);
      },
      error: () => this.saving.set(false),
    });
  }
}
