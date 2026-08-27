import { ls } from '@u/local-storage';

const LEGACY_KEY_PREFIX = 'aqfuzt-v2-';

export const AUTH_SESSION_KEYS = {
  login: '__login__',
  user: '__user__',
  permission: '__permission__',
  token: `${LEGACY_KEY_PREFIX}token`,
  salt: `${LEGACY_KEY_PREFIX}salt`,
};

export type LoginSessionPayload = {
  token?: string;
  salt?: string;
  username?: string;
  role?: string;
  permissions?: string[];
  user?: unknown;
  permission?: unknown;
  [key: string]: unknown;
};

const readString = (key: string): string => {
  const value = ls.get(key);
  return typeof value === 'string' ? value : '';
};

export function getAuthToken(): string {
  return readString(AUTH_SESSION_KEYS.token);
}

export function getAuthSalt(): string {
  return readString(AUTH_SESSION_KEYS.salt);
}

export function setAuthToken(token?: string, salt?: string): void {
  if (token) {
    ls.set(AUTH_SESSION_KEYS.token, token);
    ls.set(AUTH_SESSION_KEYS.login, 'ok');
  }
  if (salt) {
    ls.set(AUTH_SESSION_KEYS.salt, salt);
  }
}

export function getUserInfo<T = unknown>(): T | null {
  return ls.get(AUTH_SESSION_KEYS.user) || null;
}

export function getPermissionList<T = unknown>(): T | null {
  return ls.get(AUTH_SESSION_KEYS.permission) || null;
}

export function hasLoginSession(): boolean {
  return Boolean(ls.get(AUTH_SESSION_KEYS.login) || getAuthToken());
}

export function setLoginSession(payload: LoginSessionPayload): void {
  const token = payload.token || (payload.user as LoginSessionPayload | undefined)?.token;
  const salt = payload.salt || (payload.user as LoginSessionPayload | undefined)?.salt;

  setAuthToken(token, salt);
  ls.set(AUTH_SESSION_KEYS.login, 'ok');
  ls.set(
    AUTH_SESSION_KEYS.user,
    payload.user || {
      name: payload.username || '',
      username: payload.username || '',
      role: payload.role || '',
    },
  );
  ls.set(AUTH_SESSION_KEYS.permission, payload.permission || payload.permissions || []);
}

export function clearLoginSession(): void {
  Object.values(AUTH_SESSION_KEYS).forEach(key => {
    ls.remove(key);
  });
}

