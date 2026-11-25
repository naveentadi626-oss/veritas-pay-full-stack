export type Role = 'CUSTOMER' | 'MERCHANT' | 'COMPLIANCE_OFFICER' | 'ADMIN';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  role: Role;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  expiresInMs: number;
  user: UserProfile;
}

export type AccountCategory =
  | 'USER_WALLET'
  | 'MERCHANT_SETTLEMENT'
  | 'SYSTEM_TREASURY'
  | 'FEE_REVENUE'
  | 'CLEARING_SUSPENSE';

export interface LedgerAccount {
  id: string;
  accountNumber: string;
  ownerId: string | null;
  name: string;
  category: AccountCategory;
  normalBalance: 'DEBIT' | 'CREDIT';
  currency: string;
  balance: number;
  status: string;
  updatedAt: string;
}

export type EntryType = 'DEBIT' | 'CREDIT';
export type TransactionType = 'TOP_UP' | 'P2P_TRANSFER' | 'MERCHANT_PAYMENT' | 'REVERSAL';
export type TransactionStatus = 'POSTED' | 'SETTLED' | 'REVERSED';

export interface LedgerEntryView {
  id: string;
  accountId: string;
  accountNumber: string;
  accountName: string;
  entryType: EntryType;
  amount: number;
  currency: string;
  balanceAfter: number;
  createdAt: string;
}

export interface JournalTransaction {
  id: string;
  referenceId: string;
  idempotencyKey: string | null;
  transactionType: TransactionType;
  status: TransactionStatus;
  currency: string;
  totalAmount: number;
  description: string | null;
  correlationId: string | null;
  createdAt: string;
  entries: LedgerEntryView[];
}

export interface TransferResult {
  transaction: JournalTransaction;
  idempotentReplayed: boolean;
  idempotencyKey: string | null;
  feeApplied: number;
}

export interface TrialBalanceReport {
  balanced: boolean;
  totalDebits: number;
  totalCredits: number;
  discrepancy: number;
  totalEntries: number;
  totalTransactions: number;
  verifiedAt: string;
}

export interface OutboxEvent {
  id: string;
  aggregateType: string;
  aggregateId: string;
  eventType: string;
  topic: string;
  payload: string;
  status: 'PENDING' | 'PUBLISHED' | 'FAILED';
  retryCount: number;
  createdAt: string;
  publishedAt: string | null;
}

export interface AuditLog {
  id: string;
  actorId: string | null;
  actorEmail: string | null;
  action: string;
  resourceType: string;
  resourceId: string | null;
  correlationId: string | null;
  details: string | null;
  createdAt: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface ApiErrorPayload {
  status: number;
  title?: string;
  detail?: string;
  correlationId?: string;
}
