/** Bảng màu chép lại từ `web/tailwind.config.js` để 2 app nhìn thống nhất. */
export const colors = {
  brand50: '#eff6ff',
  brand100: '#dbeafe',
  brand500: '#3b82f6',
  brand600: '#2563eb',
  brand700: '#1d4ed8',
  brand900: '#1e3a8a',

  slate50: '#f8fafc',
  slate100: '#f1f5f9',
  slate200: '#e2e8f0',
  slate300: '#cbd5e1',
  slate400: '#94a3b8',
  slate500: '#64748b',
  slate600: '#475569',
  slate700: '#334155',
  slate800: '#1e293b',
  slate900: '#0f172a',

  emerald50: '#ecfdf5',
  emerald700: '#047857',
  emerald800: '#065f46',

  amber50: '#fffbeb',
  amber400: '#fbbf24',
  amber700: '#b45309',

  red50: '#fef2f2',
  red600: '#dc2626',
  red700: '#b91c1c',

  white: '#ffffff',
} as const;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24, xxl: 32 } as const;

export const radius = { sm: 8, md: 12, lg: 16, pill: 999 } as const;
