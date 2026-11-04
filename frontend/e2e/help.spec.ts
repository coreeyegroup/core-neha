import { expect, test } from '@playwright/test';

test('the help centre lists both books and searches them', async ({ page }) => {
  await page.goto('./help');
  await expect(page.getByRole('heading', { name: 'Operation manual' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Developer guide' })).toBeVisible();
  await page.getByLabel('Search the help').fill('day close');
  await expect(page.getByRole('link', { name: /Accounts › Day close/ })).toBeVisible();
});

test('the help button opens the chapter for the current screen', async ({ page, context }) => {
  await context.addCookies([{ name: 'mock_persona', value: 'priya.reception', url: 'http://localhost:4173' }]);
  await page.goto('./reception');
  await page.getByRole('link', { name: 'Help for this screen' }).click();
  await expect(page.getByRole('heading', { name: 'Reception desk', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Checkout: bills, payment, sending the patient home' })).toBeVisible();
});
