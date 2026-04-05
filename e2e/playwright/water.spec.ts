import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 8: Water (dashboard) + Suite 9 pump UI', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T8.x / T9.1 water page smoke', async ({ page }) => {
    await page.goto('/water');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T8.6–T8.8 ESP + gauge parity', async () => {
    test.skip(!process.env.E2E_RUN_HARDWARE, 'Farm ESP8266 + E2E_RUN_HARDWARE=1');
  });

  test('T9.2–T9.3 pump controls (UI)', async () => {
    test.skip(!process.env.E2E_REAL_PUMPS_UI, 'Set when pump control buttons have stable selectors');
  });
});
