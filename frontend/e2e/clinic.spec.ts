import { expect, test, type BrowserContext } from '@playwright/test';

// Vijaya Eye Clinic: each position signs in to its own department dashboard (mock personas, see e2e/clinic-mock.mjs).
async function as(context: BrowserContext, username: string) {
  await context.addCookies([{ name: 'mock_persona', value: username, url: 'http://localhost:4173' }]);
}

test('a receptionist lands on the reception desk and checks in an appointment', async ({ page, context }) => {
  await as(context, 'priya.reception');
  await page.goto('./');
  await expect(page.getByText('Receptionist · Reception · Vijaya Eye Clinic')).toBeVisible();
  await expect(page.getByRole('link', { name: /Reception desk/ }).first()).toBeVisible();
  await page.goto('./reception');
  await expect(page.getByRole('heading', { name: 'Reception desk' })).toBeVisible();
  await page.getByRole('button', { name: 'Check in' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Check in' }).click();
  await expect(page.getByText(/Token 7: Savitri Devi checked in/)).toBeVisible();
});

test('the optometrist sees the optometry queue, not the reception desk', async ({ page, context }) => {
  await as(context, 'sunita.optometry');
  await page.goto('./optometry');
  await expect(page.getByRole('heading', { name: 'Optometry' })).toBeVisible();
  await expect(page.getByText('Savitri Devi')).toBeVisible();
  await page.goto('./reception');
  await expect(page.getByText(/You do not have front-office access/)).toBeVisible();
});

test('the doctor sees optometry findings and the surgery advice on the visit', async ({ page, context }) => {
  await as(context, 'dr.vijay');
  await page.goto('./visits/0195f000-0000-7000-8000-000000004002');
  await expect(page.getByRole('heading', { name: /Token 3 · Fathima Begum/ })).toBeVisible();
  await expect(page.getByLabel('Unaided distance vision, right eye')).toHaveValue('6/60');
  await expect(page.getByLabel('Sphere (D), right eye')).toHaveValue('+1.00');
  await expect(page.getByLabel('Diagnosis')).toHaveValue('Immature senile cataract, right eye');
});

test('the accounts manager approves a discount requested at the desk', async ({ page, context }) => {
  await as(context, 'shobha.accounts');
  await page.goto('./accounts');
  await expect(page.getByText('₹38,950.00').first()).toBeVisible();
  await page.getByRole('tab', { name: /Discounts/ }).click();
  await page.getByRole('button', { name: 'Decide…' }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByText('Discount approved')).toBeVisible();
});
