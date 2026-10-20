import { apiBaseUrl } from '../config'
import type { PathOptions, PathResponse } from './types'

/** A failed API call, reduced to what is safe and useful to show a person. */
export class ApiError extends Error {
  readonly status: number | null
  readonly title: string

  constructor(status: number | null, title: string, message: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.title = title
  }

  /** Worth offering "try again" (network, busy, provider trouble), as opposed to a bad request. */
  get retryable(): boolean {
    return this.status === null || this.status === 429 || this.status >= 500
  }
}

const GENERIC: Record<number, string> = {
  400: 'The request was not valid.',
  404: 'This path could not be found. The link may be mistyped.',
  409: 'This request conflicts with a stored path.',
  422: 'No eligible papers were found for this topic.',
  429: 'Too many requests. Please wait a moment and try again.',
  502: 'The research data provider could not be reached. Please try again.',
  503: 'The server is busy right now. Please try again in a few seconds.',
}

/** Longest the UI waits for one API call (release decision R5): above the backend's 30 s provider budget. */
export const REQUEST_TIMEOUT_MS = 45_000

const TIMED_OUT = () =>
  new ApiError(null, 'Timed out', 'The server took too long to respond. Please try again; repeating the same search picks up its result.')

async function request(path: string, init: RequestInit, timeoutMs = REQUEST_TIMEOUT_MS): Promise<PathResponse> {
  // One signal for both the caller's cancel and our timeout; the timer also covers reading the body.
  const controller = new AbortController()
  const caller = init.signal
  const forward = () => controller.abort()
  if (caller?.aborted) controller.abort()
  else caller?.addEventListener('abort', forward, { once: true })
  let timedOut = false
  const timer = setTimeout(() => {
    timedOut = true
    controller.abort()
  }, timeoutMs)
  try {
    return await send(path, { ...init, signal: controller.signal })
  } catch (err) {
    if (timedOut && !caller?.aborted) throw TIMED_OUT()
    throw err
  } finally {
    clearTimeout(timer)
    caller?.removeEventListener('abort', forward)
  }
}

async function send(path: string, init: RequestInit): Promise<PathResponse> {
  let res: Response
  try {
    res = await fetch(`${apiBaseUrl}${path}`, {
      ...init,
      headers: { Accept: 'application/json', ...(init.body ? { 'Content-Type': 'application/json' } : {}), ...init.headers },
    })
  } catch (err) {
    if (err instanceof DOMException && err.name === 'AbortError') throw err
    throw new ApiError(null, 'Server unreachable', 'Could not reach the ResearchPath server. Check your connection and try again.')
  }
  if (res.ok) return (await res.json()) as PathResponse
  // Problem details (RFC 9457) carry a safe, human-readable detail; never show anything else from the body.
  let title = `Request failed (${res.status})`
  let detail = GENERIC[res.status] ?? 'Something went wrong. Please try again.'
  try {
    const problem = (await res.json()) as { title?: unknown; detail?: unknown }
    if (typeof problem.title === 'string' && problem.title.length < 120) title = problem.title
    // 4xx details and the provider/busy messages are written for people; other 5xx details are never shown.
    const showDetail = res.status < 500 || res.status === 502 || res.status === 503
    if (showDetail && typeof problem.detail === 'string' && problem.detail.length < 500) detail = problem.detail
  } catch (err) {
    if (init.signal?.aborted) throw err
    // Not JSON (e.g. a proxy error page): keep the generic message.
  }
  throw new ApiError(res.status, title, detail)
}

export function createPath(
  topic: string,
  options: PathOptions,
  idempotencyKey: string,
  signal?: AbortSignal,
  timeoutMs = REQUEST_TIMEOUT_MS,
) {
  return request(
    '/api/paths',
    { method: 'POST', headers: { 'Idempotency-Key': idempotencyKey }, body: JSON.stringify({ topic, ...options }), signal },
    timeoutMs,
  )
}

export function getPath(pathId: string, revision?: number, signal?: AbortSignal) {
  const id = encodeURIComponent(pathId)
  return request(revision ? `/api/paths/${id}/revisions/${revision}` : `/api/paths/${id}`, { method: 'GET', signal })
}

export function regeneratePath(pathId: string, options: PathOptions, parentRevision: number, signal?: AbortSignal) {
  return request(`/api/paths/${encodeURIComponent(pathId)}/revisions`, {
    method: 'POST',
    body: JSON.stringify({ ...options, parentRevision }),
    signal,
  })
}

/** A fresh key per new submission, so a repeated or retried POST cannot create a second path. */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}
