import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError, REQUEST_TIMEOUT_MS, createPath, getPath } from './client'
import { problem } from '../test/fixtures'

describe('client errors', () => {
  it('shows the rate-limit message and treats 429 as retryable', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => problem(429, 'Too many requests', 'Too many rebuilds from this connection. Please wait a moment.')))
    const err = await getPath('AAAAAAAAAAAAAAAAAAAAAA').catch((e: unknown) => e)
    expect(err).toBeInstanceOf(ApiError)
    expect((err as ApiError).message).toMatch(/too many rebuilds/i)
    expect((err as ApiError).retryable).toBe(true)
  })

  it('treats validation errors as not retryable and hides 500 details', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => problem(400, 'Bad Request', 'budget must be one of 5, 10, 15 or 20')))
    const bad = (await getPath('AAAAAAAAAAAAAAAAAAAAAA').catch((e: unknown) => e)) as ApiError
    expect(bad.retryable).toBe(false)
    vi.stubGlobal('fetch', vi.fn(async () => problem(500, 'Unexpected error', 'internal detail')))
    const boom = (await getPath('AAAAAAAAAAAAAAAAAAAAAA').catch((e: unknown) => e)) as ApiError
    expect(boom.message).not.toContain('internal detail')
    expect(boom.retryable).toBe(true)
  })
})

/** A fetch that never answers but, like the real one, rejects when its signal aborts. */
function hangingFetch() {
  const seen: AbortSignal[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn((_url: string, init: RequestInit) => {
      const signal = init.signal as AbortSignal
      seen.push(signal)
      return new Promise<Response>((_resolve, reject) => {
        signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')))
      })
    }),
  )
  return seen
}

const OPTIONS = { experience: 'INTERMEDIATE', focus: 'BALANCED', budget: 10 } as const

describe('client timeout', () => {
  afterEach(() => {
    vi.useRealTimers()
    vi.unstubAllGlobals()
  })

  it('waits 45 s by default, above the backend provider budget of 30 s', () => {
    expect(REQUEST_TIMEOUT_MS).toBe(45_000)
  })

  it('gives up after the timeout with a retryable message and aborts the request', async () => {
    vi.useFakeTimers()
    const seen = hangingFetch()
    const pending = createPath('graph neural networks', OPTIONS, 'key-12345678', undefined, 1000).catch((e: unknown) => e)
    await vi.advanceTimersByTimeAsync(999)
    expect(seen[0].aborted).toBe(false)
    await vi.advanceTimersByTimeAsync(1)
    const err = (await pending) as ApiError
    expect(err).toBeInstanceOf(ApiError)
    expect(err.title).toBe('Timed out')
    expect(err.retryable).toBe(true)
    expect(seen[0].aborted).toBe(true)
  })

  it('a cancel by the person stays a cancel, not a timeout', async () => {
    vi.useFakeTimers()
    hangingFetch()
    const controller = new AbortController()
    const pending = createPath('graph neural networks', OPTIONS, 'key-12345678', controller.signal, 1000).catch((e: unknown) => e)
    controller.abort()
    const err = await pending
    expect(err).toBeInstanceOf(DOMException)
    expect((err as DOMException).name).toBe('AbortError')
  })

  it('shows the server message for a provider time-out (503)', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => problem(503, 'Research data provider too slow',
      'The research data provider did not respond in time, so nothing was saved. Please try again in a moment.')))
    const err = (await createPath('x y', OPTIONS, 'key-12345678').catch((e: unknown) => e)) as ApiError
    expect(err.title).toBe('Research data provider too slow')
    expect(err.message).toMatch(/nothing was saved/)
    expect(err.retryable).toBe(true)
  })
})
