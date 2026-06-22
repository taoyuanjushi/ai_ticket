/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{ts,tsx}"],
  theme: {
    extend: {
      colors: {
        ink: "#17202a",
        muted: "#697386",
        line: "#d9e0ea",
        panel: "#f6f8fb",
        brand: "#1d4ed8",
        teal: "#0f766e",
        amber: "#b45309",
        danger: "#b42318",
      },
      boxShadow: {
        soft: "0 14px 35px rgba(24, 39, 75, 0.08)",
      },
    },
  },
  plugins: [],
};
