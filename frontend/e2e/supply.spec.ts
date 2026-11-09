import { expect, test, type BrowserContext } from '@playwright/test';

// Pharmacy, optical shop, optical lab and stores at Vijaya Eye Clinic (mock personas, see e2e/clinic-mock.mjs).
async function as(context: BrowserContext, username: string) {
  await context.addCookies([{ name: 'mock_persona', value: username, url: 'http://localhost:4173' }]);
}

test('the pharmacist dispenses a waiting prescription', async ({ page, context }) => {
  await as(context, 'arvind.pharmacy');
  await page.goto('./pharmacy');
  await expect(page.getByRole('heading', { name: 'Pharmacy' })).toBeVisible();
  await page.getByText('VEC/RX/2026-27/000002').click();
  await expect(page.getByRole('dialog').getByText('Moxifloxacin 0.5% eye drops, 5 ml')).toBeVisible();
  await page.getByRole('button', { name: 'Dispense and bill' }).click();
  await expect(page.getByText(/Dispensed: sale VEC\/PH\/2026-27\/000002/)).toBeVisible();
});

test('the optical shop sees orders and holds delivery until paid', async ({ page, context }) => {
  await as(context, 'rohit.optical');
  await page.goto('./optical');
  await expect(page.getByRole('heading', { name: 'Optical shop' })).toBeVisible();
  await expect(page.getByText('Ready for patient').first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Deliver' })).toBeDisabled();
  await page.goto('./pharmacy');
  await expect(page.getByText(/You do not have pharmacy access/)).toBeVisible();
});

test('a lab technician moves a job on the lab board', async ({ page, context }) => {
  await as(context, 'dinesh.lab');
  await page.goto('./optical-lab');
  await expect(page.getByRole('heading', { name: 'Optical lab' })).toBeVisible();
  await page.getByRole('button', { name: /000002/ }).click();
  await expect(page.getByText('Specification (frozen when the order was placed)')).toBeVisible();
  await page.getByRole('button', { name: 'Fitting done, send to QC' }).click();
  await expect(page.getByText(/is now qc/)).toBeVisible();
});

test('the store keeper sees stock, expiry and the adjustment waiting for approval', async ({ page, context }) => {
  await as(context, 'nagaraj.stores');
  await page.goto('./inventory');
  await expect(page.getByRole('heading', { name: 'Inventory' })).toBeVisible();
  await expect(page.getByText('Moxifloxacin 0.5% eye drops, 5 ml').first()).toBeVisible();
  await page.getByRole('tab', { name: /Adjustments/ }).click();
  await expect(page.getByText(/Bottle cracked on the shelf/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Approve' })).toHaveCount(0);
});
