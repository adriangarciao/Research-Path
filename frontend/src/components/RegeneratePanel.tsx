import { useState, type FormEvent } from 'react'
import type { ApiError } from '../api/client'
import type { PathOptions, PathResponse } from '../api/types'
import { OptionFields } from './OptionFields'

interface Props {
  path: PathResponse
  pending: boolean
  error: ApiError | null
  onRegenerate: (options: PathOptions) => void
}

function optionsOf(path: PathResponse): PathOptions {
  return { ...path.options, budget: path.options.budget as PathOptions['budget'] }
}

/** Change settings and rebuild from the same examined papers; the current path stays visible meanwhile. */
export function RegeneratePanel({ path, pending, error, onRegenerate }: Props) {
  const [options, setOptions] = useState<PathOptions>(() => optionsOf(path))
  const current = optionsOf(path)
  const unchanged =
    options.experience === current.experience && options.focus === current.focus && options.budget === current.budget

  function submit(e: FormEvent) {
    e.preventDefault()
    if (!pending && !unchanged) onRegenerate(options)
  }

  return (
    <form className="regenerate" onSubmit={submit} aria-busy={pending} aria-labelledby="regenerate-title">
      <h3 id="regenerate-title">Adjust this path</h3>
      <p className="regenerate__note">
        Uses the same {path.snapshot.eligibleCandidates} papers examined for this topic; nothing is searched again.
      </p>
      <OptionFields idPrefix="regen" value={options} onChange={setOptions} disabled={pending} />
      <div className="regenerate__actions">
        <button className="button button--primary" type="submit" disabled={pending || unchanged}>
          {pending ? 'Rebuilding…' : 'Rebuild path'}
        </button>
        {unchanged && !pending && <span className="regenerate__hint">Change a setting to rebuild.</span>}
        {pending && (
          <span className="regenerate__hint" role="status">
            Rebuilding… the current path stays here until the new one is ready.
          </span>
        )}
      </div>
      {error && (
        <div className="alert alert--error" role="alert">
          <strong>{error.title}.</strong> {error.message} The path above is unchanged.
          {error.retryable && (
            <button className="button button--link" type="button" onClick={() => onRegenerate(options)}>
              Try again
            </button>
          )}
        </div>
      )}
    </form>
  )
}
