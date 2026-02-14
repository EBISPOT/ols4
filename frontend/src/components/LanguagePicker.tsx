import { KeyboardArrowDown } from "@mui/icons-material";
import countryCodeToFlagEmoji from "country-code-to-flag-emoji";
import Ontology from "../model/Ontology";

export default function LanguagePicker({
  ontology,
  lang,
  onChangeLang,
}: {
  ontology: Ontology;
  lang: string;
  onChangeLang: (lang: string) => void;
}) {
  // Filter to only show valid language codes (2-letter ISO codes or locale format like en-GB)
  const validLanguages = ontology.getLanguages().filter((lang) => {
    const countryCode = lang.split('-').pop() || lang;
    return /^[a-zA-Z]{2}$/.test(countryCode);
  });

  return (
    <div className="flex items-center group relative text-md">
      <select
        className="input-default appearance-none pr-7 z-20 bg-transparent cursor-pointer max-w-xs"
        onChange={(e) => {
          onChangeLang(e.target.value);
        }}
        value={lang}
      >
        {validLanguages.map((lang) => {
          return (
            <option key={lang} value={lang}>
              {getEmoji(lang)}&nbsp;&nbsp;{lang}
            </option>
          );
        })}
      </select>
      <div className="absolute right-2 top-2 z-10 text-neutral-default group-focus:text-neutral-dark group-hover:text-neutral-dark">
        <KeyboardArrowDown fontSize="medium" />
      </div>
    </div>
  );
}

function getEmoji(lang: string) {
  // handle special cases
  const specialCases: { [key: string]: string } = {
    en: "en-GB",
    cs: "cs-CZ",
    zh: "zh-CN",
  };

  const code = specialCases[lang] || lang;

  return countryCodeToFlagEmoji(code);
}
