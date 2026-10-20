import { describe, expect, it } from 'vitest'
import { authorLine, isHint, notice, safeHref } from './format'

describe('safeHref', () => {
  it('accepts only absolute http(s) URLs', () => {
    expect(safeHref('https://doi.org/10.1/x')).toBe('https://doi.org/10.1/x')
    expect(safeHref('http://example.org/a b')).toBe('http://example.org/a%20b')
    for (const bad of ['javascript:alert(1)', 'JAVASCRIPT:alert(1)', 'data:text/html,hi', '/relative', 'file:///etc/passwd', '', null]) {
      expect(safeHref(bad)).toBeNull()
    }
  })
})

describe('notice', () => {
  it('splits backend codes into a heading and a sentence', () => {
    expect(notice('FEWER_THAN_REQUESTED: only 12 eligible candidates for a budget of 15')).toEqual({
      heading: 'Fewer papers than requested',
      text: 'Only 12 eligible candidates for a budget of 15.',
    })
    expect(notice('plain text')).toEqual({ heading: 'Note', text: 'plain text' })
  })

  it('treats the small-pool suggestion as a hint, not a warning', () => {
    const hint = 'SMALL_POOL: only 62 of 200 retrieved papers passed the relevance check for this topic'
    expect(isHint(hint)).toBe(true)
    expect(notice(hint).heading).toBe('A broader topic may help')
    expect(isHint('FEWER_THAN_REQUESTED: only 12 eligible candidates for a budget of 15')).toBe(false)
    expect(isHint('SMALL_POOL')).toBe(false)
    expect(isHint('plain text')).toBe(false)
  })
})

describe('authorLine', () => {
  it('names shown authors and counts the rest', () => {
    expect(authorLine([], 0)).toBe('Authors not listed')
    expect(authorLine(['A', 'B'], 2)).toBe('A, B')
    expect(authorLine(['A'], 3)).toBe('A and 2 more')
  })
})
