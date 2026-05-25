/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{svelte,js}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
        london: ['UnifrakturMaguntia', 'serif'],
      },
      colors: {
        kobser: {
          bg: '#121212',
          surface: '#1E1E1E',
          accent: '#FF9F1C',
          accentHover: '#FFBF69',
          success: '#2EC4B6',
          text: '#FFFFFF',
          muted: '#B3B3B3',
        },
      },
      animation: {
        'spin-slow': 'spin 1.5s linear infinite',
        'slide-up': 'slideUp 0.3s ease-out forwards',
        'bar1': 'musicBar 1s ease-in-out infinite',
        'bar2': 'musicBar 1s ease-in-out infinite 0.2s',
        'bar3': 'musicBar 1s ease-in-out infinite 0.4s',
      },
      keyframes: {
        slideUp: {
          '0%': { transform: 'translateY(100%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
        musicBar: {
          '0%, 100%': { transform: 'scaleY(0.25)' },
          '50%':       { transform: 'scaleY(1)' },
        },
      },
    },
  },
  plugins: [],
};
