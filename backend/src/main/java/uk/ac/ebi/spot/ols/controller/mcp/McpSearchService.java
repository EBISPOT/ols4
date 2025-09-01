package uk.ac.ebi.spot.ols.controller.mcp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import uk.ac.ebi.spot.ols.repository.EntityRepository;

@Service
public class McpSearchService {

    @Autowired
    EntityRepository entityRepository;

    
}
