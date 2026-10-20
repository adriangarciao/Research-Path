import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Paper } from '../api/types'
import { rev1 } from '../test/fixtures'
import { PaperCard } from './PaperCard'

const base: Paper = rev1.papers[0]

describe('PaperCard', () => {
  it('renders a real paper from the API with its reasons, tags and links in the path', () => {
    render(<PaperCard paper={base} />)
    const link = screen.getByRole('link', { name: new RegExp(base.title.slice(0, 20), 'i') })
    expect(link).toHaveAttribute('href', base.link)
    expect(link).toHaveAttribute('target', '_blank')
    expect(link.getAttribute('rel')).toContain('noopener')
    expect(link.getAttribute('rel')).toContain('noreferrer')
    for (const r of base.reasons) expect(screen.getByText(r.text)).toBeInTheDocument()
    for (const t of base.tags) expect(screen.getByText(t)).toBeInTheDocument()
  })

  it('never turns unsafe URLs into links and renders provider text as text', () => {
    const hostile: Paper = {
      ...base,
      title: '<img src=x onerror="alert(1)"> Graphs',
      authors: ['<b>Eve</b>'],
      authorCount: 1,
      link: 'javascript:alert(1)',
      linkKind: 'LANDING_PAGE',
    }
    const { container } = render(<PaperCard paper={hostile} />)
    // Only the constructed OpenAlex record link remains; the unsafe provider link is dropped.
    expect(screen.getAllByRole('link')).toHaveLength(1)
    expect(screen.getByRole('link')).toHaveAttribute('href', `https://openalex.org/${base.openalexId}`)
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
    expect(screen.getByText(/<img src=x/)).toBeInTheDocument()
    expect(screen.getByText('<b>Eve</b>')).toBeInTheDocument()
  })

  it('states missing metadata and caveats plainly', () => {
    const sparse: Paper = {
      ...base,
      authors: [],
      authorCount: 0,
      year: null,
      citedByCount: null,
      link: null,
      linkKind: null,
      tags: [],
      cites: [],
      citedBy: [],
      flags: ['YEAR_UNKNOWN', 'CITATIONS_UNKNOWN', 'NO_SAFE_LINK', 'ABSTRACT_MISSING'],
    }
    render(<PaperCard paper={sparse} />)
    expect(screen.getByText('Authors not listed')).toBeInTheDocument()
    expect(screen.getByText(/Year unknown/)).toBeInTheDocument()
    expect(screen.getByText('No verified link')).toBeInTheDocument()
    expect(screen.getByText('No abstract in provider metadata')).toBeInTheDocument()
    expect(screen.queryByText(/citations?$/)).toBeNull()
  })

  it('shortens long author lists honestly and handles very long titles', () => {
    const long: Paper = { ...base, authors: ['A', 'B', 'C', 'D', 'E'], authorCount: 42, title: 'x'.repeat(400) }
    render(<PaperCard paper={long} />)
    expect(screen.getByText('A, B, C, D, E and 37 more')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 4 })).toHaveTextContent('x'.repeat(400))
  })

  it('always offers the OpenAlex record, and never for a malformed id', () => {
    const { unmount } = render(<PaperCard paper={{ ...base, link: null, linkKind: null }} />)
    expect(screen.getByRole('link', { name: /openalex record/i })).toHaveAttribute('href', `https://openalex.org/${base.openalexId}`)
    unmount()
    render(<PaperCard paper={{ ...base, link: null, linkKind: null, openalexId: 'javascript:alert(1)' }} />)
    expect(screen.queryByRole('link')).toBeNull()
  })
})
