import { LedgerEntryView } from '../types';
import { formatMoney } from '../api/client';
import { StatusBadge } from './StatusBadge';

export function JournalEntryTable({ entries }: { entries: LedgerEntryView[] }) {
  return (
    <div className="overflow-x-auto rounded-lg border border-slate-800 bg-slate-950/60">
      <table className="w-full text-left text-xs">
        <thead className="bg-slate-900/80 text-slate-400 uppercase tracking-wider">
          <tr>
            <th className="px-3 py-2">Account</th>
            <th className="px-3 py-2">Side</th>
            <th className="px-3 py-2 text-right">Amount</th>
            <th className="px-3 py-2 text-right">Balance After</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800/80">
          {entries.map((entry) => (
            <tr key={entry.id} className="hover:bg-slate-900/40">
              <td className="px-3 py-2">
                <div className="font-medium text-slate-200">{entry.accountName}</div>
                <div className="font-mono text-[11px] text-slate-500">{entry.accountNumber}</div>
              </td>
              <td className="px-3 py-2">
                <StatusBadge
                  label={entry.entryType}
                  tone={entry.entryType === 'DEBIT' ? 'amber' : 'emerald'}
                />
              </td>
              <td className="px-3 py-2 text-right font-mono text-slate-100">
                {formatMoney(entry.amount, entry.currency)}
              </td>
              <td className="px-3 py-2 text-right font-mono text-slate-400">
                {formatMoney(entry.balanceAfter, entry.currency)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
