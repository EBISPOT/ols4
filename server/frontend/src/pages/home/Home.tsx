import { BarChart } from "@mui/icons-material";
import { Box, Grid, Link, Stack, Theme, Typography } from "@mui/material";
import { createStyles, makeStyles } from "@mui/styles";
import { Fragment, useEffect } from "react";
import { Timeline } from "react-twitter-widgets";
import { useAppDispatch, useAppSelector } from "../../app/hooks";
import Header from "../../components/Header";
import Spinner from "../../components/Spinner";
import HomeSearchBox from "./HomeSearchBox";
import { getStats } from "./homeSlice";

const useStyles: any = makeStyles((theme: Theme) => createStyles({}));

export default function Home() {
  const dispatch = useAppDispatch();
  const stats = useAppSelector((state) => state.home.stats);

  let classes = useStyles();

  useEffect(() => {
    dispatch(getStats());
  }, []);

  document.title = "Ontology Lookup Service (OLS)";
  return (
    <Fragment>
      <Header section="home" />
      <main>
        <Grid container spacing={0}>
          <Grid item container xs={9} spacing={0} p={1}>
            <Grid item xs={12}>
              <Typography fontSize="h5.fontSize" py={1}>
                Welcome to the EMBL-EBI Ontology Lookup Service
              </Typography>

              <HomeSearchBox />

              {/* <TextField fullWidth size="small" label="Search OLS..." inputProps={{
									startAdornment: <InputAdornment position="start"><Search /></InputAdornment>,
									classes: {
										adornedStart: classes.adornedStart
									       }
								}}/> */}
            </Grid>
            <Grid item xs={6} py={1}>
              Examples: diabetes, GO:0098743
            </Grid>
            <Grid item xs={6} py={1}>
              Looking for a particular ontology?
            </Grid>
          </Grid>
          <Grid item xs={3}>
            <Stack direction="row">
              <BarChart /> <Box fontSize="h6.fontSize">Data Content</Box>
            </Stack>
            {stats ? (
              <Box>
                <ul>
                  <li>
                    {stats.numberOfOntologies.toLocaleString()} ontologies
                  </li>
                  <li>{stats.numberOfClasses.toLocaleString()} classes</li>
                  <li>
                    {stats.numberOfProperties.toLocaleString()} properties
                  </li>
                  <li>
                    {stats.numberOfIndividuals.toLocaleString()} individuals
                  </li>
                </ul>
              </Box>
            ) : (
              <Spinner />
            )}
          </Grid>

          <Grid item xs={3} p={1}>
            <Link href="about">About OLS</Link>
            <p>
              The Ontology Lookup Service (OLS) is a repository for biomedical
              ontologies that aims to provide a single point of access to the
              latest ontology versions. You can browse the ontologies through
              the website as well as programmatically via the OLS API. OLS is
              developed and maintained by the Samples, Phenotypes and Ontologies
              Team (SPOT) at EMBL-EBI.
            </p>
          </Grid>
          <Grid item xs={3} p={1}>
            <Link href="https://www.ebi.ac.uk/spot/ontology/">
              Related Tools
            </Link>
            <p>
              In addition to OLS the SPOT team also provides the OxO, Zooma and
              Webulous services. OxO provides cross-ontology mappings between
              terms from different ontologies. Zooma is a service to assist in
              mapping data to ontologies in OLS and Webulous is a tool for
              building ontologies from spreadsheets.
            </p>
          </Grid>
          <Grid item xs={3} p={1}>
            <Link href="https://github.com/EBISPOT/OLS/issues">
              Report an Issue
            </Link>
            <p>
              For feedback, enquiries or suggestion about OLS or to request a
              new ontology please use our GitHub issue tracker. For
              announcements relating to OLS, such as new releases and new
              features sign up to the OLS announce mailing list
            </p>
          </Grid>
          <Grid item xs={3}>
            <Timeline
              dataSource={{
                sourceType: "profile",
                screenName: "EBIOLS",
              }}
              options={{
                height: "400",
              }}
            />
          </Grid>
        </Grid>
      </main>
    </Fragment>
  );

  // return <div>
  //     {/* <div>Logged in as {getToken().authEmail} with token {getToken().auth}</div> */}
  //         <Breadcrumbs>
  //             <Link color="inherit" href="/">
  //                 Projects
  //             </Link>
  //         </Breadcrumbs>
  //     <h2>Projects</h2>
  //     <ProjectList />
  // </div>
}
