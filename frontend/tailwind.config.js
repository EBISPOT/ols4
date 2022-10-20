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
        orange: {
          default: "#ffac1b",
          50: "#ffe6ba",
          100: "#ffd58d",
          200: "#ffcd76",
          300: "#ffc45f",
          400: "#ffbc48",
          500: "#ffb431",
          600: "#cc8915",
          700: "#b27812",
          800: "#996710",
          900: "#66440a",
        },
        link: {
          default: "#00827c", // #3b6fb6
          black: "#1a1c1a",
          hover: "#025064", // #193f90
          visited: "#4974a5", // #73459
          tab: "#54585a",
          button: "#707372",
          rule: "#d8d8d8",
        },
        // other colours
        "embl-green": "#5e801a",
      },
      boxShadow: {
        button: "8px 8px 0 theme('colors.link.hover'), -5px -5px rgba(0,0,0,0)",
        "button-hover":
          "4px 4px 0 theme('colors.link.hover'), 2px 2px 4px rgba(0,0,0,.25),-5px -5px rgba(0,0,0,0)",
        "button-active":
          "0px 0px 0 theme('colors.link.hover'), 2px 2px 2px rgba(0,0,0,.125), -5px -5px rgba(0,0,0,0)",
        "button-dark":
          "8px 8px 0 theme('colors.link.tab'), -5px -5px rgba(0,0,0,0)",
        "button-dark-hover":
          "4px 4px 0 theme('colors.link.tab'), 2px 2px 4px rgba(0,0,0,.25),-5px -5px rgba(0,0,0,0)",
        "button-dark-active":
          "0px 0px 0 theme('colors.link.tab'), 2px 2px 2px rgba(0,0,0,.125), -5px -5px rgba(0,0,0,0)",
      },
    },
  },
  plugins: [],
};
