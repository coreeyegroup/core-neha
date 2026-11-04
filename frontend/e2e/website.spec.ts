import { expect, test } from '@playwright/test';

// The public website at <base>/ (the staff app is at <base>/staff/).
test('the website shows the clinic, its specialities and a staff login', async ({ page }) => {
  await page.goto('/core-neha/');
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Vijaya Eye Clinic' }).first()).toBeVisible();
  await page.goto('/core-neha/specialities');
  await expect(page.getByRole('heading', { name: 'Cataract surgery', level: 2 })).toBeVisible();
  await page.goto('/core-neha/doctors');
  await expect(page.getByRole('heading', { name: 'Dr. Vasant N Patil' })).toBeVisible();
  await page.goto('/core-neha/contact');
  await page.getByRole('main').getByRole('link', { name: 'Staff login' }).click();
  await expect(page).toHaveURL(/\/core-neha\/staff\/$/);
});
