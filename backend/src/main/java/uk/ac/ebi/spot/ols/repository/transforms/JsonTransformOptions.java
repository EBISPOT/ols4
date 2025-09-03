package uk.ac.ebi.spot.ols.repository.transforms;

import io.swagger.v3.oas.annotations.Parameter;

public class JsonTransformOptions {

    @Parameter(name="resolveReferences", description="Whether to resolve any referenced entities inline in the response JSON. Otherwise linked entities will be available in the linkedEntities map.", required=false)
    public boolean resolveReferences = false;

    @Parameter(name="manchesterSyntax", description="Whether to convert class expressions to Manchester syntax. If false a JSON representation of the class expressions will be returned.", required=false)
    public boolean manchesterSyntax = false;
    
  public boolean isResolveReferences() { return resolveReferences; }
  public void setResolveReferences(boolean resolveReferences) { this.resolveReferences = resolveReferences; }

  public boolean isManchesterSyntax() { return manchesterSyntax; }
  public void setManchesterSyntax(boolean manchesterSyntax) { this.manchesterSyntax = manchesterSyntax; }
}

