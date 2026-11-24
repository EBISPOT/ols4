package uk.ac.ebi.spot.ols.repository.transforms;

import com.google.gson.JsonElement;

public class JsonTransformer {

    public static JsonElement transformJson(
            JsonElement object,
            String lang,
            JsonTransformOptions options
    ) {
        object = LocalizationTransform.transform(object, lang);
        object = RemoveLiteralDatatypesTransform.transform(object);

        if(options.resolveReferences) {
            object = ResolveReferencesTransform.transform(object);
        }

        if(options.manchesterSyntax) {
            object = ManchesterSyntaxTransform.transform(object);
        }
        if(options.view == JsonTransformOptions.TreeView.tree) {
            object = TreeNodeViewTransform.transform(object);
        }

        return object;
    }
    
}
