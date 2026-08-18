import {
  uiChartPalette,
  uiThemeTokens,
  type UiChartPalette,
  type UiThemeTokens,
} from './design-tokens.ts';

export const UI_THEME_MESSAGE_TYPE = 'zenvis:ui' as const;
export const UI_THEME_READY_TYPE = 'zenvis:ui:ready' as const;
export const UI_THEME_CONTRACT_VERSION = '1.0.0' as const;

export interface UiThemeContractV1 {
  readonly type: typeof UI_THEME_MESSAGE_TYPE;
  readonly contractVersion: typeof UI_THEME_CONTRACT_VERSION;
  readonly themeId: 'zenvis-light';
  readonly colorScheme: 'light';
  readonly density: 'compact';
  readonly locale: string;
  readonly timezone: string;
  readonly reducedMotion: boolean;
  readonly tokens: UiThemeTokens;
  readonly chartPalette: UiChartPalette;
}

export interface UiThemeContractOptions {
  locale?: string;
  timezone?: string;
  reducedMotion?: boolean;
}

function getDefaultLocale(): string {
  return typeof navigator !== 'undefined' && navigator.language ? navigator.language : 'zh-CN';
}

function getDefaultTimezone(): string {
  try {
    return Intl.DateTimeFormat().resolvedOptions().timeZone || 'Asia/Shanghai';
  } catch {
    return 'Asia/Shanghai';
  }
}

function getDefaultReducedMotion(): boolean {
  return (
    typeof window !== 'undefined' &&
    typeof window.matchMedia === 'function' &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches
  );
}

export function buildUiThemeContractV1(
  options: UiThemeContractOptions = {},
): Readonly<UiThemeContractV1> {
  return Object.freeze({
    type: UI_THEME_MESSAGE_TYPE,
    contractVersion: UI_THEME_CONTRACT_VERSION,
    themeId: 'zenvis-light',
    colorScheme: 'light',
    density: 'compact',
    locale: options.locale || getDefaultLocale(),
    timezone: options.timezone || getDefaultTimezone(),
    reducedMotion: options.reducedMotion ?? getDefaultReducedMotion(),
    tokens: uiThemeTokens,
    chartPalette: uiChartPalette,
  });
}

/**
 * Resolve a postMessage target origin from a sanitized iframe URL.
 * Only explicit HTTP(S) origins are accepted; opaque and script-like URLs are rejected.
 */
export function resolveUiTargetOrigin(iframeUrl: string, applicationOrigin: string): string | null {
  if (!iframeUrl || !applicationOrigin) {
    return null;
  }

  try {
    const base = new URL(applicationOrigin);
    const target = new URL(iframeUrl, base);
    if (
      !['http:', 'https:'].includes(base.protocol) ||
      !['http:', 'https:'].includes(target.protocol)
    ) {
      return null;
    }
    return target.origin;
  } catch {
    return null;
  }
}

export function isUiThemeReadyPayload(payload: unknown): boolean {
  if (!payload || typeof payload !== 'object') {
    return false;
  }

  const candidate = payload as { type?: unknown; contractVersion?: unknown };
  return (
    candidate.type === UI_THEME_READY_TYPE &&
    (candidate.contractVersion === undefined ||
      candidate.contractVersion === UI_THEME_CONTRACT_VERSION)
  );
}

export interface UiThemeReadyEventLike {
  readonly data: unknown;
  readonly origin: string;
  readonly source: unknown;
}

export function isUiThemeReadyEvent(
  event: UiThemeReadyEventLike,
  targetWindow: unknown,
  targetOrigin: string,
): boolean {
  return (
    Boolean(targetWindow) &&
    event.source === targetWindow &&
    event.origin === targetOrigin &&
    isUiThemeReadyPayload(event.data)
  );
}
