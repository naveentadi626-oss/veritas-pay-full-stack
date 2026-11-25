import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { JournalEntryTable } from '../components/JournalEntryTable';
import { LedgerEntryView } from '../types';

describe('JournalEntryTable', () => {
  it('renders balanced DEBIT and CREDIT entries with formatted currency', () => {
    const entries: LedgerEntryView[] = [
      {
        id: '1',
        accountId: 'acc-1',
        accountNumber: 'VP-10010001',
        accountName: 'Alice Operating Wallet',
        entryType: 'DEBIT',
        amount: 100,
        currency: 'USD',
        balanceAfter: 900,
        createdAt: '2026-11-25T00:00:00Z',
      },
      {
        id: '2',
        accountId: 'acc-2',
        accountNumber: 'VP-20010001',
        accountName: 'Apex Cloud Settlement',
        entryType: 'CREDIT',
        amount: 99,
        currency: 'USD',
        balanceAfter: 1099,
        createdAt: '2026-11-25T00:00:00Z',
      },
      {
        id: '3',
        accountId: 'acc-3',
        accountNumber: 'VP-FEES-01',
        accountName: 'Platform Fee Revenue',
        entryType: 'CREDIT',
        amount: 1,
        currency: 'USD',
        balanceAfter: 11,
        createdAt: '2026-11-25T00:00:00Z',
      },
    ];

    render(<JournalEntryTable entries={entries} />);

    expect(screen.getByText('Alice Operating Wallet')).toBeInTheDocument();
    expect(screen.getByText('Apex Cloud Settlement')).toBeInTheDocument();
    expect(screen.getByText('Platform Fee Revenue')).toBeInTheDocument();
    expect(screen.getByText('$100.00')).toBeInTheDocument();
    expect(screen.getByText('$99.00')).toBeInTheDocument();
    expect(screen.getByText('$1.00')).toBeInTheDocument();
  });
});
