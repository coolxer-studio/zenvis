import { ls } from '@u/local-storage';
import { getAuthSalt, getAuthToken, setAuthToken } from '@u/auth-session';

const key = 'aqfuzt-v2-';

export function getToken(): string {
  return getAuthToken();
}

export function getSalt(): string {
  return getAuthSalt();
}

export function setToken(token: string): void {
  setAuthToken(token);
}

export function setSalt(salt: string): void {
  setAuthToken(undefined, salt);
}

export function getLang(): string {
  return ls.get(key + 'lang') || '';
}

export function setLang(lang: string): void {
  ls.set(key + 'lang', lang);
}

export function getOptionPermissions(): string {
  return ls.get(key + 'option-permissions') || '';
}

export function setOptionPermissions(permissions: string): void {
  ls.set(key + 'option-permissions', permissions);
}
