/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#12283f",
        muted: "#63768b",
        canvas: "#f4f7fb",
        bank: "#0b6b5b",
        "bank-soft": "#e6f4f0",
      },
      boxShadow: {
        card: "0 16px 45px rgba(18, 40, 63, 0.08)",
      },
    },
  },
  plugins: [],
};
