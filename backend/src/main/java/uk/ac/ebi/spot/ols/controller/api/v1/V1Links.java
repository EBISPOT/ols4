package uk.ac.ebi.spot.ols.controller.api.v1;

import org.springframework.hateoas.Link;
import org.springframework.hateoas.server.mvc.WebMvcLinkBuilder;

final class V1Links {
    private V1Links() {
    }

    // Legacy V1 child links omit the self link's language query. HATEOAS 3
    // preserves that query when appending a path, so remove it explicitly.
    static Link child(WebMvcLinkBuilder parent, String path) {
        return Link.of(parent.slash(path).toUriComponentsBuilder()
                .replaceQuery(null).build().toUriString());
    }
}
