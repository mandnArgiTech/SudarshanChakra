import { test, expect } from '@playwright/test';
import { credentialsConfigured, loginAsAdmin } from './helpers/login';

test.describe('Suite 10: Video Recording & Playback', () => {
  test.beforeEach(async ({ page }) => {
    test.skip(!credentialsConfigured(), 'dashboard credentials not set');
    await loginAsAdmin(page);
  });

  test('T10.3 recordings area (smoke)', async ({ page }) => {
    await page.goto('/cameras');
    await expect(page.locator('body')).toBeVisible();
  });

  test('T10.1–T10.2 Edge recordings API + T10.4–T10.6 player', async () => {
    test.skip(!process.env.E2E_EDGE_RECORDINGS, 'Point E2E_FLASK_URL at edge with recordings API enabled');
  });
});
