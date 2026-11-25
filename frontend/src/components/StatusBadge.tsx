interface Props {
  label: string;
  tone?: 'emerald' | 'amber' | 'rose' | 'indigo' | 'slate';
}

const TONE_CLASSES: Record<NonNullable<Props['tone']>, string> = {
  emerald: 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30',
  amber: 'bg-amber-500/15 text-amber-300 border-amber-500/30',
  rose: 'bg-rose-500/15 text-rose-300 border-rose-500/30',
  indigo: 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30',
  slate: 'bg-slate-700/40 text-slate-300 border-slate-600/40',
};

export function StatusBadge({ label, tone = 'slate' }: Props) {
  return (
    <span
      className={`inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium border ${TONE_CLASSES[tone]}`}
    >
      {label}
    </span>
  );
}
