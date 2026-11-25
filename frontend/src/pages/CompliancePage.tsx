import { useQuery } from '@tanstack/react-query';
import { ShieldCheck, RefreshCw } from 'lucide-react';
import { api, formatMoney } from '../api/client';
import { StatusBadge } from '../components/StatusBadge';

export function CompliancePage() {
  const trialQuery = useQuery({ queryKey: ['trial-balance'], queryFn: api.getTrialBalance });
  const outboxQuery = useQuery({ queryKey: ['outbox'], queryFn: () => api.getOutboxEvents(0, 15) });
  const auditQuery = useQuery({ queryKey: ['audit'], queryFn: () => api.getAuditLogs(0, 15) });

  const trial = trialQuery.data;

  return (
    <div className="space-y-6">
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-5 h-5 text-emerald-400" />
            <h2 className="text-base font-semibold text-white">
              System-Wide Trial Balance Reconciliation
            </h2>
          </div>
          <button
            onClick={() => trialQuery.refetch()}
            className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-xs text-slate-200"
          >
            <RefreshCw className="w-3.5 h-3.5" /> Re-verify Invariant
          </button>
        </div>

        {trial && (
          <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
            <div className="p-4 rounded-lg bg-slate-950 border border-slate-800">
              <div className="text-xs text-slate-400">Invariant Status</div>
              <div className="mt-2">
                <StatusBadge
                  label={trial.balanced ? 'BALANCED (DEBIT == CREDIT)' : 'DISCREPANCY DETECTED'}
                  tone={trial.balanced ? 'emerald' : 'rose'}
                />
              </div>
            </div>
            <div className="p-4 rounded-lg bg-slate-950 border border-slate-800">
              <div className="text-xs text-slate-400">Total Debits</div>
              <div className="mt-1 text-lg font-mono font-bold text-amber-300">
                {formatMoney(trial.totalDebits)}
              </div>
            </div>
            <div className="p-4 rounded-lg bg-slate-950 border border-slate-800">
              <div className="text-xs text-slate-400">Total Credits</div>
              <div className="mt-1 text-lg font-mono font-bold text-emerald-300">
                {formatMoney(trial.totalCredits)}
              </div>
            </div>
            <div className="p-4 rounded-lg bg-slate-950 border border-slate-800">
              <div className="text-xs text-slate-400">Entries / Transactions</div>
              <div className="mt-1 text-lg font-mono font-bold text-white">
                {trial.totalEntries} / {trial.totalTransactions}
              </div>
            </div>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
          <h3 className="text-sm font-semibold text-white">Transactional Outbox (Kafka Relay)</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="text-slate-400 uppercase border-b border-slate-800">
                <tr>
                  <th className="py-2 pr-3">Event</th>
                  <th className="py-2 pr-3">Topic</th>
                  <th className="py-2 pr-3">Status</th>
                  <th className="py-2 text-right">Retries</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70">
                {(outboxQuery.data?.content ?? []).map((ev) => (
                  <tr key={ev.id}>
                    <td className="py-2 pr-3 font-mono text-indigo-300">{ev.eventType}</td>
                    <td className="py-2 pr-3 font-mono text-slate-400">{ev.topic}</td>
                    <td className="py-2 pr-3">
                      <StatusBadge
                        label={ev.status}
                        tone={ev.status === 'PUBLISHED' ? 'emerald' : ev.status === 'PENDING' ? 'amber' : 'rose'}
                      />
                    </td>
                    <td className="py-2 text-right font-mono text-slate-400">{ev.retryCount}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
          <h3 className="text-sm font-semibold text-white">Immutable Audit Log</h3>
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="text-slate-400 uppercase border-b border-slate-800">
                <tr>
                  <th className="py-2 pr-3">Action</th>
                  <th className="py-2 pr-3">Actor</th>
                  <th className="py-2">Details</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800/70">
                {(auditQuery.data?.content ?? []).map((log) => (
                  <tr key={log.id}>
                    <td className="py-2 pr-3 font-mono text-slate-200">{log.action}</td>
                    <td className="py-2 pr-3 text-slate-400">{log.actorEmail}</td>
                    <td className="py-2 text-slate-500 truncate max-w-[220px]" title={log.details ?? ''}>
                      {log.details}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}
