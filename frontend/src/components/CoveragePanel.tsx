import type { Coverage } from '../api/types'

const SHOWN = 12

/** Which subtopics of the examined pool the path touches. A labelled breakdown, not a score of "completeness". */
export function CoveragePanel({ coverage, paperCount }: { coverage: Coverage; paperCount: number }) {
  const shown = coverage.concepts.slice(0, SHOWN)
  const hidden = coverage.concepts.length - shown.length + coverage.conceptsNotShown
  return (
    <section className="coverage" aria-labelledby="coverage-title">
      <h3 id="coverage-title">Subtopics covered</h3>
      <p className="coverage__summary">
        <strong>{coverage.conceptsCovered}</strong> of {coverage.conceptsInPool} subtopics found in the examined papers
        appear in this path ({Math.round(coverage.weightedCoverage * 100)}% when weighted by how common each one is).
      </p>
      <p className="coverage__denominator">{coverage.denominator}</p>
      <ul className="coverage__list">
        {shown.map((c) => (
          <li key={c.label}>
            <span className="coverage__label">{c.label}</span>
            <span className="coverage__bar" aria-hidden="true">
              <span style={{ width: `${Math.min(100, (c.coveredBy.length / Math.max(1, paperCount)) * 100)}%` }} />
            </span>
            <span className="coverage__count">
              {c.coveredBy.length} {c.coveredBy.length === 1 ? 'paper' : 'papers'}
              <span className="visually-hidden">: {c.coveredBy.map((p) => `#${p}`).join(', ')}</span>
            </span>
          </li>
        ))}
      </ul>
      {hidden > 0 && <p className="coverage__more">and {hidden} more subtopics with smaller weight</p>}
    </section>
  )
}
