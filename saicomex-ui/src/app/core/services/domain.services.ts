import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { ApiService } from './api.service';
import * as M from '../models/api.models';

/**
 * One injectable per API area, grouped in a single file.
 *
 * Each is a thin typed wrapper over {@link ApiService} — no caching, no state,
 * no logic. Splitting eleven ten-line classes across eleven files would add
 * navigation cost without adding a single thing worth finding.
 */

@Injectable({ providedIn: 'root' })
export class ProjectApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.ProjectSummary>>('/projects', params); }
  options() { return this.api.get<M.ProjectSummary[]>('/projects/options'); }
  get(id: number) { return this.api.get<M.ProjectDetail>(`/projects/${id}`); }
  create(body: unknown) { return this.api.post<M.ProjectDetail>('/projects', body); }
  update(id: number, body: unknown) { return this.api.put<M.ProjectDetail>(`/projects/${id}`, body); }
  remove(id: number, reason?: string) { return this.api.delete<void>(`/projects/${id}`, { reason }); }
}

@Injectable({ providedIn: 'root' })
export class OperationApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.OperationSummary>>('/operations', params); }
  options(projectId?: number) { return this.api.get<M.OperationSummary[]>('/operations/options', { projectId }); }
  get(id: number) { return this.api.get<M.OperationSummary>(`/operations/${id}`); }
  create(body: unknown) { return this.api.post<M.OperationSummary>('/operations', body); }
  update(id: number, body: unknown) { return this.api.put<M.OperationSummary>(`/operations/${id}`, body); }
  remove(id: number, reason?: string) { return this.api.delete<void>(`/operations/${id}`, { reason }); }
}

@Injectable({ providedIn: 'root' })
export class ShaftApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.ShaftSummary>>('/shafts', params); }
  options(params?: Record<string, unknown>) { return this.api.get<M.ShaftSummary[]>('/shafts/options', params); }
  get(id: number) { return this.api.get<M.ShaftDetail>(`/shafts/${id}`); }
  create(body: unknown) { return this.api.post<M.ShaftDetail>('/shafts', body); }
  update(id: number, body: unknown) { return this.api.put<M.ShaftDetail>(`/shafts/${id}`, body); }
  setStatus(id: number, status: string, reason: string) { return this.api.patch<M.ShaftDetail>(`/shafts/${id}/status`, { status, reason }); }
  remove(id: number, reason?: string) { return this.api.delete<void>(`/shafts/${id}`, { reason }); }
}

@Injectable({ providedIn: 'root' })
export class PartnerApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.PartnerSummary>>('/partners', params); }
  options() { return this.api.get<M.PartnerSummary[]>('/partners/options'); }
  get(id: number) { return this.api.get<M.PartnerDetail>(`/partners/${id}`); }
  create(body: unknown) { return this.api.post<M.PartnerDetail>('/partners', body); }
  update(id: number, body: unknown) { return this.api.put<M.PartnerDetail>(`/partners/${id}`, body); }
  remove(id: number, reason?: string) { return this.api.delete<void>(`/partners/${id}`, { reason }); }
}

@Injectable({ providedIn: 'root' })
export class ContractApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.ContractSummary>>('/contracts', params); }
  get(id: number) { return this.api.get<M.ContractDetail>(`/contracts/${id}`); }
  create(body: unknown) { return this.api.post<M.ContractDetail>('/contracts', body); }
  update(id: number, body: unknown) { return this.api.put<M.ContractDetail>(`/contracts/${id}`, body); }
  activate(id: number) { return this.api.post<M.ContractDetail>(`/contracts/${id}/activate`); }
  terminate(id: number, reason: string) { return this.api.post<M.ContractDetail>(`/contracts/${id}/terminate`, { reason }); }
  amend(id: number, body: unknown) { return this.api.post<M.ContractDetail>(`/contracts/${id}/amend`, body); }
  versions(id: number) { return this.api.get<M.ContractVersionDto[]>(`/contracts/${id}/versions`); }
  expiring(days = 30) { return this.api.get<M.ContractSummary[]>('/contracts/expiring', { days }); }
}

@Injectable({ providedIn: 'root' })
export class AgreementApi {
  private api = inject(ApiService);
  byContract(contractId: number) { return this.api.get<M.AgreementSummary[]>('/agreements', { contractId }); }
  get(id: number) { return this.api.get<M.AgreementDetail>(`/agreements/${id}`); }
  create(body: unknown) { return this.api.post<M.AgreementDetail>('/agreements', body); }
  update(id: number, body: unknown) { return this.api.put<M.AgreementDetail>(`/agreements/${id}`, body); }
  activate(id: number) { return this.api.post<M.AgreementDetail>(`/agreements/${id}/activate`); }
  ruleTypes() { return this.api.get<M.AgreementRuleTypeDto[]>('/agreements/rule-types'); }
}

@Injectable({ providedIn: 'root' })
export class SettlementApi {
  private api = inject(ApiService);
  list(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.SettlementSummary>>('/settlements', params); }
  get(id: number) { return this.api.get<M.SettlementDetail>(`/settlements/${id}`); }
  preview(body: unknown) { return this.api.post<M.PreviewResult>('/settlements/preview', body); }
  calculate(body: unknown) { return this.api.post<M.SettlementDetail>('/settlements', body); }
  recalculate(id: number) { return this.api.post<M.SettlementDetail>(`/settlements/${id}/recalculate`); }
  approve(id: number, comments?: string) { return this.api.post<M.SettlementDetail>(`/settlements/${id}/approve`, { comments }); }
  cancel(id: number, reason: string) { return this.api.post<void>(`/settlements/${id}/cancel`, {}, { reason }); }
  partnerStatement(partnerId: number) { return this.api.get<M.PartnerStatement>(`/settlements/partner/${partnerId}/statement`); }
}

@Injectable({ providedIn: 'root' })
export class DashboardApi {
  private api = inject(ApiService);
  executive(from?: string, to?: string) { return this.api.get<M.ExecutiveDashboard>('/dashboard/executive', { from, to }); }
  projects(from?: string, to?: string) { return this.api.get<M.ProjectPerformance[]>('/dashboard/projects', { from, to }); }
  shafts(projectId?: number, from?: string, to?: string) { return this.api.get<M.ShaftPerformance[]>('/dashboard/shafts', { projectId, from, to }); }
  shaftKpis(shaftId: number, from?: string, to?: string) { return this.api.get<M.ShaftKpis>(`/dashboard/shafts/${shaftId}`, { from, to }); }
  shaftExpenses(shaftId: number, from?: string, to?: string) { return this.api.get<M.CategoryBreakdown[]>(`/dashboard/shafts/${shaftId}/expenses`, { from, to }); }
}

@Injectable({ providedIn: 'root' })
export class TransactionApi {
  private api = inject(ApiService);
  production(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.ProductionSummary>>('/production', params); }
  createProduction(body: unknown) { return this.api.post<unknown>('/production', body); }
  expenses(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.ExpenseSummary>>('/expenses', params); }
  createExpense(body: unknown) { return this.api.post<unknown>('/expenses', body); }
  sales(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.SaleSummary>>('/sales', params); }
  payments(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.PaymentSummary>>('/payments', params); }
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private api = inject(ApiService);
  users(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.UserSummary>>('/users', params); }
  user(id: number) { return this.api.get<M.UserDetail>(`/users/${id}`); }
  createUser(body: unknown) { return this.api.post<{ user: M.UserDetail; initialPassword: string }>('/users', body); }
  updateUser(id: number, body: unknown) { return this.api.put<M.UserDetail>(`/users/${id}`, body); }
  setUserStatus(id: number, status: string, reason?: string) { return this.api.patch<M.UserDetail>(`/users/${id}/status`, { status, reason }); }
  resetPassword(id: number) { return this.api.post<{ initialPassword: string }>(`/users/${id}/reset-password`); }
  removeUser(id: number, reason?: string) { return this.api.delete<void>(`/users/${id}`, { reason }); }

  roles() { return this.api.get<M.RoleSummary[]>('/roles'); }
  role(id: number) { return this.api.get<M.RoleDetail>(`/roles/${id}`); }
  createRole(body: unknown) { return this.api.post<M.RoleDetail>('/roles', body); }
  updateRole(id: number, body: unknown) { return this.api.put<M.RoleDetail>(`/roles/${id}`, body); }
  permissionCatalogue() { return this.api.get<{ module: string; permissions: M.PermissionDto[] }[]>('/roles/permissions'); }

  audit(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.AuditEntry>>('/audit', params); }
  auditForEntity(entityType: string, entityId: number) { return this.api.get<M.PageResponse<M.AuditEntry>>(`/audit/entity/${entityType}/${entityId}`); }

  settings() { return this.api.get<M.SystemConfigByCategory>('/settings'); }
  updateSetting(key: string, value: string) { return this.api.put<M.SystemConfigDto>(`/settings/${key}`, { value }); }

  alerts(params?: Record<string, unknown>) { return this.api.get<M.PageResponse<M.AlertDto>>('/alerts', params); }
  acknowledgeAlert(id: number, note?: string) { return this.api.post<M.AlertDto>(`/alerts/${id}/acknowledge`, { note }); }
  resolveAlert(id: number, note?: string) { return this.api.post<M.AlertDto>(`/alerts/${id}/resolve`, { note }); }
  notifications() { return this.api.get<M.PageResponse<M.NotificationDto>>('/notifications'); }
  unreadCount() { return this.api.get<number>('/notifications/unread-count'); }
  markAllRead() { return this.api.post<void>('/notifications/read-all'); }
}

/**
 * Reference lists, fetched once and held for the session.
 *
 * These change roughly never — currencies, units, categories, statuses. One
 * call at sign-in warms every dropdown in the application; refetching them per
 * screen would be dozens of redundant round trips on a slow site connection.
 */
@Injectable({ providedIn: 'root' })
export class ReferenceService {
  private api = inject(ApiService);
  readonly data = signal<M.ReferenceData | null>(null);

  load(): Observable<M.ReferenceData> {
    return this.api.get<M.ReferenceData>('/reference/all').pipe(tap(d => this.data.set(d)));
  }

  ensureLoaded(): void {
    if (!this.data()) this.load().subscribe({ error: () => {} });
  }
}
