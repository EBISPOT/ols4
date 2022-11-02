These are the OLS3 widgets:

* https://github.com/EBISPOT/OLS-autocomplete
* https://github.com/EBISPOT/OLS-treeview
* https://github.com/EBISPOT/OLS-graphview

Embedded in a single page. There are no modifications to the code of the widgets, and the code used to embed them is taken directly from their examples.

The purpose of `legacy-plugins-test` is to make sure they work with OLS4 (via the OLS3 API compatibility layer).

To try it yourself, you need to host this directory on a local HTTP server due to CORS restrictions. You could copy it to one you already have running, or run `serve` in this directory and navigate to `http://localhost:5000/test.html`. (Make sure you have the OLS4 backend running on port 8080.)
