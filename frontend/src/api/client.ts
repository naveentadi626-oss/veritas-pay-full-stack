import {
  ApiErrorPayload,
  AuditLog,
  AuthResponse,
  JournalTransaction,
  LedgerAccount,
  OutboxEvent,
  PageResponse,
  TransferResult,
  TrialBalanceReport,
} from '../types';

const TOKEN_STORAGE_KEY = 'veritas_auth_session';

export class ApiError extends Error {
  public readonly status: number;
  public readonly code: string;
  public readonly correlationId?: string;

  constructor(payload: ApiErrorPayload) {
    super(payload.detail || payload.title || 'Request failed');
    this.status = payload.status;
    this.code = payload.title || 'API_ERROR';
    this.correlationId = payload.correlationId;
  }
}

export function getStoredSession(): AuthResponse | null {
  const raw = localStorage.getItem(TOKEN_STORAGE_KEY);
  if (!raw) return null;
  try {
    return JSON.parse(raw) as AuthResponse;
  } catch {
    return null;
  }
}

export function setStoredSession(session: AuthResponse | null): void {
  if (!session) {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
  } else {
    localStorage.setItem(TOKEN_STORAGE_KEY, JSON.stringify(session));
  }
}

async function requestJson<T>(
  path: string,
  options: RequestInit = {},
  retryOnAuth = true
): Promise<{ data: T; status: number; headers: Headers }> {
  const session = getStoredSession();
  const headers = new Headers(options.headers || {});
  if (!headers.has('Content-Type') && options.body) {
    headers.set('Content-Type', 'application/json');
  }
  if (!headers.has('X-Correlation-Id')) {
    headers.set('X-Correlation-Id', crypto.randomUUID());
  }
  if (session?.accessToken && !headers.has('Authorization')) {
    headers.set('Authorization', `Bearer ${session.accessToken}`);
  }

  const res = await fetch(path, { ...options, headers });

  if (res.status === 401 && retryOnAuth && session?.refreshToken) {
    const refreshed = await tryRefreshToken(session.refreshToken);
    if (refreshed) {
      headers.set('Authorization', `Bearer ${refreshed.accessToken}`);
      const retryRes = await fetch(path, { ...options, headers });
      if (!retryRes.ok) {
        const errBody = await parseProblem(retryRes);
        throw new ApiError(errBody);
      }
      return { data: (await retryRes.json()) as T, status: retryRes.status, headers: retryRes.headers };
    }
  }

  if (!res.ok) {
    const errBody = await parseProblem(res);
    throw new ApiError(errBody);
  }

  return { data: (await res.json()) as T, status: res.status, headers: res.headers };
}

async function parseProblem(res: Response): Promise<ApiErrorPayload> {
  try {
    const json = (await res.json()) as Partial<ApiErrorPayload>;
    return {
      status: res.status,
      title: json.title,
      detail: json.detail,
      correlationId: json.correlationId,
    };
  } catch {
    return { status: res.status, detail: res.statusText };
  }
}

async function tryRefreshToken(refreshToken: string): Promise<AuthResponse | null> {
  try {
    const res = await fetch('/api/v1/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken }),
    });
    if (!res.ok) {
      setStoredSession(null);
      return null;
    }
    const nextSession = (await res.json()) as AuthResponse;
    setStoredSession(nextSession);
    return nextSession;
  } catch {
    return null;
  }
}

export const api = {
  login: async (email: string, password: string) => {
    const { data } = await requestJson<AuthResponse>(
      '/api/v1/auth/login',
      { method: 'POST', body: JSON.stringify({ email, password }) },
      false
    );
    return data;
  },

  getAccounts: async () => {
    const { data } = await requestJson<LedgerAccount[]>('/api/v1/accounts');
    return data;
  },

  getDirectory: async () => {
    const { data } = await requestJson<LedgerAccount[]>('/api/v1/accounts/directory');
    return data;
  },

  createAccount: async (name: string, category: string) => {
    const { data } = await requestJson<LedgerAccount>('/api/v1/accounts', {
      method: 'POST',
      body: JSON.stringify({ name, category, currency: 'USD' }),
    });
    return data;
  },

  topUp: async (destinationAccountId: string, amount: number, description: string) => {
    const { data } = await requestJson<TransferResult>('/api/v1/payments/top-ups', {
      method: 'POST',
      body: JSON.stringify({ destinationAccountId, amount, currency: 'USD', description }),
    });
    return data;
  },

  executeTransfer: async (
    idempotencyKey: string,
    payload: {
      sourceAccountId: string;
      destinationAccountId: string;
      amount: number;
      currency: string;
      description: string;
      merchantPayment: boolean;
    }
  ) => {
    return requestJson<TransferResult>('/api/v1/payments/transfers', {
      method: 'POST',
      headers: { 'Idempotency-Key': idempotencyKey },
      body: JSON.stringify(payload),
    });
  },

  getTransactions: async (page = 0, size = 15) => {
    const { data } = await requestJson<PageResponse<JournalTransaction>>(
      `/api/v1/ledger/transactions?page=${page}&size=${size}`
    );
    return data;
  },

  getTrialBalance: async () => {
    const { data } = await requestJson<TrialBalanceReport>('/api/v1/ledger/reconciliation/trial-balance');
    return data;
  },

  getOutboxEvents: async (page = 0, size = 20) => {
    const { data } = await requestJson<PageResponse<OutboxEvent>>(
      `/api/v1/payments/outbox?page=${page}&size=${size}`
    );
    return data;
  },

  getAuditLogs: async (page = 0, size = 25) => {
    const { data } = await requestJson<PageResponse<AuditLog>>(
      `/api/v1/audit/logs?page=${page}&size=${size}`
    );
    return data;
  },
};

export function formatMoney(amount: number, currency = 'USD'): string {
  return new Intl.NumberFormat('en-US', {
    style: 'currency',
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}
