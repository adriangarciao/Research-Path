import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { rev1 } from '../test/fixtures'
import { PathView } from './PathView'

describe('PathView', () => {
  it('states the retention policy when the server sends one', () => {
    const path = { ...rev1, retention: { deleteAfterDaysUnviewed: 90, notice: 'Paths that nobody opens for 90 days are deleted.' } }
    render(<PathView path={path} regenerating={false} regenerateError={null} onRegenerate={vi.fn()} onOpenRevision={vi.fn()} loadingRevision={null} />)
    expect(screen.getByText('Paths that nobody opens for 90 days are deleted.')).toBeInTheDocument()
  })

  it('renders older responses without a retention block', () => {
    render(<PathView path={rev1} regenerating={false} regenerateError={null} onRegenerate={vi.fn()} onOpenRevision={vi.fn()} loadingRevision={null} />)
    expect(screen.getByText(rev1.access)).toBeInTheDocument()
    expect(screen.queryByText(/nobody opens/)).toBeNull()
  })

  it('shows a small-pool suggestion apart from the warnings', () => {
    const hint = 'SMALL_POOL: only 62 of 200 retrieved papers passed the relevance check for this topic, so this path was chosen from a smaller pool than usual. A broader or differently worded topic may give a fuller path'
    const path = { ...rev1, warnings: [hint, 'FEWER_THAN_REQUESTED: only 12 eligible candidates for a budget of 15'] }
    render(<PathView path={path} regenerating={false} regenerateError={null} onRegenerate={vi.fn()} onOpenRevision={vi.fn()} loadingRevision={null} />)
    const suggestions = screen.getByRole('region', { name: 'Suggestions' })
    expect(suggestions).toHaveTextContent(/a broader topic may help\..*only 62 of 200/i)
    const warnings = screen.getByRole('region', { name: 'Warnings' })
    expect(warnings).toHaveTextContent(/fewer papers than requested/i)
    expect(warnings).not.toHaveTextContent(/62 of 200/)
  })

  it('shows no suggestion section without hints', () => {
    render(<PathView path={{ ...rev1, warnings: [] }} regenerating={false} regenerateError={null} onRegenerate={vi.fn()} onOpenRevision={vi.fn()} loadingRevision={null} />)
    expect(screen.queryByRole('region', { name: 'Suggestions' })).toBeNull()
  })
})
