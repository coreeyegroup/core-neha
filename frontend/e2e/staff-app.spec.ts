import { expect, test } from '@playwright/test';

test('overview shows the demonstration notice and permission-driven navigation', async ({ page, isMobile }) => {
  await page.goto('./');
  await expect(page.getByRole('heading', { name: 'Welcome, Asha Admin' })).toBeVisible();
  await expect(page.getByText(/demonstration environment with synthetic people/i)).toBeVisible();
  if (isMobile) {
    await page.getByRole('button', { name: 'Open navigation' }).click();
  }
  await expect(page.getByRole('link', { name: 'Audit trail' })).toBeVisible();
});

test('centre workspace lists only that centre’s departments', async ({ page }) => {
  await page.goto('./centres');
  await page.getByRole('cell', { name: 'Indiranagar Eye Hospital' }).click();
  await expect(page.getByRole('heading', { name: 'Indiranagar Eye Hospital' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Surgery and day-care' })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Optical manufacturing' })).toHaveCount(0);
});

test('an approver reviews and approves a pending request', async ({ page }) => {
  await page.goto('./approvals');
  await page.getByRole('button', { name: 'Approve…' }).click();
  await page.getByLabel('Decision note').fill('Confirmed with the centre manager');
  await page.getByRole('button', { name: 'Approve', exact: true }).click();
  await expect(page.getByText(/now has this access/)).toBeVisible();
});

test('a refused request shows the rule and its code', async ({ page }) => {
  await page.goto('./users/0195f000-0000-7000-8000-000000000002');
  await page.getByRole('button', { name: 'Request access…' }).click();
  await page.getByLabel('Reason').fill('test');
  await page.getByRole('button', { name: 'Submit request' }).click();
  await expect(page.getByText('iam.assignment.self')).toBeVisible();
});

test('role approval shows the impact before publishing', async ({ page }) => {
  await page.goto('./roles/0195f000-0000-7000-8000-000000000402');
  await page.getByRole('button', { name: 'Review and approve…' }).click();
  await expect(page.getByText(/changes the access of 1 user/)).toBeVisible();
  await expect(page.getByText('audit.read').last()).toBeVisible();
});

test('an administrator reads the organisation licence and its modules', async ({ page }) => {
  await page.goto('./licence');
  await expect(page.getByRole('heading', { name: 'Licence' })).toBeVisible();
  await expect(page.getByText('LIC-DEMO')).toBeVisible();
  await expect(page.getByText('21 in use of 25 licensed')).toBeVisible();
  await expect(page.getByRole('row', { name: /Optical manufacturing/ }).getByText('Licensed')).toBeVisible();
});
