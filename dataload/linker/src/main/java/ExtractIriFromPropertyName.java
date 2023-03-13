import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractIriFromPropertyName {

    /* We have some added (by owl2json) properties like
    		relatedTo+http://....
		negativePropertyAssertion+http://...
	which consist of some alphabetic prefix, a + character, and the IRI
	this extracts the IRIs from them
	otherwise just returns the property name
	*/
    private static final Pattern namePattern = Pattern.compile( "^([A-z]+)\\+(.+)$" );
    public static String extract(String propertyName) {
        Matcher m = namePattern.matcher(propertyName);

        if(m.find()) {
            return m.group(2);
        } else {
            return propertyName;
        }
    }

}
