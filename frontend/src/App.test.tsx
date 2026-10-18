import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import App from './App'

describe('App', () => {
  it('reports backend health from /actuator/health', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(JSON.stringify({ status: 'UP' }), { status: 200 })))
    render(<App />)
    expect(await screen.findByText('UP')).toBeInTheDocument()
  })

  it('shows UNREACHABLE when the request fails', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => { throw new TypeError('network') }))
    render(<App />)
    expect(await screen.findByText('UNREACHABLE')).toBeInTheDocument()
  })
})
