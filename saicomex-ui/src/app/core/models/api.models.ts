/**
 * Types mirroring the API's DTOs.
 *
 * Hand-written rather than generated from the OpenAPI schema: the schema is
 * the contract, but a small hand-kept model file stays readable and makes a
 * breaking backend change show up as a TypeScript error rather than as
 * `undefined` on a screen.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

// ------------------------------------------------------------------- auth

export interface LoginResponse {
  token: string;
  expiresInMs: number;
  userId: number;
  email: string;
  fullName: string;
  roleCode: string;
  roleName: string;
  permissions: string[];
  projectIds: number[];
  shaftIds: number[];
  mustChangePassword: boolean;
  preferredCurrency?: string;
}

export interface CurrentUser {
  userId: number;
  email: string;
  fullName: string;
  roleCode: string;
  roleName: string;
  permissions: string[];
  projectIds: number[];
  shaftIds: number[];
  mustChangePassword: boolean;
  preferredCurrency?: string;
  companyName: string;
  reportingCurrency: string;
}

// --------------------------------------------------------------- hierarchy

export interface ProjectSummary {
  id: number;
  code: string;
  name: string;
  projectType?: string;
  status: string;
  locationName?: string;
  projectManagerName?: string;
  startDate?: string;
  operationCount: number;
  shaftCount: number;
  activeShaftCount: number;
  budgetAmount?: number;
  budgetCurrency?: string;
}

export interface ProjectDetail extends ProjectSummary {
  description?: string;
  latitude?: number;
  longitude?: number;
  boundaryGeojson?: string;
  projectManagerId?: number;
  plannedCompletionDate?: string;
  actualCompletionDate?: string;
  licenceNumber?: string;
  licenceExpiryDate?: string;
  permitNumber?: string;
  permitExpiryDate?: string;
  notes?: string;
  documentCount?: number;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

export interface OperationSummary {
  id: number;
  code: string;
  name: string;
  operationType: string;
  projectId: number;
  projectName?: string;
  status: string;
  managerName?: string;
  shaftCount?: number;
  activeShaftCount?: number;
  startDate?: string;
}

export interface ShaftSummary {
  id: number;
  code: string;
  name: string;
  shaftNumber?: string;
  projectId: number;
  projectName?: string;
  miningOperationId?: number;
  operationName?: string;
  ownerPartnerId?: number;
  ownerPartnerName?: string;
  status: string;
  contractStatus?: string;
  productionTarget?: number;
  productionTargetUnit?: string;
  startDate?: string;
}

export interface ShaftDetail extends ShaftSummary {
  description?: string;
  latitude?: number;
  longitude?: number;
  shaftManagerId?: number;
  shaftManagerName?: string;
  depthMetres?: number;
  commissionedDate?: string;
  closureDate?: string;
  productionTargetPeriod?: string;
  activeContractId?: number;
  activeContractNumber?: string;
  documentCount?: number;
  notes?: string;
  createdAt?: string;
  createdBy?: string;
  updatedAt?: string;
  updatedBy?: string;
}

// ---------------------------------------------------------------- partners

export interface PartnerSummary {
  id: number;
  code: string;
  legalName: string;
  tradingName?: string;
  partnerType: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  status: string;
  shaftCount?: number;
  totalPayable?: number;
  totalPaid?: number;
  outstanding?: number;
}

export interface PartnerDetail extends PartnerSummary {
  registrationNumber?: string;
  taxNumber?: string;
  idNumber?: string;
  address?: string;
  city?: string;
  country?: string;
  /** Null unless the caller holds `partners.banking`. */
  bankName?: string;
  bankBranch?: string;
  bankAccountName?: string;
  bankAccountNumber?: string;
  bankSwift?: string;
  paymentCurrency?: string;
  paymentMethod?: string;
  onboardedDate?: string;
  notes?: string;
  shafts?: { shaftId: number; shaftCode: string; shaftName: string; projectName?: string; status: string }[];
  contracts?: { contractId: number; contractNumber: string; shaftName?: string; status: string; effectiveDate?: string; expiryDate?: string }[];
}

// --------------------------------------------------------------- contracts

export interface ContractSummary {
  id: number;
  contractNumber: string;
  projectName?: string;
  shaftName?: string;
  partnerName?: string;
  contractTypeName?: string;
  status: string;
  effectiveDate: string;
  expiryDate?: string;
  settlementCurrency?: string;
  hasActiveAgreement?: boolean;
}

export interface ContractDetail extends ContractSummary {
  projectId?: number;
  miningOperationId?: number;
  shaftId?: number;
  partnerId?: number;
  contractTypeId?: number;
  title?: string;
  renewalDate?: string;
  signedDate?: string;
  currentVersion?: number;
  settlementFrequency?: string;
  specialConditions?: string;
  terminationNotes?: string;
  approvedBy?: string;
  approvedAt?: string;
  versions?: ContractVersionDto[];
  documentCount?: number;
  createdAt?: string;
  createdBy?: string;
}

export interface ContractVersionDto {
  id: number;
  versionNumber: number;
  effectiveFrom: string;
  effectiveTo?: string;
  changeReason: string;
  changeSummary?: string;
  status: string;
  approvedBy?: string;
  approvedAt?: string;
  createdAt?: string;
  createdBy?: string;
}

// -------------------------------------------------------------- agreements

export interface AgreementRuleTierDto {
  id?: number;
  tierNo: number;
  fromValue: number;
  toValue?: number | null;
  saicomexPercent?: number;
  partnerPercent?: number;
  fixedAmount?: number;
  rateAmount?: number;
}

export interface AgreementRuleDto {
  id?: number;
  ruleType: string;
  name: string;
  description?: string;
  sequenceNo: number;
  scope: string;
  expenseCategoryId?: number;
  scopeValue?: string;
  calculationMethod: string;
  saicomexPercent?: number;
  partnerPercent?: number;
  fixedAmount?: number;
  rateAmount?: number;
  rateUnit?: string;
  currency?: string;
  borneBy: string;
  deductBeforeSplit: boolean;
  minAmount?: number;
  maxAmount?: number;
  capPercent?: number;
  recoverableTotal?: number;
  recoveredToDate?: number;
  effectiveFrom?: string;
  effectiveTo?: string;
  isActive: boolean;
  notes?: string;
  tiers?: AgreementRuleTierDto[];
}

export interface AgreementSummary {
  id: number;
  contractId: number;
  name: string;
  status: string;
  settlementBasis: string;
  effectiveFrom: string;
  effectiveTo?: string;
  currency: string;
  defaultSaicomexPercent?: number;
  defaultPartnerPercent?: number;
  ruleCount?: number;
}

export interface AgreementDetail extends AgreementSummary {
  description?: string;
  contractNumber?: string;
  shaftName?: string;
  partnerName?: string;
  contractVersionId?: number;
  roundingScale?: number;
  roundingMode?: string;
  notes?: string;
  approvedBy?: string;
  approvedAt?: string;
  rules: AgreementRuleDto[];
}

export interface AgreementRuleTypeDto {
  code: string;
  name: string;
  description?: string;
  stage: string;
  defaultSequence: number;
}

// ------------------------------------------------------------- settlements

export interface CalculationStepDto {
  stepNo: number;
  stage: string;
  ruleId?: number;
  ruleType?: string;
  ruleName?: string;
  expression: string;
  inputAmount?: number;
  percentApplied?: number;
  rateApplied?: number;
  resultAmount: number;
  runningBalance?: number;
  beneficiary?: string;
  notes?: string;
}

export interface SettlementLineDto {
  id: number;
  lineType: string;
  sourceTable: string;
  sourceId?: number;
  lineDate?: string;
  description: string;
  categoryCode?: string;
  quantity?: number;
  unitCode?: string;
  amount: number;
  currency?: string;
  baseAmount: number;
  included: boolean;
  exclusionReason?: string;
}

export interface SettlementSummary {
  id: number;
  settlementNumber: string;
  projectName?: string;
  shaftName?: string;
  partnerName?: string;
  periodStart: string;
  periodEnd: string;
  currency: string;
  grossRevenue: number;
  netDistributable: number;
  saicomexShare: number;
  partnerNetPayable: number;
  amountPaid: number;
  amountOutstanding: number;
  status: string;
}

export interface SettlementDetail extends SettlementSummary {
  projectId?: number;
  shaftId?: number;
  partnerId?: number;
  contractId?: number;
  contractNumber?: string;
  agreementId?: number;
  agreementName?: string;
  settlementDate?: string;
  totalDeductions: number;
  partnerShare: number;
  partnerAdjustments: number;
  amountRetained: number;
  totalProduction?: number;
  productionUnit?: string;
  totalExpenses?: number;
  calculatedAt?: string;
  calculatedBy?: string;
  approvedBy?: string;
  approvedAt?: string;
  notes?: string;
  steps: CalculationStepDto[];
  lines: SettlementLineDto[];
}

export interface PreviewResult {
  shaftId: number;
  shaftName: string;
  partnerName: string;
  contractNumber: string;
  agreementName: string;
  periodStart: string;
  periodEnd: string;
  currency: string;
  grossRevenue: number;
  totalDeductions: number;
  netDistributable: number;
  saicomexShare: number;
  partnerShare: number;
  partnerAdjustments: number;
  partnerNetPayable: number;
  totalProduction?: number;
  productionUnit?: string;
  steps: CalculationStepDto[];
  warnings: string[];
}

export interface PartnerStatement {
  partnerId: number;
  partnerName: string;
  currency: string;
  totalEarned: number;
  totalPaid: number;
  totalRetained: number;
  totalOutstanding: number;
  settlements: SettlementSummary[];
}

// --------------------------------------------------------------- dashboard

export interface ExecutiveDashboard {
  currency: string;
  productionUnit: string;
  periodStart: string;
  periodEnd: string;
  totalProjects: number;
  activeProjects: number;
  suspendedProjects: number;
  closedProjects: number;
  totalOperations: number;
  totalShafts: number;
  activeShafts: number;
  nonProducingShafts: number;
  productionToday: number;
  productionThisWeek: number;
  productionThisMonth: number;
  productionYearToDate: number;
  productionPeriod: number;
  grossRevenue: number;
  operatingExpenditure: number;
  capitalExpenditure: number;
  netOperatingResult: number;
  saicomexShare: number;
  partnerShare: number;
  outstandingPartnerSettlements: number;
  outstandingLiabilities: number;
  openAlerts: number;
  criticalAlerts: number;
  pendingApprovals: number;
  dataNotes: string[];
}

export interface ProjectPerformance {
  projectId: number;
  projectCode: string;
  projectName: string;
  status: string;
  shaftCount: number;
  activeShaftCount: number;
  production: number;
  productionUnit: string;
  revenue: number;
  expenses: number;
  netResult: number;
  budgetAmount?: number;
  budgetVariance?: number;
}

export interface ShaftPerformance {
  shaftId: number;
  shaftCode: string;
  shaftName: string;
  projectId: number;
  projectName?: string;
  partnerName?: string;
  status: string;
  production: number;
  productionUnit: string;
  productionTarget?: number;
  productionVariance?: number;
  revenue: number;
  expenses: number;
  netResult: number;
  costPerUnit?: number;
  partnerPayable?: number;
  lastProductionDate?: string;
}

export interface CategoryBreakdown {
  categoryCode: string;
  categoryName: string;
  expenseClass: string;
  amount: number;
  percentOfTotal: number;
}

export interface ShaftKpis {
  shaftId: number;
  shaftName: string;
  currency: string;
  productionUnit: string;
  periodStart: string;
  periodEnd: string;
  production: number;
  revenue: number;
  expenses: number;
  netResult: number;
  costPerUnit?: number;
  fuelCostPerUnit?: number;
  profitMargin?: number;
  partnerPayable?: number;
  partnerOutstanding?: number;
  expenseBreakdown: CategoryBreakdown[];
}

// ------------------------------------------------------- production & cost

export interface ProductionSummary {
  id: number;
  projectName?: string;
  shaftName?: string;
  productionDate: string;
  shift?: string;
  quantity: number;
  unitCode: string;
  oreTonnes?: number;
  grade?: number;
  targetQuantity?: number;
  varianceQuantity?: number;
  status: string;
  recordedBy?: string;
}

export interface ExpenseSummary {
  id: number;
  expenseNumber: string;
  projectName?: string;
  shaftName?: string;
  categoryName?: string;
  expenseDate: string;
  description: string;
  amount: number;
  currency: string;
  baseAmount: number;
  status: string;
}

export interface SaleSummary {
  id: number;
  saleNumber: string;
  projectName?: string;
  shaftName?: string;
  buyerName?: string;
  saleDate: string;
  product: string;
  quantity: number;
  unitCode: string;
  unitPrice: number;
  currency: string;
  grossAmount: number;
  netAmount: number;
  status: string;
  paymentStatus?: string;
  settlementStatus?: string;
}

export interface PaymentSummary {
  id: number;
  paymentNumber: string;
  paymentType: string;
  paymentDate: string;
  recipientName: string;
  projectName?: string;
  shaftName?: string;
  amount: number;
  currency: string;
  paymentMethod: string;
  status: string;
}

// -------------------------------------------------------------- admin etc.

export interface UserSummary {
  id: number;
  email: string;
  fullName: string;
  roleCode: string;
  roleName: string;
  department?: string;
  status: string;
  lastLoginAt?: string;
  assignedProjectCount?: number;
  assignedShaftCount?: number;
}

export interface UserDetail extends UserSummary {
  firstName?: string;
  lastName?: string;
  phone?: string;
  jobTitle?: string;
  roleId?: number;
  preferredCurrency?: string;
  mfaEnabled?: boolean;
  mustChangePassword?: boolean;
  projectIds?: number[];
  shaftIds?: number[];
  createdAt?: string;
  createdBy?: string;
}

export interface RoleSummary {
  id: number;
  code: string;
  name: string;
  description?: string;
  isSystem: boolean;
  isActive: boolean;
  userCount?: number;
  permissionCount?: number;
}

export interface RoleDetail extends RoleSummary {
  displayOrder?: number;
  permissionCodes: string[];
}

export interface PermissionDto {
  code: string;
  module: string;
  action: string;
  description?: string;
}

export interface AuditEntry {
  id: number;
  occurredAt: string;
  userEmail?: string;
  userRole?: string;
  action: string;
  entityType: string;
  entityId?: number;
  entityLabel?: string;
  fieldName?: string;
  oldValue?: string;
  newValue?: string;
  reason?: string;
  summary?: string;
  ipAddress?: string;
}

export interface AlertDto {
  id: number;
  category: string;
  severity: string;
  title: string;
  message: string;
  projectId?: number;
  shaftId?: number;
  actualValue?: number;
  thresholdValue?: number;
  status: string;
  acknowledgedBy?: string;
  acknowledgedAt?: string;
  triggeredAt: string;
}

export interface NotificationDto {
  id: number;
  category: string;
  title: string;
  message: string;
  linkUrl?: string;
  severity: string;
  isRead: boolean;
  createdAt: string;
}

export interface SystemConfigDto {
  key: string;
  value?: string;
  valueType: string;
  category: string;
  label?: string;
  description?: string;
  editable: boolean;
  updatedAt?: string;
  updatedBy?: string;
}

/** GET /api/settings returns the settings already grouped by category. */
export type SystemConfigByCategory = Record<string, SystemConfigDto[]>;

export interface ReferenceData {
  currencies: { code: string; name: string; symbol?: string; decimalPlaces: number }[];
  productionUnits: { code: string; name: string; unitClass: string; decimalPlaces: number }[];
  expenseCategories: { id: number; code: string; name: string; expenseClass: string }[];
  contractTypes: { id: number; code: string; name: string; description?: string }[];
  agreementRuleTypes: AgreementRuleTypeDto[];
  roles: { id: number; code: string; name: string }[];
  projectStatuses: string[];
  shaftStatuses: string[];
  operationStatuses: string[];
  contractStatuses: string[];
  operationTypes: string[];
  reportDefinitions: { code: string; name: string; reportGroup: string }[];
}

// ------------------------------------------------------------ Phase 2: Inventory & Fuel

export interface InventoryItem {
  id: number;
  code: string;
  name: string;
  itemType: string;
  categoryId?: number;
  unit: string;
  isControlled: boolean;
  requiresPermit: boolean;
  minimumStock?: number;
  maximumStock?: number;
  reorderLevel?: number;
  standardCost?: number;
  costCurrency?: string;
  valuationMethod: string;
  isActive: boolean;
  notes?: string;
}

export interface StoreLocation {
  id: number;
  code: string;
  name: string;
  projectId?: number;
  shaftId?: number;
  locationId?: number;
  storeType: string;
  keeperUserId?: number;
  isActive: boolean;
}

export interface StockTransaction {
  id: number;
  transactionNumber: string;
  itemId: number;
  itemCode?: string;
  itemName?: string;
  storeId: number;
  storeName?: string;
  transactionType: string;
  transactionDate: string;
  quantity: number;
  unitCost?: number;
  totalCost?: number;
  currency?: string;
  balanceAfter?: number;
  shaftId?: number;
  expenseId?: number;
  permitReference?: string;
  recipientName?: string;
  reason: string;
  reference?: string;
  createdBy?: string;
}

export interface StockBalance {
  itemId: number;
  itemCode?: string;
  itemName?: string;
  unit?: string;
  storeId: number;
  storeName?: string;
  quantity: number;
  averageCost: number;
  costCurrency?: string;
  lastMovementAt?: string;
}

export interface FuelTransaction {
  id: number;
  transactionType: string;
  transactionDate: string;
  fuelType: string;
  itemId: number;
  storeId: number;
  quantityLitres: number;
  unitCost?: number;
  totalCost?: number;
  currency?: string;
  projectId?: number;
  shaftId?: number;
  equipmentId?: number;
  inventoryTransactionId?: number;
  expenseId?: number;
  odometerReading?: number;
  hourMeterReading?: number;
  openingStock?: number;
  closingStock?: number;
  reference?: string;
  createdBy?: string;
}

export interface SupplierOption {
  id: number;
  code: string;
  name: string;
  supplierType?: string;
}

export interface SupplierDetail {
  id: number;
  code: string;
  name: string;
  supplierType?: string;
  contactPerson?: string;
  phone?: string;
  email?: string;
  address?: string;
  taxNumber?: string;
  paymentTerms?: string;
  defaultCurrency?: string;
  status: string;
  notes?: string;
}

export interface PoLineDetail {
  id: number;
  lineNo: number;
  itemId?: number;
  description: string;
  quantity: number;
  receivedQuantity: number;
  unit?: string;
  unitCost: number;
  lineTotal: number;
}

export interface PoSummary {
  id: number;
  poNumber: string;
  supplierId: number;
  orderDate: string;
  expectedDate?: string;
  currency: string;
  totalAmount: number;
  status: string;
}

export interface PoDetail {
  id: number;
  poNumber: string;
  supplierId: number;
  projectId?: number;
  shaftId?: number;
  storeId?: number;
  orderDate: string;
  expectedDate?: string;
  currency: string;
  subtotal: number;
  taxAmount: number;
  totalAmount: number;
  status: string;
  approvedBy?: string;
  approvedAt?: string;
  notes?: string;
  lines: PoLineDetail[];
}
