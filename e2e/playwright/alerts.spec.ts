import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 6: Alert Pipeline (dashboard slice)', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T6.2 alerts page loads', async ({ page }) => {
    await page.goto('/alerts');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T6.1,T6.3–T6.10 MQTT + WS + Android (cross-tier)', async () => {
    test.skip(true, 'Use pytest MQTT helpers + Maestro flows; see docs/E2E_REAL_HARDWARE_TEST_PLAN.md');
  });
});
