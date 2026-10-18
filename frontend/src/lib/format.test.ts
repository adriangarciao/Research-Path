import { describe, expect, it } from 'vitest'
import { authorLine, notice, safeHref } from './format'

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
})

describe('authorLine', () => {
  it('names shown authors and counts the rest', () => {
    expect(authorLine([], 0)).toBe('Authors not listed')
    expect(authorLine(['A', 'B'], 2)).toBe('A, B')
    expect(authorLine(['A'], 3)).toBe('A and 2 more')
  })
})
