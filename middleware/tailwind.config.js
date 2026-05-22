/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ['./static/**/*.{html,js}'],
  darkMode: 'class',
  theme: {
    extend: {
      fontFamily: {
        sans: ['Inter', 'system-ui', '-apple-system', 'sans-serif'],
      },
      colors: {
        peel: {
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
      },
      keyframes: {
        slideUp: {
          '0%': { transform: 'translateY(100%)', opacity: '0' },
          '100%': { transform: 'translateY(0)', opacity: '1' },
        },
      },
    },
  },
  plugins: [],
};
