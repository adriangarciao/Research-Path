import { useCallback, useEffect, useRef, useState } from 'react'
import { ApiError, createPath, getPath, newIdempotencyKey, regeneratePath } from './api/client'
import type { PathOptions, PathResponse } from './api/types'
import { PathView } from './components/PathView'
import { SearchForm } from './components/SearchForm'

type Screen =
  | { kind: 'search' }
  | { kind: 'creating'; topic: string }
  | { kind: 'loading' }
  | { kind: 'path' }
  | { kind: 'load-error'; error: ApiError }

const PATH_ID = /^[A-Za-z0-9_-]{22}$/

/** The address bar holds the path id and revision, so a path can be reopened or shared as a link. */
function readLocation(): { pathId: string; revision?: number } | null {
  const params = new URLSearchParams(window.location.search)
  const pathId = params.get('path')
  if (!pathId) return null
  const rev = Number(params.get('rev'))
  return { pathId, revision: Number.isInteger(rev) && rev > 0 ? rev : undefined }
}

function writeLocation(path: PathResponse | null, replace = false) {
  const url = path ? `?path=${encodeURIComponent(path.pathId)}&rev=${path.revision}` : window.location.pathname
  if (replace) window.history.replaceState(null, '', url)
  else window.history.pushState(null, '', url)
}

const INVALID_LINK = new ApiError(404, 'Path not found', 'This link does not point to a reading path.')

function initialScreen(): Screen {
  const loc = readLocation()
  if (!loc) return { kind: 'search' }
  return PATH_ID.test(loc.pathId) ? { kind: 'loading' } : { kind: 'load-error', error: INVALID_LINK }
}

function isAbort(err: unknown) {
  return err instanceof DOMException && err.name === 'AbortError'
}

function toApiError(err: unknown): ApiError {
  return err instanceof ApiError ? err : new ApiError(null, 'Unexpected error', 'Something went wrong. Please try again.')
}

export default function App() {
  const [screen, setScreen] = useState<Screen>(initialScreen)
  const [path, setPath] = useState<PathResponse | null>(null)
  const [createError, setCreateError] = useState<ApiError | null>(null)
  const [lastTopic, setLastTopic] = useState('')
  const [regenerating, setRegenerating] = useState(false)
  const [regenerateError, setRegenerateError] = useState<ApiError | null>(null)
  const [loadingRevision, setLoadingRevision] = useState<number | null>(null)

  // Only the latest request may update the screen; earlier ones are aborted and ignored.
  const active = useRef<AbortController | null>(null)
  const busy = useRef(false)
  // Retrying the exact same search after a retryable failure (e.g. a timeout) reuses its idempotency key, so the
  // server returns the path it may still have finished instead of searching the provider again.
  const retryKey = useRef<{ request: string; key: string } | null>(null)

  const begin = () => {
    active.current?.abort()
    const controller = new AbortController()
    active.current = controller
    return controller
  }

  /** Fetches a path into the screen; only asynchronous state updates, so it is safe to start from an effect. */
  const fetchInto = useCallback((pathId: string, revision?: number) => {
    const controller = begin()
    getPath(pathId, revision, controller.signal)
      .then((p) => {
        if (controller.signal.aborted) return
        setPath(p)
        setScreen({ kind: 'path' })
      })
      .catch((err: unknown) => {
        if (!isAbort(err) && !controller.signal.aborted) setScreen({ kind: 'load-error', error: toApiError(err) })
      })
  }, [])

  const load = useCallback(
    (pathId: string, revision?: number) => {
      if (!PATH_ID.test(pathId)) {
        setScreen({ kind: 'load-error', error: INVALID_LINK })
        return
      }
      setScreen({ kind: 'loading' })
      fetchInto(pathId, revision)
    },
    [fetchInto],
  )

  useEffect(() => {
    const initial = readLocation()
    if (initial && PATH_ID.test(initial.pathId)) fetchInto(initial.pathId, initial.revision)
    const onPop = () => {
      const loc = readLocation()
      setRegenerateError(null)
      if (loc) load(loc.pathId, loc.revision)
      else {
        active.current?.abort()
        setScreen({ kind: 'search' })
      }
    }
    window.addEventListener('popstate', onPop)
    return () => {
      window.removeEventListener('popstate', onPop)
      active.current?.abort()
    }
  }, [fetchInto, load])

  function create(topic: string, options: PathOptions) {
    if (busy.current) return // no duplicate submissions while one is running
    busy.current = true
    const controller = begin()
    setLastTopic(topic)
    setCreateError(null)
    setScreen({ kind: 'creating', topic })
    const request = JSON.stringify({ topic, options })
    const key = retryKey.current?.request === request ? retryKey.current.key : newIdempotencyKey()
    retryKey.current = { request, key }
    createPath(topic, options, key, controller.signal)
      .then((p) => {
        retryKey.current = null
        if (controller.signal.aborted) return
        setPath(p)
        setRegenerateError(null)
        setScreen({ kind: 'path' })
        writeLocation(p)
      })
      .catch((err: unknown) => {
        if (isAbort(err) || controller.signal.aborted) return
        const error = toApiError(err)
        if (!error.retryable) retryKey.current = null
        setCreateError(error)
        setScreen({ kind: 'search' })
      })
      .finally(() => {
        busy.current = false
      })
  }

  function cancelCreate() {
    active.current?.abort()
    busy.current = false
    setScreen({ kind: 'search' })
  }

  function regenerate(options: PathOptions) {
    if (!path || busy.current) return
    busy.current = true
    const controller = begin()
    setRegenerating(true)
    setRegenerateError(null)
    regeneratePath(path.pathId, options, path.revision, controller.signal)
      .then((p) => {
        if (controller.signal.aborted) return
        setPath(p)
        writeLocation(p)
      })
      .catch((err: unknown) => {
        // On failure the previous revision stays on screen.
        if (!isAbort(err) && !controller.signal.aborted) setRegenerateError(toApiError(err))
      })
      .finally(() => {
        busy.current = false
        if (!controller.signal.aborted) setRegenerating(false)
      })
  }

  function openRevision(revision: number) {
    if (!path || busy.current) return
    const controller = begin()
    setLoadingRevision(revision)
    setRegenerateError(null)
    getPath(path.pathId, revision, controller.signal)
      .then((p) => {
        if (controller.signal.aborted) return
        setPath(p)
        writeLocation(p)
      })
      .catch((err: unknown) => {
        if (!isAbort(err) && !controller.signal.aborted) setRegenerateError(toApiError(err))
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoadingRevision(null)
      })
  }

  function newTopic() {
    active.current?.abort()
    busy.current = false
    setPath(null)
    setRegenerating(false)
    setCreateError(null)
    setScreen({ kind: 'search' })
    writeLocation(null)
  }

  return (
    <div className="app">
      <header className="masthead">
        <a
          className="masthead__brand"
          href="/"
          onClick={(e) => {
            e.preventDefault()
            newTopic()
          }}
        >
          Research<span>Path</span>
        </a>
        {screen.kind === 'path' && (
          <button type="button" className="button button--ghost" onClick={newTopic}>
            New topic
          </button>
        )}
      </header>

      <main id="main" className="main">
        {(screen.kind === 'search' || screen.kind === 'creating') && (
          <section className="intro" aria-labelledby="intro-title">
            <h1 id="intro-title" className="intro__title">
              A reading path through <em>real</em> research.
            </h1>
            <p className="intro__lede">
              Name a topic. ResearchPath examines papers from OpenAlex, keeps the relevant ones, picks a small set that
              covers different subtopics, and orders them with a short reason for each. It cannot read the papers for
              you, and the order is a recommendation, not a prerequisite chain.
            </p>
            <SearchForm onSubmit={create} pending={screen.kind === 'creating'} initialTopic={lastTopic} />
            {createError && (
              <div className="alert alert--error" role="alert">
                <strong>{createError.title}.</strong> {createError.message}
              </div>
            )}
            {screen.kind === 'creating' && (
              <div className="pending" role="status" aria-live="polite">
                <span className="pending__dot" aria-hidden="true" />
                <p>
                  Finding and screening papers for <strong>“{screen.topic}”</strong>. This usually takes 5–15 seconds.
                </p>
                <button type="button" className="button button--link" onClick={cancelCreate}>
                  Stop waiting
                </button>
              </div>
            )}
          </section>
        )}

        {screen.kind === 'loading' && (
          <div className="pending" role="status" aria-live="polite">
            <span className="pending__dot" aria-hidden="true" />
            <p>Opening reading path…</p>
          </div>
        )}

        {screen.kind === 'load-error' && (
          <div className="alert alert--error" role="alert">
            <strong>{screen.error.title}.</strong> {screen.error.message}{' '}
            <button type="button" className="button button--link" onClick={newTopic}>
              Start a new topic
            </button>
          </div>
        )}

        {screen.kind === 'path' && path && (
          <PathView
            path={path}
            regenerating={regenerating}
            regenerateError={regenerateError}
            onRegenerate={regenerate}
            onOpenRevision={openRevision}
            loadingRevision={loadingRevision}
          />
        )}
      </main>

      <footer className="footer">
        <p>
          Paper metadata from <a href="https://openalex.org" target="_blank" rel="noopener noreferrer">OpenAlex</a> (CC0).
          Selections are computed from the papers examined, not from everything published on a topic.
        </p>
      </footer>
    </div>
  )
}
