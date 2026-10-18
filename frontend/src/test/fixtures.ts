import type { PathResponse } from '../api/types'
import rev1Json from './fixtures/path-rev1.json'
import rev2Json from './fixtures/path-rev2.json'

// Real responses captured from the backend (POST /api/paths, then a regeneration), so the
// tests exercise the actual API contract rather than a hand-written shape.
export const rev1 = rev1Json as unknown as PathResponse
export const rev2 = rev2Json as unknown as PathResponse

export function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
}

export function problem(status: number, title: string, detail: string): Response {
  return new Response(JSON.stringify({ type: 'about:blank', title, status, detail }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/** A promise the test resolves later, to observe pending states. */
export function deferred<T>() {
  let resolve!: (v: T) => void
  let reject!: (e: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
