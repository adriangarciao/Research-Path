import { describe, expect, it, vi } from 'vitest'
import { ApiError, getPath } from './client'
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