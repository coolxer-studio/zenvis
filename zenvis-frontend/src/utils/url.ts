const trimTrailingSlash = (value: string): string => value.replace(/\/+$/, '');

export const appBaseUrl = trimTrailingSlash(import.meta.env.VITE_BASE_URL || '');

export function isHttpUrl(url: string): boolean {
  return /^https?:\/\//i.test(url);
}

export function withBaseUrl(path: string): string {
  if (!path || isHttpUrl(path)) return path;

  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  if (!appBaseUrl || normalizedPath.startsWith(`${appBaseUrl}/`)) {
    return normalizedPath;
  }
  return `${appBaseUrl}${normalizedPath}`;
}

export function withCacheBuster(url: string): string {
  if (!url) return '';
  const separator = url.includes('?') ? '&' : '?';
  return `${url}${separator}t=${Date.now()}`;
}

export function getAssetUrl(url: string): string {
  if (!url) return '';
  return withCacheBuster(isHttpUrl(url) ? url : withBaseUrl(url));
}

const unsafeIframeProtocolPattern = /^(javascript|data|blob|file):/i;
const fallbackIframeUrl = '/#/ExceptionPage404';

const getIframeAllowedOrigins = (): string[] => {
  return (import.meta.env.VITE_IFRAME_ALLOWED_ORIGINS || '')
    .split(',')
    .map(origin => origin.trim())
    .filter(Boolean);
};

export function sanitizeIframeUrl(url: string, fallback = fallbackIframeUrl): string {
  const candidate = url?.trim();
  if (!candidate || unsafeIframeProtocolPattern.test(candidate) || candidate.startsWith('//')) {
    return fallback;
  }

  try {
    const parsedUrl = new URL(candidate, window.location.origin);
    if (!['http:', 'https:'].includes(parsedUrl.protocol)) {
      return fallback;
    }

    const allowedOrigins = getIframeAllowedOrigins();
    const isSameOrigin = parsedUrl.origin === window.location.origin;
    const isAllowedOrigin = allowedOrigins.length === 0 || allowedOrigins.includes(parsedUrl.origin);
    return isSameOrigin || isAllowedOrigin ? parsedUrl.href : fallback;
  } catch (error) {
    return fallback;
  }
}
