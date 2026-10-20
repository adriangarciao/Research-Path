import { expect, test, type Page } from '@playwright/test'

// M15 representative journeys against the REAL backend and provider (desktop only, to keep provider use small).
// Two failure cases are SIMULATED at the browser boundary (labelled below); everything else is live.

test.beforeEach(async ({ page }, testInfo) => {
  void page
  test.skip(testInfo.project.name !== 'desktop', 'journeys run once, on desktop')
})

async function startPath(page: Page, topic: string, experience: string, budget: string, focus: string) {
  await page.goto('/')
  await page.getByLabel(/what do you want to learn/i).fill(topic)
  await page.getByRole('radio', { name: experience, exact: true }).check()
  await page.getByRole('radio', { name: budget, exact: true }).check()
  await page.getByRole('radio', { name: focus, exact: true }).check()
  await page.getByRole('button', { name: /build reading path/i }).click()
}

async function expectPath(page: Page, topic: string, settings: RegExp, papers: number) {
  await expect(page.getByRole('heading', { level: 2, name: topic })).toBeVisible()
  await expect(page.getByText(settings)).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(papers)
  // Every paper explains itself; landmark slots name their citation count.
  await expect(page.getByText(/why it’s here/i)).toHaveCount(papers)
  await expect(page.getByText(/most-cited works among the papers examined/i).first()).toBeVisible()
}

for (const [topic, experience, budget, focus] of [
  ['graph neural networks', 'Beginner', '5', 'Balanced'],
  ['urban heat island mitigation', 'Advanced', '10', 'Recent'],
] as const) {
  test(`journey: ${experience}/${budget}/${focus} on "${topic}", then Intermediate/10/Balanced`, async ({ page }, info) => {
    await startPath(page, topic, experience, budget, focus)
    await expectPath(page, topic, new RegExp(`${experience} · ${focus} · ${budget} papers`), Number(budget))
    await page.screenshot({ path: info.outputPath('created.png'), fullPage: true })

    const panel = page.getByRole('form', { name: /adjust this path/i })
    await panel.getByRole('radio', { name: 'Intermediate' }).check()
    await panel.getByRole('radio', { name: '10', exact: true }).check()
    await panel.getByRole('radio', { name: 'Balanced' }).check()
    await panel.getByRole('button', { name: /rebuild path/i }).click()
    await expect(page.getByText(/revision 2/i)).toBeVisible()
    await expectPath(page, topic, /Intermediate · Balanced · 10 papers/, 10)
  })
}

test('empty result: a topic with no eligible papers is explained and the form is kept (live)', async ({ page }) => {
  await startPath(page, 'qwzxv blorptangle frumious', 'Intermediate', '10', 'Balanced')
  await expect(page.getByRole('alert')).toContainText(/no eligible papers/i)
  await expect(page.getByLabel(/what do you want to learn/i)).toHaveValue('qwzxv blorptangle frumious')
  await expect(page.getByRole('button', { name: /build reading path/i })).toBeEnabled()
})

test('provider error (SIMULATED 502 at the browser): message shown, no fake result', async ({ page }) => {
  await page.route('**/api/paths', (route) =>
    route.fulfill({
      status: 502,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Research data provider unavailable', status: 502, detail: 'Research data provider request failed; try again later.' }),
    }),
  )
  await startPath(page, 'graph neural networks', 'Intermediate', '10', 'Balanced')
  await expect(page.getByRole('alert')).toContainText(/provider request failed/i)
  await expect(page.getByRole('article')).toHaveCount(0)
})

test('failed regeneration (SIMULATED 503 at the browser) keeps the path; the live retry succeeds', async ({ page }) => {
  await startPath(page, 'graph neural networks', 'Intermediate', '5', 'Balanced')
  await expect(page.getByRole('article')).toHaveCount(5)
  await page.route('**/api/paths/*/revisions', (route) =>
    route.fulfill({
      status: 503,
      contentType: 'application/problem+json',
      body: JSON.stringify({ title: 'Busy', status: 503, detail: 'Too many paths are being generated right now; try again shortly.' }),
    }),
  )
  const panel = page.getByRole('form', { name: /adjust this path/i })
  await panel.getByRole('radio', { name: '15', exact: true }).check()
  await panel.getByRole('button', { name: /rebuild path/i }).click()
  await expect(panel.getByRole('alert')).toContainText(/path above is unchanged/i)
  await expect(page.getByRole('article')).toHaveCount(5)
  await expect(page.getByText(/revision 1/i)).toBeVisible()

  await page.unroute('**/api/paths/*/revisions')
  await panel.getByRole('button', { name: /try again/i }).click()
  await expect(page.getByText(/revision 2/i)).toBeVisible()
  await expect(page.getByRole('article')).toHaveCount(15)
})
