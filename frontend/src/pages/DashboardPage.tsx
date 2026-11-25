import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Plus, ArrowUpRight, Wallet, Activity } from 'lucide-react';
import { api, formatMoney } from '../api/client';
import { useAuth } from '../context/AuthContext';
import { StatusBadge } from '../components/StatusBadge';

export function DashboardPage({ onNavigateTransfer }: { onNavigateTransfer: () => void }) {
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const [newAccName, setNewAccName] = useState('');
  const [topUpId, setTopUpId] = useState<string | null>(null);
  const [topUpAmount, setTopUpAmount] = useState('500');

  const accountsQuery = useQuery({ queryKey: ['accounts'], queryFn: api.getAccounts });
  const txQuery = useQuery({ queryKey: ['transactions', 0], queryFn: () => api.getTransactions(0, 6) });

  const createMutation = useMutation({
    mutationFn: () =>
      api.createAccount(
        newAccName,
        user?.role === 'MERCHANT' ? 'MERCHANT_SETTLEMENT' : 'USER_WALLET'
      ),
    onSuccess: () => {
      setNewAccName('');
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['directory'] });
    },
  });

  const topUpMutation = useMutation({
    mutationFn: ({ id, amount }: { id: string; amount: number }) =>
      api.topUp(id, amount, 'Instant Treasury Funding'),
    onSuccess: () => {
      setTopUpId(null);
      queryClient.invalidateQueries({ queryKey: ['accounts'] });
      queryClient.invalidateQueries({ queryKey: ['transactions'] });
    },
  });

  const accounts = accountsQuery.data ?? [];
  const totalBalance = accounts
    .filter((a) => a.category !== 'SYSTEM_TREASURY')
    .reduce((sum, a) => sum + Number(a.balance), 0);

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 text-xs uppercase tracking-wider">
            <span>Managed Liquidity</span>
            <Wallet className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="mt-2 text-2xl font-bold text-white font-mono">
            {formatMoney(totalBalance)}
          </div>
          <div className="mt-1 text-xs text-slate-500">Across {accounts.length} ledger accounts</div>
        </div>

        <div className="p-5 rounded-xl bg-slate-900 border border-slate-800">
          <div className="flex items-center justify-between text-slate-400 text-xs uppercase tracking-wider">
            <span>Recent Journal Postings</span>
            <Activity className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="mt-2 text-2xl font-bold text-white font-mono">
            {txQuery.data?.totalElements ?? 0}
          </div>
          <div className="mt-1 text-xs text-slate-500">ACID-verified double-entry transactions</div>
        </div>

        <div className="p-5 rounded-xl bg-gradient-to-br from-indigo-950/70 to-slate-900 border border-indigo-800/40 flex flex-col justify-between">
          <div>
            <div className="text-xs uppercase tracking-wider text-indigo-300 font-semibold">
              Idempotent Payment Engine
            </div>
            <p className="mt-1 text-xs text-slate-300">
              Test concurrent-safe transfers, 1% merchant fee splits, and retry deduplication.
            </p>
          </div>
          <button
            onClick={onNavigateTransfer}
            className="mt-3 inline-flex items-center gap-1.5 text-xs font-semibold text-indigo-300 hover:text-white"
          >
            Open Transfer Simulator <ArrowUpRight className="w-4 h-4" />
          </button>
        </div>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-base font-semibold text-white">Ledger Accounts</h2>
          {user?.role !== 'COMPLIANCE_OFFICER' && (
            <form
              onSubmit={(e) => {
                e.preventDefault();
                if (newAccName.trim().length >= 2) createMutation.mutate();
              }}
              className="flex items-center gap-2"
            >
              <input
                type="text"
                placeholder="New wallet name..."
                value={newAccName}
                onChange={(e) => setNewAccName(e.target.value)}
                className="px-3 py-1.5 rounded-lg bg-slate-950 border border-slate-700 text-xs text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
              <button
                type="submit"
                disabled={createMutation.isPending || newAccName.trim().length < 2}
                className="inline-flex items-center gap-1 px-3 py-1.5 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-xs font-medium text-white"
              >
                <Plus className="w-3.5 h-3.5" /> Open Account
              </button>
            </form>
          )}
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
          {accounts.map((acc) => (
            <div key={acc.id} className="p-4 rounded-xl bg-slate-950/70 border border-slate-800 space-y-3">
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="text-sm font-semibold text-white">{acc.name}</div>
                  <div className="text-xs font-mono text-slate-500">{acc.accountNumber}</div>
                </div>
                <StatusBadge label={acc.normalBalance} tone={acc.normalBalance === 'CREDIT' ? 'emerald' : 'amber'} />
              </div>

              <div className="text-xl font-bold font-mono text-white">
                {formatMoney(acc.balance, acc.currency)}
              </div>

              <div className="flex items-center justify-between pt-2 border-t border-slate-800/80 text-xs">
                <span className="text-slate-400">{acc.category}</span>
                {(acc.category === 'USER_WALLET' || acc.category === 'MERCHANT_SETTLEMENT') &&
                  user?.role !== 'COMPLIANCE_OFFICER' && (
                    <button
                      onClick={() => setTopUpId(topUpId === acc.id ? null : acc.id)}
                      className="text-indigo-400 hover:text-indigo-300 font-medium"
                    >
                      + Top Up
                    </button>
                  )}
              </div>

              {topUpId === acc.id && (
                <div className="flex items-center gap-2 pt-2">
                  <input
                    type="number"
                    min="1"
                    step="0.01"
                    value={topUpAmount}
                    onChange={(e) => setTopUpAmount(e.target.value)}
                    className="w-24 px-2 py-1 rounded bg-slate-900 border border-slate-700 text-xs text-white font-mono"
                  />
                  <button
                    onClick={() => topUpMutation.mutate({ id: acc.id, amount: Number(topUpAmount) })}
                    disabled={topUpMutation.isPending}
                    className="px-2.5 py-1 rounded bg-emerald-600 hover:bg-emerald-500 text-xs font-medium text-white"
                  >
                    Fund
                  </button>
                </div>
              )}
            </div>
          ))}
        </div>
      </div>

      <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-3">
        <h2 className="text-base font-semibold text-white">Latest Transactions</h2>
        <div className="overflow-x-auto">
          <table className="w-full text-left text-xs">
            <thead className="text-slate-400 uppercase border-b border-slate-800">
              <tr>
                <th className="py-2 pr-4">Reference</th>
                <th className="py-2 pr-4">Type</th>
                <th className="py-2 pr-4">Description</th>
                <th className="py-2 pr-4">Status</th>
                <th className="py-2 text-right">Amount</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-800/70">
              {(txQuery.data?.content ?? []).map((tx) => (
                <tr key={tx.id}>
                  <td className="py-2.5 pr-4 font-mono text-indigo-300">{tx.referenceId}</td>
                  <td className="py-2.5 pr-4 text-slate-300">{tx.transactionType}</td>
                  <td className="py-2.5 pr-4 text-slate-400">{tx.description}</td>
                  <td className="py-2.5 pr-4">
                    <StatusBadge
                      label={tx.status}
                      tone={tx.status === 'SETTLED' ? 'emerald' : 'indigo'}
                    />
                  </td>
                  <td className="py-2.5 text-right font-mono text-white">
                    {formatMoney(tx.totalAmount, tx.currency)}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}
