
Reimplementation of the Ontology Lookup Service (OLS) API atop a Neo4j database populated by [owl2neo](https://github.com/EBISPOT/owl2neo) - which, unlike the OLS3 indexer, can load all of the OBO foundry ontologies in minutes rather than days.

This reimplementation aims for **100% API compatibility** with OLS3.


<table>
  <thead>
    <th>Endpoint</th>
    <th>Implementation</th>
    <th>Testing</th>
  </thead>
  <tbody>
  <tr>
    <td><code>/api/ontologies</code></td>
    <td>Done</td>
    <td>TODO</td>
    </tr>
    
  <tr>
    <td><code>/api/ontologies/:ontologyId</code></td>
    <td>Done</td>
    <td>TODO</td>
    </tr>
        
  <tr>
    <td><code>/api/ontologies/:ontologyId/terms</code></td>
    <td>Done</td>
    <td>TODO</td>
    </tr>
                  
  <tr>
    <td><code>/api/ontologies/:ontologyId/properties</code></td>
    <td>Done</td>
    <td>TODO</td>
    </tr>
          
            
  <tr>
    <td><code>/api/ontologies/:ontologyId/individuals</code></td>
    <td>TODO</td>
    <td>TODO</td>
    </tr>
          
            
  <tr>
    <td><code>/api/suggest</code></td>
    <td>Done</td>
    <td>TODO</td>
    </tr>
    
  </tbody>
</table>

    
