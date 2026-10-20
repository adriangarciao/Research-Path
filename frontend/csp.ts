import type { Plugin } from 'vite'

/**
 * Origin of the API the built UI may call, from VITE_API_BASE_URL. Empty = same origin.
 * Only https origins (or http on localhost/127.0.0.1 for local testing) are accepted; anything else fails the build.
 */
export function apiOrigin(apiBase: string | undefined): string | null {
  if (!apiBase) return null
  let url: URL
  try {
    url = new URL(apiBase)
  } catch {
    throw new Error(`VITE_API_BASE_URL is not an absolute URL: ${apiBase}`)
  }
  const local = url.hostname === 'localhost' || url.hostname === '127.0.0.1'
  if (url.protocol !== 'https:' && !(url.protocol === 'http:' && local)) {
    throw new Error(`VITE_API_BASE_URL must use https (http only for localhost): ${apiBase}`)
  }
  if (url.username || url.password || url.search || url.hash || (url.pathname !== '/' && url.pathname !== '')) {
    throw new Error(`VITE_API_BASE_URL must be a bare origin such as https://api.example.com: ${apiBase}`)
  }
  return url.origin
}

/** The connect-src the page itself declares: its own origin plus the exact API origin. */
export function connectSrc(apiBase: string | undefined): string {
  const origin = apiOrigin(apiBase)
  return origin ? `connect-src 'self' ${origin}` : "connect-src 'self'"
}

/**
 * Adds a meta CSP that narrows connect-src to the exact API origin. The hosting headers (vercel.json) carry the rest
 * of the policy; a browser enforces both, so requests are allowed only where both policies agree.
 */
export function cspMetaPlugin(apiBase: string | undefined): Plugin {
  const policy = connectSrc(apiBase)
  return {
    name: 'researchpath-csp-meta',
    apply: 'build',
    transformIndexHtml(html) {
      return html.replace('<head>', `<head>\n    <meta http-equiv="Content-Security-Policy" content="${policy}" />`)
    },
  }
}
