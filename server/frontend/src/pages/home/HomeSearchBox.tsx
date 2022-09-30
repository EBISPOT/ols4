import {
  Autocomplete,
  CircularProgress,
  Stack,
  TextField
} from "@mui/material";
import React, { useEffect, useState } from "react";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Ontology from "../../model/Ontology";
import Thing from "../../model/Thing";
import { getSearchOptions } from "./homeSlice";

export default function HomeSearchBox() {
  const dispatch = useAppDispatch();
  const options = useAppSelector((state) => state.home.searchOptions);
  const loading = useAppSelector((state) => state.home.loadingSearchOptions);

  const [open, setOpen] = useState<boolean>(false);
  const [query, setQuery] = useState<string>("");

  useEffect(() => {
    dispatch(getSearchOptions(query))
  }, [query]);

  return (
    <Autocomplete
      id="asynchronous-demo"
      style={{ width: 500 }}
      open={open}
      onOpen={() => {
        setOpen(true);
      }}
      onClose={() => {
        setOpen(false);
      }}
      onChange={(e, option) => {}}
      // getOptionSelected={(option:OlsSearchResult, value:OlsSearchResult) => option.iri === value.iri}
      getOptionLabel={(option: Thing) => option.getId()}
      renderOption={(props, option: Thing) => (
        <Stack
          direction="row"
          justifyContent="space-between"
          //   alignItems="center"
        >
          <span>{truncate(option.getName(), 40)}</span>

          {!(option instanceof Ontology) && (
            <span
              style={{
                backgroundColor: "#1976d2",
                padding: "0 10px",
                lineHeight: "1.5",
                fontSize: ".875rem",
                color: "#fff",
                verticalAlign: "middle",
                whiteSpace: "nowrap",
                textAlign: "center",
                borderRadius: "0.6rem",
                textTransform: "uppercase",
              }}
            >
              {option.getOntologyId()}
            </span>
          )}
        </Stack>
      )}
      filterOptions={(x) => x}
      options={options}
      loading={loading}
      renderInput={(params) => (
        <TextField
          {...params}
          label="Search..."
          variant="outlined"
          value={query}
          onChange={(e) => {
            setQuery(e.target.value);
          }}
          InputProps={{
            ...params.InputProps,
            endAdornment: (
              <React.Fragment>
                {loading ? (
                  <CircularProgress color="inherit" size={20} />
                ) : null}
                {params.InputProps.endAdornment}
              </React.Fragment>
            ),
          }}
        />
      )}
    />
  );
}

function truncate(str, len) {
  if (str.length > len) {
    return str.substr(0, len) + "...";
  } else {
    return str;
  }
}
