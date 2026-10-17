import { useEffect, useState } from 'react'
import { apiBaseUrl } from './config'

type BackendStatus = 'CHECKING' | 'UP' | 'DOWN' | 'UNKNOWN' | 'UNREACHABLE'

function App() {
  const [status, setStatus] = useState<BackendStatus>('CHECKING')

  useEffect(() => {
    const controller = new AbortController()
    fetch(`${apiBaseUrl}/actuator/health`, { signal: controller.signal })
      .then(async (res) => {
        const body = (await res.json().catch(() => null)) as { status?: string } | null
        const reported = body?.status
        if (reported === 'UP' || reported === 'DOWN') setStatus(reported)
        // No health body + error status: e.g. the dev proxy could not reach the backend.
        else setStatus(res.ok ? 'UNKNOWN' : 'UNREACHABLE')
      })
      .catch((err: unknown) => {
        if (!(err instanceof DOMException && err.name === 'AbortError')) setStatus('UNREACHABLE')
      })
    return () => controller.abort()
  }, [])

  return (
    <main>
      <h1>ResearchPath — Development Setup</h1>
      <p className="muted">
        Environment scaffold only. No research features are implemented yet.
      </p>

      <section className="card" aria-live="polite">
        <strong>Backend health:</strong>{' '}
        <span className={`status-${status}`}>{status}</span>
        <p className="muted">
          Reported by <code>{apiBaseUrl || '(same origin)'}/actuator/health</code>. The frontend runs
          whether or not the backend is up.
        </p>
      </section>
    </main>
  )
}

export default App
