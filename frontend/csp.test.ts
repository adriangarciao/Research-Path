// @vitest-environment node
import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'
import { apiOrigin, connectSrc, cspMetaPlugin } from './csp.ts'

type HeaderRule = { source: string; headers: { key: string; value: string }[] }

describe('API origin for the CSP', () => {
  it('accepts bare https origins and local http only', () => {
    expect(apiOrigin(undefined)).toBeNull()
    expect(apiOrigin('')).toBeNull()
    expect(apiOrigin('https://researchpath-api.up.railway.app')).toBe('https://researchpath-api.up.railway.app')
    expect(apiOrigin('https://api.example.com/')).toBe('https://api.example.com')
    expect(apiOrigin('http://127.0.0.1:8080')).toBe('http://127.0.0.1:8080')
    for (const bad of ['http://api.example.com', 'api.example.com', 'https://api.example.com/v1', 'https://u:p@api.example.com',
      'https://api.example.com?x=1', 'javascript:alert(1)', 'ftp://api.example.com']) {
      expect(() => apiOrigin(bad), bad).toThrow()
    }
  })

  it('narrows connect-src to the exact API origin', () => {
    expect(connectSrc('https://api.example.com')).toBe("connect-src 'self' https://api.example.com")
    expect(connectSrc(undefined)).toBe("connect-src 'self'")
    const plugin = cspMetaPlugin('https://api.example.com')
    const transform = plugin.transformIndexHtml as (html: string) => string
    expect(transform('<html><head><title>x</title></head></html>')).toContain(
      `<meta http-equiv="Content-Security-Policy" content="connect-src 'self' https://api.example.com" />`,
    )
  })
})

describe('vercel.json hosting headers', () => {
  const config = JSON.parse(readFileSync(new URL('./vercel.json', import.meta.url), 'utf8')) as {
    headers: HeaderRule[]
    rewrites?: unknown
    framework: string
    outputDirectory: string
  }
  const all = Object.fromEntries(config.headers.find((r) => r.source === '/(.*)')!.headers.map((h) => [h.key, h.value]))

  it('ships a strict CSP and the standard hardening headers on every path', () => {
    const csp = all['Content-Security-Policy']
    for (const directive of ["default-src 'self'", "script-src 'self'", "style-src 'self'", "object-src 'none'",
      "base-uri 'self'", "frame-ancestors 'none'", "form-action 'self'"]) {
      expect(csp).toContain(directive)
    }
    expect(csp).not.toMatch(/unsafe-inline|unsafe-eval|\*/)
    expect(all['X-Content-Type-Options']).toBe('nosniff')
    expect(all['X-Frame-Options']).toBe('DENY')
    expect(all['Referrer-Policy']).toBe('no-referrer')
    expect(all['Permissions-Policy']).toContain('camera=()')
    expect(all['Strict-Transport-Security']).toMatch(/max-age=\d{8}/)
  })

  it('is a static Vite build without an API proxy or rewrites', () => {
    expect(config.framework).toBe('vite')
    expect(config.outputDirectory).toBe('dist')
    expect(config.rewrites).toBeUndefined()
  })
})