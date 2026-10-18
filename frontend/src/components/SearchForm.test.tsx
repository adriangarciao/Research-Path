import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { SearchForm } from './SearchForm'

describe('SearchForm', () => {
  it('defaults to Intermediate, 10 papers, Balanced', () => {
    render(<SearchForm onSubmit={vi.fn()} pending={false} />)
    expect(screen.getByRole('radio', { name: 'Intermediate' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '10' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'Balanced' })).toBeChecked()
  })

  it('blocks a blank topic with a message and focuses the input', async () => {
    const onSubmit = vi.fn()
    render(<SearchForm onSubmit={onSubmit} pending={false} />)
    await userEvent.type(screen.getByLabelText(/what do you want to learn/i), '   ')
    await userEvent.click(screen.getByRole('button', { name: /build reading path/i }))
    expect(onSubmit).not.toHaveBeenCalled()
    expect(screen.getByRole('alert')).toHaveTextContent(/enter a research topic/i)
    expect(screen.getByLabelText(/what do you want to learn/i)).toHaveFocus()
  })

  it('submits the cleaned topic with the chosen settings, keyboard only', async () => {
    const onSubmit = vi.fn()
    render(<SearchForm onSubmit={onSubmit} pending={false} />)
    const user = userEvent.setup()
    await user.type(screen.getByLabelText(/what do you want to learn/i), '  graph   neural networks ')
    await user.click(screen.getByRole('radio', { name: 'Advanced' }))
    await user.click(screen.getByRole('radio', { name: '20' }))
    await user.click(screen.getByRole('radio', { name: 'Recent' }))
    await user.click(screen.getByLabelText(/what do you want to learn/i))
    await user.keyboard('{Enter}')
    expect(onSubmit).toHaveBeenCalledWith('graph neural networks', { experience: 'ADVANCED', budget: 20, focus: 'RECENT' })
  })

  it('limits the topic length like the server', () => {
    render(<SearchForm onSubmit={vi.fn()} pending={false} />)
    expect(screen.getByLabelText(/what do you want to learn/i)).toHaveAttribute('maxLength', '300')
  })

  it('disables input and settings while a request is pending', () => {
    render(<SearchForm onSubmit={vi.fn()} pending={true} />)
    expect(screen.getByRole('button', { name: /building/i })).toBeDisabled()
    expect(screen.getByLabelText(/what do you want to learn/i)).toBeDisabled()
    expect(screen.getByRole('radio', { name: 'Beginner' })).toBeDisabled()
  })
})
