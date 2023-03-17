/** @type {import('tailwindcss').Config} */
const colors = require("tailwindcss/colors");
module.exports = {
  content: ["./src/**/*.{js,jsx,ts,tsx,css}"],
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
          default: "#f4941c",
          50: "#fbdeba",
          100: "#f9c98d",
          200: "#f8be76",
          300: "#f7b460",
          400: "#f6a949",
          500: "#f59e32",
          600: "#db8519",
          700: "#c37616",
          800: "#aa6713",
          900: "#7a4a0e",
        },
        neutral: {
          default: "#666666",
          light: "#eaeaea",
          dark: "#525252",
          black: "#292929",
        },
        link: {
          default: "#00827c",
          light: "#6eaba6",
          dark: "#155552",
          hover: "#106462",
          visited: "#2185a9",
        },
        // other colours
        embl: {
          green: {
            default: "#18974C",
            lightest: "#D0DEBB",
            light: "#6CC24A",
            dark: "#007B53",
            darkest: "#0A5032",
          },
          grey: {
            default: "#707372",
            lightest: "#D0D0CE",
            light: "#A8A99E",
            dark: "#54585A",
            darkest: "#373A36",
          },
          blue: {
            default: "#3B6FB6",
            light: "#8BB8E8",
            dark: "#193F90",
          },
          purple: {
            default: "#734595",
            light: "#CBA3D8",
            dark: "#563D82",
          },
          orange: {
            default: "#F49E17",
            light: "#EFC06E",
            dark: "#B65417",
          },
          yellow: {
            default: "#F4C61F",
            light: "#FDD757",
            dark: "#FDD757",
          },
          lime: {
            default: "#A1BE1F",
            light: "#E2E868",
            dark: "#7FB428",
          },
          red: {
            default: "#D41645",
            light: "#E58F9E",
            dark: "#A6093D",
          },
        },
      },
      boxShadow: {
        button: "8px 8px 0 theme(colors.link.hover), -5px -5px rgba(0,0,0,0)",
        "button-hover":
          "4px 4px 0 theme(colors.link.hover), 2px 2px 4px rgba(0,0,0,.25),-5px -5px rgba(0,0,0,0)",
        "button-active":
          "0px 0px 0 theme(colors.link.hover), 2px 2px 2px rgba(0,0,0,.125), -5px -5px rgba(0,0,0,0)",
        "button-orange":
          "8px 8px 0 theme(colors.orange.800), -5px -5px rgba(0,0,0,0)",
        "button-orange-hover":
          "4px 4px 0 theme(colors.orange.800), 2px 2px 4px rgba(0,0,0,.25),-5px -5px rgba(0,0,0,0)",
        "button-orange-active":
          "0px 0px 0 theme(colors.orange.800), 2px 2px 2px rgba(0,0,0,.125), -5px -5px rgba(0,0,0,0)",
        "button-disabled":
          "8px 8px 0 theme(colors.neutral.dark), -5px -5px rgba(0,0,0,0)",
        card: "0px 2px 6px theme(colors.neutral.dark / 50%)",
        input: "0 0 0 1px theme(colors.neutral.dark)",
      },
    },
  },
  plugins: [],
};
