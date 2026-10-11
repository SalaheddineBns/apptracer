import { Role } from '../api/generated';

// RG-03-10 : MANAGER inclut les droits USER ; ADMIN n'inclut ni USER ni MANAGER (comme côté back).
const GRANTED: Record<Role, readonly Role[]> = {
  [Role.User]: [Role.User],
  [Role.Manager]: [Role.Manager, Role.User],
  [Role.Admin]: [Role.Admin],
};

export function roleSatisfies(actual: Role, required: Role): boolean {
  return GRANTED[actual].includes(required);
}

/** Page d'accueil du rôle (architecture §7.2, H1). */
export function homeUrlFor(role: Role): string {
  return role === Role.Admin ? '/admin/comptes' : '/journal';
}
