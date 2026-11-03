/**
 * Design tokens. Every colour, size and spacing value used by the staff
 * application comes from here (lint forbids raw colours elsewhere).
 */
export const tokens = {
  font: {
    family: '"IBM Plex Sans", system-ui, -apple-system, "Segoe UI", sans-serif',
    size: { xs: 12, sm: 13, md: 14, lg: 16, xl: 20, xxl: 24 },
  },
  radius: 6,
  space: 8,
  light: {
    background: '#f5f7fa',
    surface: '#ffffff',
    surfaceAlt: '#eef2f6',
    border: '#d5dce4',
    text: '#17202a',
    textMuted: '#536170',
    primary: '#0b5cad',
    primaryContrast: '#ffffff',
    success: '#1f7a3d',
    warning: '#9a5b00',
    danger: '#b42318',
    info: '#175cd3',
    demo: '#7a2e98',
  },
  dark: {
    background: '#0f141a',
    surface: '#161d25',
    surfaceAlt: '#1d2630',
    border: '#2c3846',
    text: '#e6edf3',
    textMuted: '#9aa8b6',
    primary: '#5aa9f5',
    primaryContrast: '#06121f',
    success: '#4cc27a',
    warning: '#e0a43a',
    danger: '#f07063',
    info: '#7cb4ff',
    demo: '#c89be0',
  },
} as const;
