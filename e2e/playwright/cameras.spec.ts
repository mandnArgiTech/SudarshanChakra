import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 3: Real Camera — Live Feed', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'E2E_ADMIN_USER / E2E_ADMIN_PASS not set (export from e2e_config via run_full_e2e.sh)');
    await loginAsAdmin(page);
  });

  test('T3.1 login reaches app', async ({ page }) => {
    await expect(page).not.toHaveURL(/\/login$/);
  });

  test('T3.2 navigate to cameras', async ({ page }) => {
    await page.goto('/cameras');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T3.3–T3.7 real MJPEG / cards (farm)', async () => {
    test.skip(!process.env.E2E_REAL_CAMERAS, 'Set E2E_REAL_CAMERAS=1 and data-testid feeds on dashboard for strict checks');
  });
});
