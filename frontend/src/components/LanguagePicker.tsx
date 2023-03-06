import { FormControl, InputLabel, MenuItem, Select } from "@mui/material";
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
  return (
      <FormControl variant="standard" fullWidth size="small">
        <InputLabel id="demo-simple-select-label">Language</InputLabel>
        <Select
          className="my-0"
          labelId="demo-simple-select-label"
          id="demo-simple-select"
          value={lang}
          label="Language"
          onChange={(ev) => onChangeLang(ev.target.value)}
        >
          {ontology.getLanguages().map((lang) => {
            return (
              <MenuItem key={lang} value={lang}>
                {lang}
              </MenuItem>
            );
          })}
        </Select>
      </FormControl>
  );
}
