import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api, formatMoney } from '../api/client';
import { StatusBadge } from '../components/StatusBadge';
import { JournalEntryTable } from '../components/JournalEntryTable';

export function LedgerExplorerPage() {
  const [page, setPage] = useState(0);
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const txQuery = useQuery({
    queryKey: ['transactions', page],
    queryFn: () => api.getTransactions(page, 12),
  });

  const rows = txQuery.data?.content ?? [];

  return (
    <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
      <div className="flex items-center justify-between">
        <div>
          <h2 className="text-base font-semibold text-white">Double-Entry Ledger Explorer</h2>
          <p className="text-xs text-slate-400">
            Click any journal transaction to verify balanced DEBIT and CREDIT postings.
          </p>
        </div>
        <div className="flex items-center gap-2 text-xs">
          <button
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
            className="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 text-slate-200"
          >
            Prev
          </button>
          <span className="text-slate-400 font-mono">
            Page {page + 1} / {Math.max(1, txQuery.data?.totalPages ?? 1)}
          </span>
          <button
            disabled={page + 1 >= (txQuery.data?.totalPages ?? 1)}
            onClick={() => setPage((p) => p + 1)}
            className="px-3 py-1 rounded bg-slate-800 disabled:opacity-40 text-slate-200"
          >
            Next
          </button>
        </div>
      </div>

      <div className="space-y-3">
        {rows.map((tx) => {
          const isOpen = expandedId === tx.id;
          return (
            <div key={tx.id} className="rounded-xl border border-slate-800 bg-slate-950/60 overflow-hidden">
              <button
                type="button"
                onClick={() => setExpandedId(isOpen ? null : tx.id)}
                className="w-full px-4 py-3 flex flex-wrap items-center justify-between gap-3 text-left hover:bg-slate-900/50 transition"
              >
                <div className="flex items-center gap-3">
                  <span className="font-mono text-xs font-semibold text-indigo-300">{tx.referenceId}</span>
                  <StatusBadge label={tx.transactionType} tone="slate" />
                  <StatusBadge
                    label={tx.status}
                    tone={tx.status === 'SETTLED' ? 'emerald' : 'indigo'}
                  />
                  <span className="text-xs text-slate-400">{tx.description}</span>
                </div>
                <div className="flex items-center gap-4">
                  <span className="font-mono text-xs text-slate-500">
                    {new Date(tx.createdAt).toLocaleString()}
                  </span>
                  <span className="font-mono text-sm font-semibold text-white">
                    {formatMoney(tx.totalAmount, tx.currency)}
                  </span>
                </div>
              </button>

              {isOpen && (
                <div className="px-4 pb-4 pt-2 border-t border-slate-800/80 space-y-2">
                  <div className="flex flex-wrap gap-4 text-[11px] font-mono text-slate-400">
                    <span>Tx ID: {tx.id}</span>
                    {tx.idempotencyKey && <span>Idempotency: {tx.idempotencyKey}</span>}
                    {tx.correlationId && <span>Correlation: {tx.correlationId}</span>}
                  </div>
                  <JournalEntryTable entries={tx.entries} />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
