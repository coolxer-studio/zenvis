export const uiThemeTokens = Object.freeze({
  primary: '#2f5ee5',
  primaryHover: '#274fca',
  primaryActive: '#2145b2',
  primarySoft: '#eef3ff',
  accent: '#0f9fa3',
  success: '#0f9f74',
  warning: '#c27a08',
  danger: '#d1435b',
  info: '#2563c7',
  pageBackground: '#f2f5fa',
  surface: '#ffffff',
  subtleBackground: '#f7f9fc',
  textPrimary: '#15233a',
  textSecondary: '#47556b',
  textMuted: '#66758a',
  border: '#dce4ef',
  divider: '#e8edf4',
  radiusSmall: '6px',
  radiusMedium: '8px',
  radiusLarge: '12px',
  controlHeight: '32px',
  headerHeight: '60px',
  motionFast: '120ms',
  motionBase: '180ms',
  motionSlow: '240ms',
} as const);

export const uiChartPalette = Object.freeze([
  '#2f5ee5',
  '#0f9fa3',
  '#7c5ce5',
  '#0f9f74',
  '#d89a20',
  '#d1435b',
  '#4f83cc',
  '#718096',
] as const);

export type UiThemeTokens = Readonly<typeof uiThemeTokens>;
export type UiChartPalette = Readonly<typeof uiChartPalette>;
