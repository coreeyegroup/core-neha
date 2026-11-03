import { expect, test } from '@playwright/test';

test('the signed-out page offers sign-in and does not call the API as a user', async ({ page }) => {
  await page.goto('./signed-out');
  await expect(page.getByText('You have signed out.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Sign in' })).toBeVisible();
});
