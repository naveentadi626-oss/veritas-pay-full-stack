import { useEffect, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Send, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { api, ApiError, formatMoney } from '../api/client';
import { TransferResult } from '../types';
import { StatusBadge } from '../components/StatusBadge';
import { JournalEntryTable } from '../components/JournalEntryTable';

export function TransferSimulatorPage() {
  const queryClient = useQueryClient();
  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: api.getAccounts });
  const directoryQuery = useQuery({ queryKey: ['directory'], queryFn: api.getDirectory });

  const myAccounts = (accountsQuery.data ?? []).filter(
    (a) => a.category === 'USER_WALLET' || a.category === 'MERCHANT_SETTLEMENT'
  );
  const directory = directoryQuery.data ?? [];

  const [sourceId, setSourceId] = useState('');
  const [destId, setDestId] = useState('');
  const [amount, setAmount] = useState('250.00');
  const [description, setDescription] = useState('Enterprise API Settlement');
  const [merchantPayment, setMerchantPayment] = useState(true);
  const [idempotencyKey, setIdempotencyKey] = useState(() => `idem-${crypto.randomUUID().slice(0, 12)}`);
  const [lastResponse, setLastResponse] = useState<{
    status: number;
    replayedHeader: string | null;
    result: TransferResult;
  } | null>(null);
  const [errorBox, setErrorBox] = useState<{ status: number; code: string; message: string } | null>(null);

  useEffect(() => {
    if (!sourceId && myAccounts.length > 0) setSourceId(myAccounts[0].id);
  }, [myAccounts, sourceId]);

  useEffect(() => {
    if (!destId && directory.length > 0 && sourceId) {
      const candidate = directory.find((a) => a.id !== sourceId);
      if (candidate) setDestId(candidate.id);
    }
  }, [directory, sourceId, destId]);

  const transferMutation = useMutation({
    mutationFn: (overrideAmount?: number) =>
      api.executeTransfer(idempotencyKey, {
        sourceAccountId: sourceId,
        destinationAccountId: destId,
        amount: overrideAmount ?? Number(amount),
        currency: 'USD',
        description,
        merchantPayment,
      }),
    onSuccess: (res) => {
      setErrorBox(null);
      setLastResponse({
        status: res.status,
        replayedHeader: res.headers.get('X-Idempotent-Replayed'),
        result: res.data,
      });
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
    onError: (err) => {
      if (err instanceof ApiError) {
        setErrorBox({ status: err.status, code: err.code, message: err.message });
      } else {
        setErrorBox({ status: 500, code: 'UNKNOWN', message: 'Transfer failed' });
      }
    },
  });

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
      <div className="lg:col-span-5 bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
        <div>
          <h2 className="text-base font-semibold text-white">Idempotent Transfer Simulator</h2>
          <p className="text-xs text-slate-400 mt-1">
            Demonstrates RFC-style Idempotency-Key deduplication, payload hash verification, and
            double-entry fee splitting.
          </p>
        </div>

        <div className="space-y-3">
          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Source Account</label>
            <select
              value={sourceId}
              onChange={(e) => setSourceId(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs text-white"
            >
              {myAccounts.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name} ({a.accountNumber}) — {formatMoney(a.balance)}
                </option>
              ))}
            </select>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Destination Account</label>
            <select
              value={destId}
              onChange={(e) => setDestId(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs text-white"
            >
              {directory
                .filter((a) => a.id !== sourceId)
                .map((a) => (
                  <option key={a.id} value={a.id}>
                    {a.name} ({a.accountNumber}) [{a.category}]
                  </option>
                ))}
            </select>
          </div>

          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1">Amount (USD)</label>
              <input
                type="number"
                step="0.01"
                min="0.01"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs font-mono text-white"
              />
            </div>

            <div className="flex items-end pb-2">
              <label className="inline-flex items-center gap-2 text-xs text-slate-300 cursor-pointer">
                <input
                  type="checkbox"
                  checked={merchantPayment}
                  onChange={(e) => setMerchantPayment(e.target.checked)}
                  className="rounded border-slate-700 bg-slate-950 text-indigo-600"
                />
                Merchant Payment (1% Fee)
              </label>
            </div>
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1">Description</label>
            <input
              type="text"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs text-white"
            />
          </div>

          <div>
            <div className="flex items-center justify-between mb-1">
              <label className="text-xs font-medium text-slate-300">Idempotency-Key Header</label>
              <button
                type="button"
                onClick={() => setIdempotencyKey(`idem-${crypto.randomUUID().slice(0, 12)}`)}
                className="text-[11px] text-indigo-400 hover:text-indigo-300 inline-flex items-center gap-1"
              >
                <RefreshCw className="w-3 h-3" /> New Key
              </button>
            </div>
            <input
              type="text"
              value={idempotencyKey}
              onChange={(e) => setIdempotencyKey(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-950 border border-slate-700 text-xs font-mono text-indigo-300"
            />
          </div>
        </div>

        <div className="pt-2 space-y-2">
          <button
            type="button"
            disabled={transferMutation.isPending || !sourceId || !destId}
            onClick={() => transferMutation.mutate(undefined)}
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-xs font-semibold text-white"
          >
            <Send className="w-4 h-4" />
            Execute Transfer (Uses Current Key)
          </button>

          {lastResponse && (
            <div className="grid grid-cols-2 gap-2">
              <button
                type="button"
                disabled={transferMutation.isPending}
                onClick={() => transferMutation.mutate(undefined)}
                className="py-2 px-3 rounded-lg bg-emerald-600/20 hover:bg-emerald-600/30 border border-emerald-500/40 text-emerald-300 text-xs font-medium"
              >
                Simulate Network Retry
              </button>
              <button
                type="button"
                disabled={transferMutation.isPending}
                onClick={() => transferMutation.mutate(Number(amount) + 10)}
                className="py-2 px-3 rounded-lg bg-rose-600/20 hover:bg-rose-600/30 border border-rose-500/40 text-rose-300 text-xs font-medium"
              >
                Test Tampered Amount (409)
              </button>
            </div>
          )}
        </div>
      </div>

      <div className="lg:col-span-7 space-y-4">
        {errorBox && (
          <div className="p-4 rounded-xl bg-rose-950/50 border border-rose-500/40 flex items-start gap-3">
            <AlertTriangle className="w-5 h-5 text-rose-400 shrink-0 mt-0.5" />
            <div className="text-xs space-y-1">
              <div className="font-semibold text-rose-200">
                HTTP {errorBox.status} — {errorBox.code}
              </div>
              <div className="text-rose-300">{errorBox.message}</div>
            </div>
          </div>
        )}

        {lastResponse ? (
          <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <CheckCircle2 className="w-5 h-5 text-emerald-400" />
                <span className="text-sm font-semibold text-white">
                  {lastResponse.result.transaction.referenceId}
                </span>
                <StatusBadge
                  label={`HTTP ${lastResponse.status}`}
                  tone={lastResponse.status === 201 ? 'emerald' : 'amber'}
                />
                {lastResponse.result.idempotentReplayed && (
                  <StatusBadge label="X-Idempotent-Replayed: true" tone="amber" />
                )}
              </div>
              <span className="text-xs font-mono text-slate-400">
                Fee: {formatMoney(lastResponse.result.feeApplied)}
              </span>
            </div>

            <div className="text-xs text-slate-400">
              {lastResponse.result.idempotentReplayed
                ? 'Duplicate request detected via SHA-256 fingerprint. Cached transaction returned with ZERO duplicate balance mutation.'
                : 'New balanced journal transaction committed atomically and outbox event queued for Kafka settlement.'}
            </div>

            <JournalEntryTable entries={lastResponse.result.transaction.entries} />
          </div>
        ) : (
          <div className="h-full min-h-[260px] flex items-center justify-center rounded-xl border border-dashed border-slate-800 text-xs text-slate-500 p-8 text-center">
            Submit a transfer on the left to inspect its HTTP status, Idempotency replay header,
            and balanced DEBIT / CREDIT double-entry ledger lines.
          </div>
        )}
      </div>
    </div>
  );
}
