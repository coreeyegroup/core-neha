import { expect, test, type BrowserContext } from '@playwright/test';

// Laboratory and imaging at Vijaya Eye Clinic (mock personas, see e2e/clinic-mock.mjs).
async function as(context: BrowserContext, username: string) {
  await context.addCookies([{ name: 'mock_persona', value: username, url: 'http://localhost:4173' }]);
}

test('the pathologist validates a result and sees the critical value', async ({ page, context }) => {
  await as(context, 'sudha.lab');
  await page.goto('./laboratory');
  await expect(page.getByRole('heading', { name: 'Laboratory' })).toBeVisible();
  await page.getByRole('tab', { name: /To validate/ }).click();
  await expect(page.getByText('critical').first()).toBeVisible();
  await page.getByText('Fasting blood sugar').click();
  await expect(page.getByRole('dialog').getByText('High')).toBeVisible();
  await page.getByRole('button', { name: 'Validate and release' }).click();
  await expect(page.getByText(/released/i).first()).toBeVisible();
});

test('the radiologist sees studies to report with a link to the image viewer', async ({ page, context }) => {
  await as(context, 'harish.radiology');
  await page.goto('./imaging');
  await expect(page.getByRole('heading', { name: 'Imaging' })).toBeVisible();
  await page.getByRole('tab', { name: /To report/ }).click();
  await expect(page.getByRole('link', { name: 'Open viewer' })).toHaveAttribute('href', /api\/pacs\/ohif\/viewer\?StudyInstanceUIDs=2\.25\.5678/);
  await page.goto('./laboratory');
  await expect(page.getByText(/You do not have laboratory access/)).toBeVisible();
});
