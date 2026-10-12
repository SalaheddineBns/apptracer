/**
 * Ne retient que les chemins internes à l'application : jamais d'URL absolue, de schéma ni d'URL
 * relative au protocole (`//hôte`), et jamais `/login` lui-même. Retourne `null` sinon.
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
