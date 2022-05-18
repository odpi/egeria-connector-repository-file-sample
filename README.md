<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the Egeria project. -->

# Repository proxy (adapter) polling example using files

See https://egeria-project.org for the main Egeria Documentation.

This is a new repository that we are currently setting up.

In development - not for use until more complete

This [repository proxy](https://egeria-project.org/concepts/repository-proxy/?h=repository) provides an example implementation for interacting with a files in a folder, which uses  
the open metadata standards of Egeria.

The repository proxy provides:
* a read-only repository connector, that issue calls to the file system.
* an event mapper that calls the repository connector, polling for the metadata associated with a file
* Audit and error log definitions used by the 2 connectors.

Furthermore, only a subset of the open types are implemented, namely:
Entity types
* DataFile
* Connection
* ConnectorType
  Endpoint
  And relationship types
* ConnectionEndpoint
* ConnectionConnectorType
* ConnectionToAsset


## Getting started
TODO
### Configuration
TODO
### T
