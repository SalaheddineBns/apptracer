/**
 * Keeps only paths inside the application: never an absolute URL, a scheme or a protocol-relative
 * URL (`//host`), and never `/login` itself. Returns `null` otherwise.
 */
export function safeReturnUrl(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')) {
    return null;
  }
  if (Array.from(value).some((char) => char.charCodeAt(0) < 32) || value === '/login' || value.startsWith('/login?') || value.startsWith('/login/')) {
    return null;
  }
  return value;
}
