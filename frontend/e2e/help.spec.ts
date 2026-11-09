import { expect, test } from '@playwright/test';

test('an administrator sees the developer guide and can search', async ({ page }) => {
  await page.goto('./help');
  await expect(page.getByRole('heading', { name: 'Operation manual' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Developer guide' })).toBeVisible();
  await page.getByLabel('Search the help').fill('one-time code');
  await expect(page.getByText(/result/).first()).toBeVisible();
});

test('a receptionist sees only the help for their work', async ({ page, context }) => {
  await context.addCookies([{ name: 'mock_persona', value: 'priya.reception', url: 'http://localhost:4173' }]);
  await page.goto('./help');
  await expect(page.getByText('Receptionist, Reception at Vijaya Eye Clinic')).toBeVisible();
  await expect(page.getByRole('link', { name: /Reception desk/ }).last()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Developer guide' })).toHaveCount(0);
  await expect(page.getByRole('heading', { name: 'Optometry', exact: true })).toHaveCount(0);
  await page.goto('./help/developer/architecture');
  await expect(page.getByRole('heading', { name: 'This help page is not available to you' })).toBeVisible();
});

test('the help button opens the chapter for the current screen', async ({ page, context }) => {
  await context.addCookies([{ name: 'mock_persona', value: 'priya.reception', url: 'http://localhost:4173' }]);
  await page.goto('./reception');
  await page.getByRole('link', { name: 'Help for this screen' }).click();
  await expect(page.getByRole('heading', { name: 'Reception desk', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Checkout: bills, payment, sending the patient home' })).toBeVisible();
});

test('the access guide shows a receptionist their own access only', async ({ page, context }) => {
  await context.addCookies([{ name: 'mock_persona', value: 'priya.reception', url: 'http://localhost:4173' }]);
  await page.goto('./help');
  await page.getByRole('link', { name: /Access guide/ }).first().click();
  await expect(page.getByRole('heading', { name: 'Access guide', level: 1 })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'What you can do, and where' })).toBeVisible();
  await expect(page.getByText('Allows opd checkin')).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Roles' })).toHaveCount(0);
  await page.getByRole('tab', { name: 'How access works' }).click();
  await expect(page.getByRole('heading', { name: 'Two-person rules' })).toBeVisible();
});

test('the access guide shows an administrator the whole model', async ({ page }) => {
  await page.goto('./help/access');
  await page.getByRole('tab', { name: 'Roles' }).click();
  await expect(page.getByText('Eye clinic pathologist')).toBeVisible();
  await page.getByRole('tab', { name: 'Modules' }).click();
  await expect(page.getByText('Not licensed').or(page.getByText('Off')).first()).toBeVisible();
  await page.getByRole('tab', { name: 'Positions' }).click();
  await expect(page.getByText('Consultant pathologist')).toBeVisible();
  await page.getByRole('tab', { name: 'Menus' }).click();
  await expect(page.getByRole('cell', { name: 'Everyone signed in' }).first()).toBeVisible();
});
