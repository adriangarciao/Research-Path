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
})