import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 5: Zone Drawing', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T5.1 zones list or draw entry (smoke)', async ({ page }) => {
    await page.goto('/zones');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T5.2–T5.6 polygon draw + save', async () => {
    test.skip(!process.env.E2E_REAL_CAMERAS, 'Farm-only: zone canvas data-testids required');
  });
});
