// Public, build-time configuration only. Anything prefixed VITE_ ends up in the
// browser bundle, so never put credentials here (no OpenAlex key, no DB password).
//
// VITE_API_BASE_URL: empty (default) = same origin; in `npm run dev` the Vite
// proxy forwards /api and /actuator to the backend (see vite.config.ts).
export const apiBaseUrl: string = import.meta.env.VITE_API_BASE_URL ?? ''
