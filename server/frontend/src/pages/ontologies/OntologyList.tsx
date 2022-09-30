import { useHistory } from "react-router-dom";
import OlsDatatable, { Column } from "../../components/OlsDatatable";
import Ontology from "../../model/Ontology";

const columns: readonly Column[] = [
  // {
  //     name: 'Debug',
  //     sortable: true,
  //     selector: (ontology:Ontology) => JSON.stringify(ontology),
  //     wrap: true
  // },
  {
    name: "",
    sortable: false,
    selector: (ontology: Ontology) =>
      ontology.getLogoURL() && <img width={50} src={ontology.getLogoURL()} />,
  },
  {
    name: "Name",
    sortable: true,
    selector: (ontology: Ontology) => ontology.getName(),
  },
  {
    name: "Description",
    sortable: true,
    selector: (ontology: Ontology) => ontology.getDescription(),
  },
];

export default function OntologyList() {
  let history = useHistory();

  return (
    <OlsDatatable
      columns={columns}
      endpoint={`/api/v2/ontologies`}
      instantiateRow={(row) => new Ontology(row)}
      onClickRow={(ontology: Ontology) => {
        history.push("/ontologies/" + ontology.getOntologyId());
      }}
    />
  );
}
