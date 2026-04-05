import { type Page } from '@playwright/test';

const USER = process.env.E2E_ADMIN_USER || process.env.E2E_PLAYWRIGHT_USER || '';
const PASS = process.env.E2E_ADMIN_PASS || process.env.E2E_PLAYWRIGHT_PASS || '';

/**
 * Log in via dashboard /login using env credentials (set by run_full_e2e.sh from e2e_config or override).
 */
export async function loginAsAdmin(page: Page): Promise<void> {
  if (!USER || !PASS) {
    throw new Error('Set E2E_ADMIN_USER and E2E_ADMIN_PASS (or E2E_PLAYWRIGHT_*) for dashboard login');
  }
  await page.goto('/login');
  await page.getByPlaceholder('Enter username').fill(USER);
  await page.getByPlaceholder('Enter password').fill(PASS);
  await page.getByRole('button', { name: /sign in/i }).click();
  await page.waitForURL(/\/(dashboard|$)/, { timeout: 30_000 }).catch(() => {});
}

export function credentialsConfigured(): boolean {
  return Boolean(USER && PASS);
}
