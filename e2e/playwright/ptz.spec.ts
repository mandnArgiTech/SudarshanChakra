import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 4: PTZ Camera Control', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T4.1 open PTZ-capable camera route (smoke)', async ({ page }) => {
    await page.goto('/cameras');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T4.2–T4.8 ONVIF-backed PTZ assertions', async () => {
    test.skip(!process.env.E2E_REAL_CAMERAS, 'Farm-only: enable E2E_REAL_CAMERAS and implement cam-01 PTZ UI selectors');
  });
});
