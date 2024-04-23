# OLS4 Widgets

OLS4 Widgets is a library for various ols components. At the moment only one component
is exposed called `entityTree` which is ideal for seamless ontology visualisations.

## Installation

You can install OLS4 Widgets using npm:

```bash
npm i @ebi-ols/ols4-widgets
```

This command installs the latest version of OLS4 widgets and saves it as a dependency in your project's `package.json` file.

## Usage

After installation, you can use OLS4 Widgets in your project by including the necessary files and 
initializing the widget with a simple javaScript command. Here's a quick example of 
how to display the chebi tree in a **React** application:

## 1. Import the dependencies in the js file you want to render the component:

```javascript
import '@ebi-ols/ols4-widgets/treestyles.css'
import { useEffect, useRef } from 'react';
import { createEntityTree } from '@ebi-ols/ols4-widgets/ols4_widgets';
```

## 2. Create the function to render the tree:

```javascript
function EntityTree() {

  let div = useRef()

  useEffect(() => {
    if(div.current) {
      createEntityTree({
        ontologyId: "chebi",
        apiUrl: "https://www.ebi.ac.uk/ols4/"
      }, div.current);
    }
  }, [div])

  return <div ref={div}></div>
}

```
**NOTE:** The main point to notice here is the ontologyId and the apiUrl. The ontologyId is the id of the ontology you want to display and the apiUrl is the base url of the OLS4 API.

### Partial rendering of the tree

You can specify a term uri e.g. water (http://purl.obolibrary.org/obo/CHEBI_15377) as a prop to the `createEntityTree` function to render a partial tree. If you specify this prop i.e. `specifiedRootIri`,
the tree will render starting from the specified term.

```javascript
useEffect(() => {
    if(div.current) {
      createEntityTree({
        ontologyId: "chebi",
        specifiedRootIri: "http://purl.obolibrary.org/obo/CHEBI_15377",
        apiUrl: "https://www.ebi.ac.uk/ols4/"
      }, div.current);
    }
  }, [div])
```

## 3. Add the element where you want the tree to appear in your HTML file:

```javascript
function App() {
return (
<div className="App">
    <EntityTree />
</div>
);
}
```
### Example of the complete ontology rendered tree

![chebi-tree-render](https://github.com/EBISPOT/ols4/assets/13108541/b9e14c70-6be0-4007-8311-91605087d5ad)

### Example of the partial ontology rendered tree by providing a specified uri of water (http://purl.obolibrary.org/obo/CHEBI_15377)

![Screenshot 2024-04-23 at 13 25 11](https://github.com/EBISPOT/ols4/assets/13108541/78a09a8b-8b25-49ff-8b2e-7469c811eaee)


## Features

- Easy to integrate with any web application.
- Full and partial ontology tree rendering.
- Lightweight and fast.