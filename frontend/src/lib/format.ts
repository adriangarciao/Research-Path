import type { Experience, Flag, Focus } from '../api/types'

/** Only absolute http(s) URLs become links; anything else (javascript:, data:, relative) is dropped. */
export function safeHref(url: string | null | undefined): string | null {
  if (!url) return null
  try {
    const parsed = new URL(url)
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : null
  } catch {
    return null
  }
}

export const EXPERIENCE_LABEL: Record<Experience, string> = {
  BEGINNER: 'Beginner',
  INTERMEDIATE: 'Intermediate',
  ADVANCED: 'Advanced',
}

export const EXPERIENCE_HINT: Record<Experience, string> = {
  BEGINNER: 'Slightly favours works marked as surveys or overviews',
  INTERMEDIATE: 'No adjustment',
  ADVANCED: 'Slightly favours works not marked as overviews',
}

export const FOCUS_LABEL: Record<Focus, string> = {
  BALANCED: 'Balanced',
  FOUNDATIONAL: 'Foundational',
  RECENT: 'Recent',
}

export const FOCUS_HINT: Record<Focus, string> = {
  BALANCED: 'Earlier and newer work weighted alike',
  FOUNDATIONAL: 'Favours older, influential work',
  RECENT: 'Favours newer work',
}

export const FLAG_LABEL: Record<Flag, string> = {
  CITATIONS_UNKNOWN: 'Citation count unknown',
  FEW_CITATIONS: 'Fewer than 5 citations so far',
  INFLUENCE_UNKNOWN: 'Citation percentile unknown',
  YEAR_UNKNOWN: 'Publication year unknown',
  NO_SAFE_LINK: 'No verified link',
  ABSTRACT_MISSING: 'No abstract in provider metadata',
  NO_SUBJECT_TAGS: 'No subject tags',
}

export const LINK_LABEL: Record<string, string> = {
  OPEN_ACCESS: 'Open-access copy',
  DOI: 'Publisher (DOI)',
  LANDING_PAGE: 'Source page',
}

const WARNING_LABEL: Record<string, string> = {
  FEWER_THAN_REQUESTED: 'Fewer papers than requested',
  NO_CONCEPT_EVIDENCE: 'No subject evidence',
  NO_CITATION_LINKS: 'No citation links',
  CITATION_CYCLE: 'Citation cycle',
  EMPTY: 'Empty path',
}

/** Backend notices are "CODE: sentence"; show a heading and the sentence. */
export function notice(raw: string): { heading: string; text: string } {
  const i = raw.indexOf(': ')
  if (i < 0) return { heading: 'Note', text: raw }
  const code = raw.slice(0, i)
  const text = raw.slice(i + 2)
  const sentence = text.charAt(0).toUpperCase() + text.slice(1)
  return { heading: WARNING_LABEL[code] ?? 'Note', text: /[.!?]$/.test(sentence) ? sentence : `${sentence}.` }
}

export function authorLine(authors: string[], total: number): string {
  if (authors.length === 0) return 'Authors not listed'
  const shown = authors.join(', ')
  return total > authors.length ? `${shown} and ${total - authors.length} more` : shown
}

export function workTypeLabel(type: string | null): string | null {
  if (!type) return null
  return type.replace(/-/g, ' ')
}

export function formatDate(iso: string): string {
  const d = new Date(iso)
  return Number.isNaN(d.getTime()) ? iso : d.toLocaleDateString(undefined, { year: 'numeric', month: 'short', day: 'numeric' })
}
