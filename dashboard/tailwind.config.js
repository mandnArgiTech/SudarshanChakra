/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  theme: {
    extend: {
      colors: {
        'sc-bg': '#0a0e17',
        'sc-surface': '#111827',
        'sc-surface-alt': '#1a2235',
        'sc-border': '#1e293b',
        'sc-accent': '#f59e0b',
        'sc-accent-dim': '#92400e',
        'sc-critical': '#ef4444',
        'sc-high': '#f97316',
        'sc-warning': '#eab308',
        'sc-success': '#22c55e',
        'sc-info': '#3b82f6',
        'sc-text': '#f1f5f9',
        'sc-text-dim': '#94a3b8',
        'sc-text-muted': '#64748b',
      },
      fontFamily: {
        mono: ['"JetBrains Mono"', 'monospace'],
      },
      animation: {
        'pulse-dot': 'pulse-dot 2s ease-in-out infinite',
      },
      keyframes: {
        'pulse-dot': {
          '0%, 100%': { opacity: '1' },
          '50%': { opacity: '0.3' },
        },
      },
    },
  },
  plugins: [],
};
