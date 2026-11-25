import React, { useState } from 'react';
import { ShieldCheck, Lock, ArrowRight } from 'lucide-react';
import { useAuth } from '../context/AuthContext';

const DEMO_PERSONAS = [
  { label: 'Alice Chen', role: 'CUSTOMER', email: 'alice@veritaspay.io' },
  { label: 'Bob Rivera', role: 'CUSTOMER', email: 'bob@veritaspay.io' },
  { label: 'Apex Cloud', role: 'MERCHANT', email: 'merchant@apexcloud.io' },
  { label: 'Priya Nair', role: 'COMPLIANCE_OFFICER', email: 'compliance@veritaspay.io' },
  { label: 'Marcus Vance', role: 'ADMIN', email: 'admin@veritaspay.io' },
];

export function LoginPage() {
  const { login } = useAuth();
  const [email, setEmail] = useState('alice@veritaspay.io');
  const [password, setPassword] = useState('VeritasDemo@2026');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      await login(email, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  const quickLogin = async (personaEmail: string) => {
    setEmail(personaEmail);
    setPassword('VeritasDemo@2026');
    setError(null);
    setLoading(true);
    try {
      await login(personaEmail, 'VeritasDemo@2026');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Authentication failed');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex items-center justify-center p-6">
      <div className="w-full max-w-4xl grid md:grid-cols-2 gap-6 bg-slate-900/70 border border-slate-800 rounded-2xl p-8 shadow-2xl">
        <div className="flex flex-col justify-between space-y-6">
          <div>
            <div className="inline-flex items-center gap-2 px-3 py-1 rounded-full bg-indigo-500/15 border border-indigo-500/30 text-indigo-300 text-xs font-medium mb-4">
              <ShieldCheck className="w-4 h-4" />
              Double-Entry FinTech Core
            </div>
            <h1 className="text-3xl font-bold tracking-tight text-white">VeritasPay</h1>
            <p className="mt-2 text-sm text-slate-400 leading-relaxed">
              Production-grade Java 21 &amp; Spring Boot payment ledger with ACID double-entry
              bookkeeping, idempotency replay protection, deadlock-free locking, and Kafka outbox streaming.
            </p>
          </div>

          <div className="space-y-2">
            <div className="text-xs font-semibold uppercase tracking-wider text-slate-400">
              1-Click RBAC Demo Personas
            </div>
            <div className="grid grid-cols-1 gap-2">
              {DEMO_PERSONAS.map((p) => (
                <button
                  key={p.email}
                  type="button"
                  disabled={loading}
                  onClick={() => quickLogin(p.email)}
                  className="flex items-center justify-between px-3 py-2 rounded-lg bg-slate-950/70 hover:bg-slate-800 border border-slate-800 text-left transition"
                >
                  <div>
                    <div className="text-sm font-medium text-slate-100">{p.label}</div>
                    <div className="text-xs text-slate-400 font-mono">{p.email}</div>
                  </div>
                  <span className="text-[11px] px-2 py-0.5 rounded bg-indigo-500/15 text-indigo-300 border border-indigo-500/30 font-medium">
                    {p.role}
                  </span>
                </button>
              ))}
            </div>
          </div>
        </div>

        <form onSubmit={handleSubmit} className="flex flex-col justify-center space-y-4 bg-slate-950/60 p-6 rounded-xl border border-slate-800">
          <div className="flex items-center gap-2 text-white font-semibold text-lg">
            <Lock className="w-5 h-5 text-indigo-400" />
            Sign in to Console
          </div>

          {error && (
            <div role="alert" className="p-3 rounded-lg bg-rose-500/15 border border-rose-500/40 text-rose-300 text-xs">
              {error}
            </div>
          )}

          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1" htmlFor="email">
              Work / Demo Email
            </label>
            <input
              id="email"
              type="email"
              required
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <div>
            <label className="block text-xs font-medium text-slate-300 mb-1" htmlFor="password">
              Password
            </label>
            <input
              id="password"
              type="password"
              required
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              className="w-full px-3 py-2 rounded-lg bg-slate-900 border border-slate-700 text-sm text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full inline-flex items-center justify-center gap-2 py-2.5 px-4 rounded-lg bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 text-white text-sm font-semibold transition"
          >
            {loading ? 'Authenticating...' : 'Sign In'}
            <ArrowRight className="w-4 h-4" />
          </button>
        </form>
      </div>
    </div>
  );
}
