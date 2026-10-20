import type { ApiError } from '../api/client'
import type { PathOptions, PathResponse } from '../api/types'
import { EXPERIENCE_LABEL, FOCUS_LABEL, formatDate, notice } from '../lib/format'
import { CoveragePanel } from './CoveragePanel'
import { PaperCard } from './PaperCard'
import { RegeneratePanel } from './RegeneratePanel'

interface Props {
  path: PathResponse
  regenerating: boolean
  regenerateError: ApiError | null
  onRegenerate: (options: PathOptions) => void
  onOpenRevision: (revision: number) => void
  loadingRevision: number | null
}

export function PathView({ path, regenerating, regenerateError, onRegenerate, onOpenRevision, loadingRevision }: Props) {
  const settings = `${EXPERIENCE_LABEL[path.options.experience]} · ${FOCUS_LABEL[path.options.focus]} · ${path.options.budget} papers`
  return (
    <div className="path" aria-busy={regenerating}>
      <header className="path__header">
        <p className="eyebrow">Reading path · revision {path.revision}</p>
        <h2 className="path__topic">{path.topic}</h2>
        <p className="path__settings">{settings}</p>
        <p className="path__counts">
          <strong>{path.selectedCount}</strong> {path.selectedCount === 1 ? 'paper' : 'papers'} selected from{' '}
          {path.snapshot.eligibleCandidates} eligible papers examined (OpenAlex, retrieved {formatDate(path.snapshot.retrievedAt)}).
        </p>
      </header>

      {path.warnings.length > 0 && (
        <section className="notices notices--warn" aria-label="Warnings">
          {path.warnings.map((w) => {
            const n = notice(w)
            return (
              <p key={w}>
                <strong>{n.heading}.</strong> {n.text}
              </p>
            )
          })}
        </section>
      )}

      <div className="path__layout">
        <ol className="stages" aria-label="Stages">
          {path.stages.map((s) => (
            <li key={s.number} className={`stage stage--${s.kind}`} aria-labelledby={`stage-${s.number}`}>
              <div className="stage__head">
                <span className="stage__index" aria-hidden="true">
                  Stage {s.number}
                </span>
                <h3 id={`stage-${s.number}`}>
                  <span className="visually-hidden">Stage {s.number}: </span>
                  {s.label}
                </h3>
                <p className="stage__purpose">{s.purpose}</p>
              </div>
              <div className="stage__papers">
                {path.papers
                  .filter((p) => p.position >= s.firstPosition && p.position <= s.lastPosition)
                  .map((p) => (
                    <PaperCard key={p.openalexId} paper={p} />
                  ))}
              </div>
            </li>
          ))}
        </ol>

        <aside className="path__aside">
          <RegeneratePanel
            key={`${path.pathId}-${path.revision}`}
            path={path}
            pending={regenerating}
            error={regenerateError}
            onRegenerate={onRegenerate}
          />
          {path.revisions.length > 1 && (
            <nav className="revisions" aria-labelledby="revisions-title">
              <h3 id="revisions-title">Revisions</h3>
              <ol>
                {path.revisions.map((r) => (
                  <li key={r.revision}>
                    <button
                      type="button"
                      className="revisions__item"
                      aria-current={r.revision === path.revision ? 'page' : undefined}
                      disabled={r.revision === path.revision || loadingRevision !== null || regenerating}
                      onClick={() => onOpenRevision(r.revision)}
                    >
                      <span className="revisions__n">{r.revision}</span>
                      <span>
                        {EXPERIENCE_LABEL[r.experience]} · {FOCUS_LABEL[r.focus]} · {r.budget}
                        {loadingRevision === r.revision && ' — opening…'}
                      </span>
                    </button>
                  </li>
                ))}
              </ol>
            </nav>
          )}
          <CoveragePanel coverage={path.coverage} paperCount={path.selectedCount} />
          <section className="notices" aria-label="About this path">
            {path.disclosures.map((d) => (
              <p key={d}>{notice(d).text}</p>
            ))}
            <p>{path.access}</p>
            {path.retention && <p>{path.retention.notice}</p>}
          </section>
        </aside>
      </div>
    </div>
  )
}
