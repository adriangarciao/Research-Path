import type { Paper } from '../api/types'
import { FLAG_LABEL, LINK_LABEL, authorLine, openAlexRecord, safeHref, workTypeLabel } from '../lib/format'

function refs(positions: number[]): string {
  return positions.map((p) => `#${p}`).join(', ')
}

/** One paper. All provider text (title, authors, tags) is rendered as plain text, never as HTML. */
export function PaperCard({ paper }: { paper: Paper }) {
  const href = safeHref(paper.link)
  const record = openAlexRecord(paper.openalexId)
  const meta = [paper.year ?? 'Year unknown', workTypeLabel(paper.workType)].filter(Boolean).join(' · ')
  const citations =
    paper.citedByCount === null ? null : `${paper.citedByCount.toLocaleString()} citation${paper.citedByCount === 1 ? '' : 's'}`
  return (
    <article className="paper" id={`paper-${paper.position}`} aria-labelledby={`paper-${paper.position}-title`}>
      <div className="paper__number" aria-hidden="true">
        {String(paper.position).padStart(2, '0')}
      </div>
      <div className="paper__body">
        <h4 className="paper__title" id={`paper-${paper.position}-title`}>
          <span className="visually-hidden">Paper {paper.position}: </span>
          {href ? (
            <a href={href} target="_blank" rel="noopener noreferrer external">
              {paper.title}
              <span className="visually-hidden"> (opens in a new tab)</span>
            </a>
          ) : (
            paper.title
          )}
        </h4>
        <p className="paper__authors">{authorLine(paper.authors, paper.authorCount)}</p>
        <p className="paper__meta">
          <span>{meta}</span>
          {citations && <span>{citations}</span>}
          {href && paper.linkKind && <span className={`link-kind link-kind--${paper.linkKind}`}>{LINK_LABEL[paper.linkKind]}</span>}
          {record && (
            <a className="record-link" href={record} target="_blank" rel="noopener noreferrer external">
              OpenAlex record<span className="visually-hidden"> for paper {paper.position} (opens in a new tab)</span>
            </a>
          )}
        </p>

        <div className="paper__why">
          <h5>Why it’s here</h5>
          <ul>
            {paper.reasons.map((r) => (
              <li key={r.code}>{r.text}</li>
            ))}
          </ul>
        </div>

        {(paper.cites.length > 0 || paper.citedBy.length > 0) && (
          <p className="paper__links">
            {paper.cites.length > 0 && (
              <span>
                Cites {refs(paper.cites)} in this path
              </span>
            )}
            {paper.citedBy.length > 0 && <span>Cited by {refs(paper.citedBy)}</span>}
          </p>
        )}

        {paper.tags.length > 0 && (
          <ul className="tags" aria-label="Subject tags">
            {paper.tags.map((t) => (
              <li key={t}>{t}</li>
            ))}
          </ul>
        )}

        {paper.flags.length > 0 && (
          <ul className="flags" aria-label="Metadata caveats">
            {paper.flags.map((f) => (
              <li key={f}>{FLAG_LABEL[f] ?? f}</li>
            ))}
          </ul>
        )}
      </div>
    </article>
  )
}
