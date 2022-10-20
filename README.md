<!-- SPDX-License-Identifier: CC-BY-4.0 -->
<!-- Copyright Contributors to the Egeria project. -->

# Repository proxy (adapter) polling example using files

See the main [Egeria Documentation](https://egeria-project.org) for more details.

This is a [repository proxy](https://egeria-project.org/concepts/repository-proxy/?h=repository) implementation
that provides an example of bringing in the metadata associated with files in a folder, into Egeria.

It showcases a pattern, whereby the [OMRS Repository connector](https://egeria-project.org/concepts/repository-connector/?h=repository+connector)  
has an embedded Repository connector that caches content. In the past, repository proxies implemented each of the find requires.
In this pattern, the OMRS requests are delegated down to the embedded OMRS repository simplifying development. 

If a member of the [cohort](https://egeria-project.org/services/omrs/cohort/?h=cohort) does not issue federated queries,
then it cannot get existing content from a 3rd party repository. This sample gives an example on how an event mapper can be written
to poll the 3rd party technology and send a batched event for each asset. The batched event contains 

* the asset (representing the file)
* connection,
* connector type 
* endpoint
* relationships

The repository proxy is made of :
* A caching repository connector, that delegates its OMRS calls to the embedded collection.
* An event mapper that polls for the metadata associated with files in a folder
* Audit and error log definitions used by the 2 connectors.

The pattern is:
![Caching Repository proxy components](images/File%20sample.drawio.png)

It shows how the event mapper polling loop:
- Gets the file info from the file system
- Adds the appropriate reference entities and relationships to the repository connector
- Finds the entities and relationships per asset
- Sends a batched event per asset
- Waits for the length of time specified in the refreshTimeInterval configuration parameter.
- repeats


A subset of the [open types](https://egeria-project.org/types/) are required for this sample:

Entity types
* DataFile
* Connection
* ConnectorType
* Endpoint

Relationship types
* ConnectionEndpoint
* ConnectionConnectorType
* ConnectionToAsset


## Getting started
- You should be familiar with how to [setup Egeria](https://egeria-project.org/education/egeria-dojo/running-egeria/setup-environment/).
- You need to decide which embedded repository you will use and ensure that the appropriate jar files are picked up by the OMAG server platform. 
- follow the below instruction to configure and run.

### Configuration

The repository proxy is configured use [usual](https://egeria-project.org/guides/admin/servers/configuring-a-repository-proxy/?h=proxy)

#### Postman
An example of how to configure the repository proxy is provided in a [postman collection](../postman/). 
##### Variables
Similar to other postman collections that Egeria ships; this postman collection requires the following variables to be set, prior to running. 

| Variable name       | Description                                                          | example value            |
|---------------------|----------------------------------------------------------------------|--------------------------|
| server              | Server name                                                          | cocofile                 |
| user                | UserId to issue the calls                                            | garyGeeke                |
| baseURL             | Base URL for rest calls                                              | https://localhost:9443   |
| refreshTimeInterval | Poll interval in seconds                                             | 1                        |
| qualifiedNamePrefix | Prefix to add to each qualified name for each entity that is created | test1                    |
| folderpathtomonitor | Folder path to monitor                                               | /testfolder/1            |



##### Running
 Note that the Postman requests are named starting with a number. You should run the posts in numerical order, when you have completed 3,
you should decide which embedded connector you want to run, choose either:
* 4a. for [in memory repository connector](https://egeria-project.org/connectors/repository/in-memory/overview/?h=memory) 
* 4b. for [XTDB](https://egeria-project.org/connectors/repository/xtdb/?h=xtdb). Please ensure that the XTDB jar file is available to the server

### Verifying it is working
* The audit log content shows progress. 
* You can see the content of the connector using the [Repository Explorer from the 
Eco-system UI](https://egeria-project.org/guides/ecosystem-ui/rex-user-guide/?h=repository+explorer). Be aware that you 
will need to [configure the Rex view service](https://egeria-project.org/guides/admin/servers/configuring-a-view-server/?h=view+server+configuration#integration-view-services)
to include the repository proxy server, with an entry in the configuration similar to this (where cocofile is the Server name): 

`{
"class"              : "ResourceEndpointConfig",
"resourceCategory"   : "Server",
"serverInstanceName" : "Caching Repository proxy file sample",
"serverName"         : "cocofile",
"platformName"       : "Platform1",
"description"        : "Caching Repository proxy file sample"
}`

Amend the `serverName` to match your server (the 'server' in the postman collection).

### Restrictions and considerations
1) The normal way that a cohort member would get information about the repository metadata 
behind a repository proxy would be to:
* issue gueries to the cohort.
* get add, update and delete information via OMRS events
If these federated queries are beeng issued, then there is no need to event mapper to poll.
2) polling as per this pattern means that all content is cached into the embedded repository. This 
may not be desirable if there is a large amount of metadata in the 3rd party technology.
3) The batched events contain all the information associated with an asset. If there was a listener listening to the 
3rd party technology (the file system here) then the listener could pick up incremental changes and 
the cache would be kept up to date.
4) The batched events could flood the cohort(s) if the interval is too short and there is a lot of metadata.
5) An integration connector or standard repository proxy pattern could be preferable for many setups.
6) If there is a requirement to write to the 3rd party technology then the OMRS repository connector 
would need to be re-implemented as it would need to include code to write to the 3rd party technology. 


### Reference materials for developers.

* [https://github.com/odpi/egeria/blob/main/open-metadata-implementation/repository-services/README.md](https://github.com/odpi/egeria/blob/main/open-metadata-implementation/repository-services/README.md) 
and it's sub-pages are great resources for developers. 
* [Egeria Webinars](https://wiki.lfaidata.foundation/display/EG/Egeria+Webinar+program) particularly the one on repository connectors.
