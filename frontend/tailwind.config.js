/** @type {import('tailwindcss').Config} */
const colors = require("tailwindcss/colors");
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx}"],
  theme: {
    extend: {
      colors: {
        // EBI colours
        petrol: {
          default: "#389196",
          50: "#c3dedf",
          100: "#9bc8ca",
          200: "#87bdc0",
          300: "#73b2b5",
          400: "#5fa7ab",
          500: "#4b9ca0",
          600: "#2c7478",
          700: "#276569",
          800: "#21575a",
          900: "#163a3c",
        },
        blue: {
          default: "#008cb5",
          50: "#b2dce8",
          100: "#7fc5da",
          200: "#66bad2",
          300: "#4caecb",
          400: "#32a3c3",
          500: "#1997bc",
          600: "#007090",
          700: "#00627e",
          800: "#00546c",
          900: "#003848",
        },
        grey: {
          1: "#eaeaea", // neutral light
          2: "#666666", // neutral default
          3: "#292929", // neutral dark
          default: "#c3c0ab",
          50: "#edece5",
          100: "#e1dfd5",
          200: "#dbd9cc",
          300: "#d5d2c4",
          400: "#cfccbb",
          500: "#c9c6b3",
          600: "#9c9988",
          700: "#888677",
          800: "#757366",
          900: "#4e4c44",
        },
        green: {
          default: "#9fcc3b",
          50: "#e2efc4",
          100: "#cfe59d",
          200: "#c5e089",
          300: "#bbdb75",
          400: "#b2d662",
          500: "#a8d14e",
          600: "#7fa32f",
          700: "#6f8e29",
          800: "#5f7a23",
          900: "#3f5117",
        },
        yellow: {
          default: "#e6b222",
          50: "#f7e7bc",
          100: "#f2d890",
          200: "#f0d07a",
          300: "#edc964",
          400: "#ebc14e",
          500: "#e8b938",
          600: "#b88e1b",
          700: "#a17c17",
          800: "#8a6a14",
          900: "#5c470d",
        },
        "embl-green": "#5e801a",
      },
    },
  },
  plugins: [],
};
