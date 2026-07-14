/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        primary: '#2A1345',
        'on-primary': '#FFFFFF',
        secondary: '#A78BC9',
        accent: '#EC4899',
        background: '#1B0B2E',
        foreground: '#FAFAFA',
        muted: '#3B1B5C',
        border: '#6D3FA0',
        destructive: '#DC2626',
        success: '#22C55E',
        warning: '#F59E0B',
      },
      fontFamily: {
        heading: ['"Press Start 2P"', 'monospace'],
        body: ['VT323', 'monospace'],
        mono: ['VT323', 'monospace'],
      },
      animation: {
        'pulse-slow': 'pulse 3s cubic-bezier(0.4, 0, 0.6, 1) infinite',
        'glow': 'glow 2s ease-in-out infinite alternate',
        'spin-slow': 'spin 3s linear infinite',
      },
      keyframes: {
        glow: {
          '0%': { boxShadow: '0 0 5px #EC4899, 0 0 10px #EC4899' },
          '100%': { boxShadow: '0 0 10px #EC4899, 0 0 20px #EC4899, 0 0 40px #EC4899' },
        },
      },
      boxShadow: {
        'accent': '0 0 10px rgba(236, 72, 153, 0.5)',
        'accent-lg': '0 0 20px rgba(236, 72, 153, 0.7)',
      },
    },
  },
  plugins: [],
}
