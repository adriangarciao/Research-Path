import { act, render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { deferred, json, problem, rev1, rev2 } from './test/fixtures'

type Call = { url: string; init: RequestInit }

function mockFetch(...responses: Array<Response | Promise<Response>>) {
  const calls: Call[] = []
  const fn = vi.fn(async (url: string, init: RequestInit) => {
    calls.push({ url, init })
    const next = responses.shift()
    if (!next) throw new Error(`unexpected fetch ${url}`)
    return next
  })
  vi.stubGlobal('fetch', fn)
  return calls
}

async function startPath(user: ReturnType<typeof userEvent.setup>) {
  await user.type(screen.getByLabelText(/what do you want to learn/i), 'reinforcement learning in robotics')
  await user.click(screen.getByRole('button', { name: /build reading path/i }))
}

beforeEach(() => window.history.replaceState(null, '', '/'))
afterEach(() => vi.unstubAllGlobals())

describe('App: create, inspect, regenerate', () => {
  it('creates a path once, shows a truthful pending state, then the stored result', async () => {
    const response = deferred<Response>()
    const calls = mockFetch(response.promise)
    const user = userEvent.setup()
    render(<App />)

    await startPath(user)
    expect(screen.getByRole('status')).toHaveTextContent(/finding and screening papers/i)
    const building = screen.getByRole('button', { name: /building/i })
    expect(building).toBeDisabled()
    await user.click(building) // a second click while pending sends nothing
    expect(calls).toHaveLength(1)

    const { url, init } = calls[0]
    expect(url).toBe('/api/paths')
    expect(init.method).toBe('POST')
    expect((init.headers as Record<string, string>)['Idempotency-Key']).toMatch(/^[A-Za-z0-9_-]{8,64}$/)
    expect(JSON.parse(init.body as string)).toEqual({
      topic: 'reinforcement learning in robotics',
      experience: 'INTERMEDIATE',
      focus: 'BALANCED',
      budget: 10,
    })

    await act(async () => response.resolve(json(rev1, 201)))
    expect(await screen.findByRole('heading', { level: 2, name: rev1.topic })).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(rev1.papers.length)
    for (const s of rev1.stages) expect(screen.getByRole('heading', { level: 3, name: new RegExp(s.label) })).toBeInTheDocument()
    // Order is the server's: paper cards appear in position order.
    const titles = screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent)
    rev1.papers.forEach((p, i) => expect(titles[i]).toContain(p.title))
    expect(screen.getByText(new RegExp(`${rev1.snapshot.eligibleCandidates} eligible papers examined`))).toBeInTheDocument()
    expect(screen.getByText(rev1.access)).toBeInTheDocument()
    expect(window.location.search).toBe(`?path=${rev1.pathId}&rev=1`)
  })

  it('keeps the current path visible while regenerating and after a failure, then shows the new revision', async () => {
    const failure = deferred<Response>()
    mockFetch(json(rev1, 201), failure.promise, json(rev2, 201))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    await screen.findByRole('heading', { level: 2, name: rev1.topic })

    const panel = within(screen.getByRole('form', { name: /adjust this path/i }))
    expect(panel.getByRole('button', { name: /rebuild path/i })).toBeDisabled() // nothing changed yet
    await user.click(panel.getByRole('radio', { name: 'Advanced' }))
    await user.click(panel.getByRole('radio', { name: '5' }))
    await user.click(panel.getByRole('button', { name: /rebuild path/i }))

    expect(panel.getByRole('status')).toHaveTextContent(/current path stays here/i)
    expect(screen.getAllByRole('article')).toHaveLength(rev1.papers.length)

    await act(async () => failure.resolve(problem(503, 'Busy', 'Too many paths are being generated right now; try again shortly.')))
    const alert = await panel.findByRole('alert')
    expect(alert).toHaveTextContent(/too many paths/i)
    expect(alert).toHaveTextContent(/path above is unchanged/i)
    expect(screen.getAllByRole('article')).toHaveLength(rev1.papers.length)
    expect(screen.getByText(/revision 1/i)).toBeInTheDocument()

    await user.click(within(alert).getByRole('button', { name: /try again/i }))
    expect(await screen.findByText(/revision 2/i)).toBeInTheDocument()
    expect(screen.getAllByRole('article')).toHaveLength(rev2.papers.length)
    expect(window.location.search).toBe(`?path=${rev2.pathId}&rev=2`)
    expect(screen.getByRole('navigation', { name: /revisions/i })).toBeInTheDocument()
  })

  it('sends the regeneration to the stored path with its parent revision', async () => {
    const calls = mockFetch(json(rev1, 201), json(rev2, 201))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    await screen.findByRole('heading', { level: 2, name: rev1.topic })
    const panel = within(screen.getByRole('form', { name: /adjust this path/i }))
    await user.click(panel.getByRole('radio', { name: 'Recent' }))
    await user.click(panel.getByRole('button', { name: /rebuild path/i }))
    await screen.findByText(/revision 2/i)
    expect(calls[1].url).toBe(`/api/paths/${rev1.pathId}/revisions`)
    expect(JSON.parse(calls[1].init.body as string)).toEqual({
      experience: 'INTERMEDIATE',
      focus: 'RECENT',
      budget: 10,
      parentRevision: 1,
    })
  })

  it('shows a create failure with the server message and keeps the form', async () => {
    mockFetch(problem(422, 'No eligible papers', 'No eligible papers were found for "x". Try a broader or differently worded topic.'))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    expect(await screen.findByRole('alert')).toHaveTextContent(/try a broader/i)
    expect(screen.getByLabelText(/what do you want to learn/i)).toHaveValue('reinforcement learning in robotics')
    expect(screen.getByRole('button', { name: /build reading path/i })).toBeEnabled()
  })

  it('does not show internal details of a server error', async () => {
    mockFetch(problem(500, 'Internal Server Error', 'java.lang.NullPointerException at dev.researchpath.Foo'))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    const alert = await screen.findByRole('alert')
    expect(alert).not.toHaveTextContent(/NullPointer|dev\.researchpath/)
  })

  it('reports an unreachable server', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('Failed to fetch') }))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    expect(await screen.findByRole('alert')).toHaveTextContent(/could not reach the researchpath server/i)
  })

  it('retrying the same search after a retryable failure reuses its key, so the server cannot search twice', async () => {
    const calls = mockFetch(
      problem(503, 'Research data provider too slow', 'The research data provider did not respond in time, so nothing was saved.'),
      problem(422, 'No eligible papers', 'No eligible papers were found.'),
      problem(422, 'No eligible papers', 'No eligible papers were found.'),
    )
    const user = userEvent.setup()
    render(<App />)
    const key = (i: number) => (calls[i].init.headers as Record<string, string>)['Idempotency-Key']
    await startPath(user)
    expect(await screen.findByRole('alert')).toHaveTextContent(/did not respond in time/i)
    await user.click(screen.getByRole('button', { name: /build reading path/i }))
    expect(await screen.findByRole('alert')).toHaveTextContent(/no eligible papers/i)
    expect(key(1)).toBe(key(0))
    // After a final (non-retryable) answer the next submission is a new request with a new key.
    await user.click(screen.getByRole('button', { name: /build reading path/i }))
    await screen.findByRole('alert')
    expect(calls).toHaveLength(3)
    expect(key(2)).not.toBe(key(1))
  })

  it('lets the person stop waiting and returns to the form', async () => {
    mockFetch(new Promise<Response>(() => {}))
    const user = userEvent.setup()
    render(<App />)
    await startPath(user)
    await user.click(screen.getByRole('button', { name: /stop waiting/i }))
    expect(screen.getByRole('button', { name: /build reading path/i })).toBeEnabled()
    expect(screen.queryByRole('status')).toBeNull()
  })
})

describe('App: opening a link', () => {
  it('loads the revision named in the address', async () => {
    window.history.replaceState(null, '', `/?path=${rev1.pathId}&rev=1`)
    const calls = mockFetch(json(rev1))
    render(<App />)
    expect(await screen.findByRole('heading', { level: 2, name: rev1.topic })).toBeInTheDocument()
    expect(calls[0].url).toBe(`/api/paths/${rev1.pathId}/revisions/1`)
  })

  it('explains an unknown path and offers a new topic', async () => {
    window.history.replaceState(null, '', '/?path=AAAAAAAAAAAAAAAAAAAAAA')
    mockFetch(problem(404, 'Path not found', 'No path with this id and revision.'))
    const user = userEvent.setup()
    render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/path not found/i)
    await user.click(screen.getByRole('button', { name: /start a new topic/i }))
    expect(screen.getByLabelText(/what do you want to learn/i)).toBeInTheDocument()
    expect(window.location.search).toBe('')
  })

  it('rejects a malformed id without calling the server', async () => {
    window.history.replaceState(null, '', '/?path=../../etc')
    const fetchSpy = vi.fn()
    vi.stubGlobal('fetch', fetchSpy)
    render(<App />)
    expect(await screen.findByRole('alert')).toHaveTextContent(/does not point to a reading path/i)
    expect(fetchSpy).not.toHaveBeenCalled()
  })
})
