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
    return this.status === null || this.status >= 500
  }
}

const GENERIC: Record<number, string> = {
  400: 'The request was not valid.',
  404: 'This path could not be found. The link may be mistyped.',
  409: 'This request conflicts with a stored path.',
  422: 'No eligible papers were found for this topic.',
  502: 'The research data provider could not be reached. Please try again.',
  503: 'The server is busy right now. Please try again in a few seconds.',
}

async function request(path: string, init: RequestInit): Promise<PathResponse> {
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
  } catch {
    // Not JSON (e.g. a proxy error page): keep the generic message.
  }
  throw new ApiError(res.status, title, detail)
}

export function createPath(topic: string, options: PathOptions, idempotencyKey: string, signal?: AbortSignal) {
  return request('/api/paths', {
    method: 'POST',
    headers: { 'Idempotency-Key': idempotencyKey },
    body: JSON.stringify({ topic, ...options }),
    signal,
  })
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

/** A fresh key per user submission, so a repeated or retried POST cannot create a second path. */
export function newIdempotencyKey(): string {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID()
  const bytes = crypto.getRandomValues(new Uint8Array(16))
  return Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('')
}
