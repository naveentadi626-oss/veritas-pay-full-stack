import { expect, test } from '@playwright/test';

test('customer login and idempotent transfer replay flow', async ({ page }) => {
  await page.goto('/');
  await page.getByText('Alice Chen').click();

  await expect(page.getByText('Managed Liquidity')).toBeVisible();
  await page.getByRole('button', { name: /Transfer Simulator/i }).click();

  await page.getByRole('button', { name: /Execute Transfer/i }).click();
  await expect(page.getByText('HTTP 201')).toBeVisible();

  await page.getByRole('button', { name: /Simulate Network Retry/i }).click();
  await expect(page.getByText('X-Idempotent-Replayed: true')).toBeVisible();
});
