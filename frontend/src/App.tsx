import { useState } from 'react';
import { LayoutDashboard, Repeat, BookOpen, ShieldAlert, LogOut } from 'lucide-react';
import { useAuth } from './context/AuthContext';
import { LoginPage } from './pages/LoginPage';
import { DashboardPage } from './pages/DashboardPage';
import { TransferSimulatorPage } from './pages/TransferSimulatorPage';
import { LedgerExplorerPage } from './pages/LedgerExplorerPage';
import { CompliancePage } from './pages/CompliancePage';
import { StatusBadge } from './components/StatusBadge';

type TabId = 'dashboard' | 'transfer' | 'ledger' | 'compliance';

export default function App() {
  const { isAuthenticated, user, logout } = useAuth();
  const [activeTab, setActiveTab] = useState<TabId>('dashboard');

  if (!isAuthenticated || !user) {
    return <LoginPage />;
  }

  const isPrivileged = user.role === 'ADMIN' || user.role === 'COMPLIANCE_OFFICER';

  const navItems: { id: TabId; label: string; icon: typeof LayoutDashboard; hidden?: boolean }[] = [
    { id: 'dashboard', label: 'Overview & Wallets', icon: LayoutDashboard },
    { id: 'transfer', label: 'Transfer Simulator', icon: Repeat, hidden: user.role === 'COMPLIANCE_OFFICER' },
    { id: 'ledger', label: 'Ledger Explorer', icon: BookOpen },
    { id: 'compliance', label: 'Trial Balance & Outbox', icon: ShieldAlert, hidden: !isPrivileged },
  ];

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col">
      <header className="border-b border-slate-800 bg-slate-900/70 backdrop-blur sticky top-0 z-10">
        <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 flex items-center justify-between gap-4">
          <div className="flex items-center gap-6">
            <span className="text-lg font-bold tracking-tight text-white">
              Veritas<span className="text-indigo-400">Pay</span>
            </span>
            <nav className="flex items-center gap-1">
              {navItems
                .filter((item) => !item.hidden)
                .map((item) => {
                  const Icon = item.icon;
                  const active = activeTab === item.id;
                  return (
                    <button
                      key={item.id}
                      onClick={() => setActiveTab(item.id)}
                      className={`inline-flex items-center gap-2 px-3 py-1.5 rounded-lg text-xs font-medium transition ${
                        active
                          ? 'bg-indigo-600 text-white'
                          : 'text-slate-400 hover:text-white hover:bg-slate-800'
                      }`}
                    >
                      <Icon className="w-4 h-4" />
                      {item.label}
                    </button>
                  );
                })}
            </nav>
          </div>

          <div className="flex items-center gap-3">
            <div className="hidden sm:block text-right">
              <div className="text-xs font-medium text-white">{user.fullName}</div>
              <div className="text-[11px] font-mono text-slate-400">{user.email}</div>
            </div>
            <StatusBadge label={user.role} tone="indigo" />
            <button
              onClick={logout}
              title="Sign out"
              className="p-2 rounded-lg text-slate-400 hover:text-white hover:bg-slate-800"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        </div>
      </header>

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 py-6">
        {activeTab === 'dashboard' && (
          <DashboardPage onNavigateTransfer={() => setActiveTab('transfer')} />
        )}
        {activeTab === 'transfer' && <TransferSimulatorPage />}
        {activeTab === 'ledger' && <LedgerExplorerPage />}
        {activeTab === 'compliance' && isPrivileged && <CompliancePage />}
      </main>
    </div>
  );
}
