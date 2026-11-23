package uk.ac.ebi.spot.ols.repository.transforms;

import io.swagger.v3.oas.annotations.Parameter;

public class JsonTransformOptions {

    @Parameter(name="resolveReferences", description="Whether to resolve any referenced entities inline in the response JSON. Otherwise linked entities will be available in the linkedEntities map.", required=false)
    public boolean resolveReferences = false;

    @Parameter(name="manchesterSyntax", description="Whether to convert class expressions to Manchester syntax. If false a JSON representation of the class expressions will be returned.", required=false)
    public boolean manchesterSyntax = false;
    
  public boolean isResolveReferences() { return resolveReferences; }
  public void setResolveReferences(boolean resolveReferences) { this.resolveReferences = resolveReferences; }

    @Parameter(name="view", description="Which fields of each entity to return. 'full' (the default) returns the whole entity. 'tree' returns only what a hierarchy tree needs: identity (iri, curie, shortForm, ontologyId, type), label, obsolescence, defining ontology, parent links (directParent, hierarchicalParent and their reification), child flags and descendant counts; annotations, synonyms, definitions, ancestor closures and linkedEntities are left out.", required=false)
    public TreeView view = TreeView.full;

    public enum TreeView { full, tree }

  public boolean isManchesterSyntax() { return manchesterSyntax; }
  public void setManchesterSyntax(boolean manchesterSyntax) { this.manchesterSyntax = manchesterSyntax; }

  public TreeView getView() { return view; }
  public void setView(TreeView view) { this.view = view; }
}

