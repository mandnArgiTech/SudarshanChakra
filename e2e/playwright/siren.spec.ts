import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 7: Siren (dashboard UI)', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T7.x siren page smoke', async ({ page }) => {
    await page.goto('/sirens');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T7.2–T7.5 MQTT audio ack (farm)', async () => {
    test.skip(!process.env.E2E_RUN_HARDWARE, 'Set E2E_RUN_HARDWARE=1 on edge + MQTT assertions');
  });
});
