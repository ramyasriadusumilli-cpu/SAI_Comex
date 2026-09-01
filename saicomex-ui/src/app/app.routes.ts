import { Routes } from '@angular/router';

import { authGuard, permissionGuard } from './core/guards/auth.guard';

/**
 * SRS §44 — main navigation.
 *
 * Every screen is lazily loaded. The permission guards mirror the server-side
 * checks exactly; they exist so an operator never lands on a page that can
 * only show them errors, not as a security boundary.
 */
export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then(m => m.LoginPage),
  },
  {
    path: '',
    canActivate: [authGuard],
    loadComponent: () => import('./layout/shell').then(m => m.Shell),
    children: [
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' },

      {
        path: 'dashboard',
        canActivate: [permissionGuard('dashboard.view')],
        loadComponent: () => import('./pages/dashboard/dashboard').then(m => m.DashboardPage),
      },

      {
        path: 'projects',
        canActivate: [permissionGuard('projects.view')],
        loadComponent: () => import('./pages/projects/project-list').then(m => m.ProjectListPage),
      },
      {
        path: 'projects/new',
        canActivate: [permissionGuard('projects.create')],
        loadComponent: () => import('./pages/projects/project-form').then(m => m.ProjectFormPage),
      },
      {
        path: 'projects/:id/edit',
        canActivate: [permissionGuard('projects.edit')],
        loadComponent: () => import('./pages/projects/project-form').then(m => m.ProjectFormPage),
      },
      {
        path: 'projects/:id',
        canActivate: [permissionGuard('projects.view')],
        loadComponent: () => import('./pages/projects/project-detail').then(m => m.ProjectDetailPage),
      },

      {
        path: 'operations',
        canActivate: [permissionGuard('operations.view')],
        loadComponent: () => import('./pages/operations/operation-list').then(m => m.OperationListPage),
      },

      {
        path: 'shafts',
        canActivate: [permissionGuard('shafts.view')],
        loadComponent: () => import('./pages/shafts/shaft-list').then(m => m.ShaftListPage),
      },
      {
        path: 'shafts/new',
        canActivate: [permissionGuard('shafts.create')],
        loadComponent: () => import('./pages/shafts/shaft-form').then(m => m.ShaftFormPage),
      },
      {
        path: 'shafts/:id/edit',
        canActivate: [permissionGuard('shafts.edit')],
        loadComponent: () => import('./pages/shafts/shaft-form').then(m => m.ShaftFormPage),
      },
      {
        path: 'shafts/:id',
        canActivate: [permissionGuard('shafts.view')],
        loadComponent: () => import('./pages/shafts/shaft-detail').then(m => m.ShaftDetailPage),
      },

      {
        path: 'partners',
        canActivate: [permissionGuard('partners.view')],
        loadComponent: () => import('./pages/partners/partner-list').then(m => m.PartnerListPage),
      },
      {
        path: 'partners/:id',
        canActivate: [permissionGuard('partners.view')],
        loadComponent: () => import('./pages/partners/partner-detail').then(m => m.PartnerDetailPage),
      },

      {
        path: 'contracts',
        canActivate: [permissionGuard('contracts.view')],
        loadComponent: () => import('./pages/contracts/contract-list').then(m => m.ContractListPage),
      },
      {
        path: 'contracts/new',
        canActivate: [permissionGuard('contracts.create')],
        loadComponent: () => import('./pages/contracts/contract-form').then(m => m.ContractFormPage),
      },
      {
        path: 'contracts/:id/edit',
        canActivate: [permissionGuard('contracts.edit')],
        loadComponent: () => import('./pages/contracts/contract-form').then(m => m.ContractFormPage),
      },
      {
        path: 'contracts/:id',
        canActivate: [permissionGuard('contracts.view')],
        loadComponent: () => import('./pages/contracts/contract-detail').then(m => m.ContractDetailPage),
      },
      {
        path: 'contracts/:contractId/agreements/new',
        canActivate: [permissionGuard('agreements.create')],
        loadComponent: () => import('./pages/agreements/agreement-builder').then(m => m.AgreementBuilderPage),
      },
      {
        path: 'agreements/:id',
        canActivate: [permissionGuard('agreements.view')],
        loadComponent: () => import('./pages/agreements/agreement-builder').then(m => m.AgreementBuilderPage),
      },

      {
        path: 'settlements',
        canActivate: [permissionGuard('settlements.view')],
        loadComponent: () => import('./pages/settlements/settlement-list').then(m => m.SettlementListPage),
      },
      {
        path: 'settlements/new',
        canActivate: [permissionGuard('settlements.calculate')],
        loadComponent: () => import('./pages/settlements/settlement-run').then(m => m.SettlementRunPage),
      },
      {
        path: 'settlements/:id',
        canActivate: [permissionGuard('settlements.view')],
        loadComponent: () => import('./pages/settlements/settlement-detail').then(m => m.SettlementDetailPage),
      },

      {
        path: 'production',
        canActivate: [permissionGuard('production.view')],
        loadComponent: () => import('./pages/transactions/production-list').then(m => m.ProductionListPage),
      },
      {
        path: 'expenses',
        canActivate: [permissionGuard('expenses.view')],
        loadComponent: () => import('./pages/transactions/expense-list').then(m => m.ExpenseListPage),
      },
      {
        path: 'sales',
        canActivate: [permissionGuard('sales.view')],
        loadComponent: () => import('./pages/transactions/sale-list').then(m => m.SaleListPage),
      },

      {
        path: 'inventory/items',
        canActivate: [permissionGuard('inventory.view')],
        loadComponent: () => import('./pages/inventory/item-list').then(m => m.InventoryItemListPage),
      },
      {
        path: 'inventory/stores',
        canActivate: [permissionGuard('inventory.view')],
        loadComponent: () => import('./pages/inventory/store-list').then(m => m.StoreListPage),
      },
      {
        path: 'inventory/stock',
        canActivate: [permissionGuard('inventory.view')],
        loadComponent: () => import('./pages/inventory/stock').then(m => m.StockPage),
      },
      {
        path: 'fuel',
        canActivate: [permissionGuard('fuel.view')],
        loadComponent: () => import('./pages/inventory/fuel-list').then(m => m.FuelListPage),
      },
      {
        path: 'suppliers',
        canActivate: [permissionGuard('suppliers.view')],
        loadComponent: () => import('./pages/inventory/supplier-list').then(m => m.SupplierListPage),
      },
      {
        path: 'purchase-orders',
        canActivate: [permissionGuard('inventory.view')],
        loadComponent: () => import('./pages/inventory/po-list').then(m => m.PurchaseOrderListPage),
      },

      {
        path: 'equipment',
        canActivate: [permissionGuard('equipment.view')],
        loadComponent: () => import('./pages/equipment/equipment-list').then(m => m.EquipmentListPage),
      },
      {
        path: 'maintenance',
        canActivate: [permissionGuard('maintenance.view')],
        loadComponent: () => import('./pages/equipment/maintenance-list').then(m => m.MaintenanceListPage),
      },

      {
        path: 'users',
        canActivate: [permissionGuard('users.view')],
        loadComponent: () => import('./pages/admin/user-list').then(m => m.UserListPage),
      },
      {
        path: 'roles',
        canActivate: [permissionGuard('roles.view')],
        loadComponent: () => import('./pages/admin/role-list').then(m => m.RoleListPage),
      },
      {
        path: 'audit',
        canActivate: [permissionGuard('audit.view')],
        loadComponent: () => import('./pages/admin/audit-log').then(m => m.AuditLogPage),
      },
      {
        path: 'settings',
        canActivate: [permissionGuard('settings.view')],
        loadComponent: () => import('./pages/admin/settings').then(m => m.SettingsPage),
      },
      {
        path: 'alerts',
        canActivate: [permissionGuard('alerts.view')],
        loadComponent: () => import('./pages/admin/alert-list').then(m => m.AlertListPage),
      },
    ],
  },
  { path: '**', redirectTo: '' },
];
