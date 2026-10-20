// Mirrors the backend PathDocument v1 (dev.researchpath.path.PathDocument) plus the revision list.
// The UI renders these values; it never recomputes selection, order or coverage.

export type Experience = 'BEGINNER' | 'INTERMEDIATE' | 'ADVANCED'
export type Focus = 'BALANCED' | 'FOUNDATIONAL' | 'RECENT'
export type Budget = 5 | 10 | 15 | 20

export const EXPERIENCES: readonly Experience[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED']
export const FOCUSES: readonly Focus[] = ['BALANCED', 'FOUNDATIONAL', 'RECENT']
export const BUDGETS: readonly Budget[] = [5, 10, 15, 20]
export const MAX_TOPIC_LENGTH = 300

export interface PathOptions {
  experience: Experience
  focus: Focus
  budget: Budget
}

export const DEFAULT_OPTIONS: PathOptions = { experience: 'INTERMEDIATE', focus: 'BALANCED', budget: 10 }

export type StageKind = 'OVERVIEW' | 'PERIOD' | 'REMAINING'

export interface Stage {
  number: number
  kind: StageKind
  label: string
  purpose: string
  firstPosition: number
  lastPosition: number
}

export interface Explanation {
  code: string
  text: string
}

export type Flag =
  | 'CITATIONS_UNKNOWN'
  | 'FEW_CITATIONS'
  | 'INFLUENCE_UNKNOWN'
  | 'YEAR_UNKNOWN'
  | 'NO_SAFE_LINK'
  | 'ABSTRACT_MISSING'
  | 'NO_SUBJECT_TAGS'

export interface Paper {
  position: number
  stage: number
  openalexId: string
  title: string
  authors: string[]
  authorCount: number
  year: number | null
  workType: string | null
  citedByCount: number | null
  link: string | null
  linkKind: 'OPEN_ACCESS' | 'DOI' | 'LANDING_PAGE' | null
  tags: string[]
  reasons: Explanation[]
  cites: number[]
  citedBy: number[]
  flags: Flag[]
  selectionStep: number
}

export interface CoveredConcept {
  label: string
  kind: 'TOPIC' | 'KEYWORD'
  weight: number
  coveredBy: number[]
}

export interface Coverage {
  weightedCoverage: number
  conceptsCovered: number
  conceptsInPool: number
  denominator: string
  concepts: CoveredConcept[]
  conceptsNotShown: number
}

export interface RevisionSummary {
  revision: number
  parentRevision: number | null
  experience: Experience
  focus: Focus
  budget: number
  createdAt: string
}

export interface PathResponse {
  documentVersion: number
  pathId: string
  revision: number
  parentRevision: number | null
  topic: string
  createdAt: string
  options: { experience: Experience; focus: Focus; budget: number }
  snapshot: { retrievedAt: string; candidatesRetrieved: number; eligibleCandidates: number }
  versions: Record<string, number>
  requestedCount: number
  selectedCount: number
  coverage: Coverage
  stages: Stage[]
  papers: Paper[]
  warnings: string[]
  disclosures: string[]
  access: string
  revisions: RevisionSummary[]
  /** Retention policy at view time (not part of the stored revision); absent in older responses. */
  retention?: { deleteAfterDaysUnviewed: number; notice: string }
}
