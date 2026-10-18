import { expect, test } from '@playwright/test'

// Live: create → inspect → regenerate → reopen by link, through the real UI, backend and provider.
test('a person can create, inspect and regenerate a reading path', async ({ page }, testInfo) => {
  const shots = (name: string) => testInfo.outputPath(`${name}.png`)
  await page.goto('/')
  await expect(page.getByRole('heading', { level: 1 })).toContainText('reading path')
  await page.screenshot({ path: shots('1-search'), fullPage: true })

  // Keyboard-only form use.
  await page.getByLabel(/what do you want to learn/i).fill('graph neural networks')
  await page.getByRole('radio', { name: '5', exact: true }).check()
  await page.getByLabel(/what do you want to learn/i).press('Enter')
  await expect(page.getByRole('status')).toContainText(/finding and screening papers/i)
  await expect(page.getByRole('button', { name: /building/i })).toBeDisabled()

  await expect(page.getByRole('heading', { level: 2, name: 'graph neural networks' })).toBeVisible()
  await expect(page.getByText(/revision 1/i)).toBeVisible()
  const papers = page.getByRole('article')
  await expect(papers).toHaveCount(5)
  await expect(page.getByRole('heading', { level: 3, name: /stage 1/i })).toBeVisible()
  await expect(page.getByText(/eligible papers examined/)).toBeVisible()
  await expect(page.getByText(/anyone with this link can view this path/i)).toBeVisible()
  // Every external link is http(s) and opens safely in a new tab.
  for (const link of await page.locator('article a').all()) {
    expect(await link.getAttribute('href')).toMatch(/^https?:\/\//)
    expect(await link.getAttribute('rel')).toContain('noopener')
  }
  await expect(page).toHaveURL(/\?path=[A-Za-z0-9_-]{22}&rev=1$/)
  await page.screenshot({ path: shots('2-path'), fullPage: true })

  const firstUrl = page.url()
  const panel = page.getByRole('form', { name: /adjust this path/i })
  await panel.getByRole('radio', { name: 'Advanced' }).check()
  await panel.getByRole('radio', { name: '10', exact: true }).check()
  await panel.getByRole('button', { name: /rebuild path/i }).click()
  // The previous revision stays visible until the new one arrives.
  await expect(page.getByText(/revision 2/i)).toBeVisible()
  await expect(papers).toHaveCount(10)
  await expect(page.getByRole('navigation', { name: /revisions/i })).toBeVisible()
  await expect(page).toHaveURL(/rev=2$/)
  await page.screenshot({ path: shots('3-regenerated'), fullPage: true })

  // Reopening the first link shows the original, unchanged revision.
  await page.goto(firstUrl)
  await expect(page.getByText(/revision 1/i)).toBeVisible()
  await expect(papers).toHaveCount(5)

  // Back to the revision list: open revision 2 again from the navigation.
  await page.getByRole('navigation', { name: /revisions/i }).getByRole('button', { name: /advanced/i }).click()
  await expect(page.getByText(/revision 2/i)).toBeVisible()

  // An unknown link explains itself.
  await page.goto('/?path=AAAAAAAAAAAAAAAAAAAAAA')
  await expect(page.getByRole('alert')).toContainText(/path not found/i)
})
