/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.repositoryconnector;


import org.odpi.egeria.connectors.file.auditlog.FileOMRSAuditCode;
import org.odpi.egeria.connectors.file.auditlog.FileOMRSErrorCode;
import org.odpi.openmetadata.frameworks.connectors.Connector;
import org.odpi.openmetadata.frameworks.connectors.VirtualConnectorExtension;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectionCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.ConnectionProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.EmbeddedConnectionProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.VirtualConnectionProperties;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.EmbeddedConnection;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.VirtualConnection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.io.UnsupportedEncodingException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This sample repository connector is implemented to show a polling pattern against a file system.
 * The polling call is initiated from FileOMRSRepositoryEventMapper using a refreshRepository call.
 *
 * Being a repository connector - it exposes the OMRS API.
 * As ususal, the OMRS API is implemented in a metadata collection.
 * This class implments a second embedded metadata collection, this is an Egeria native repository that is defined in the configuration.
 *
 * The outer metadata collection is the only metadata collection id that the collection rest APIs recognise.
 * The embedded metadata collection has a different metadata collection id. This will be the outer metadata collection id appeneded with -embedded, or it's
 * value will come from the embedded collection id in the config (if there is one).
 *
 * The refreshRepository call will populate the content of the 3rd party technilogy (in this example Files) in the the embedded repository as reference copies.
 * The reference copies will contain the outer metadata collection id as their home.
 *
 * Queries to this connector will come to the outer collection and be delegated down to the embedded collection , the results of the queries are amended to contain
 * the outer metadata collection id.
 *
 *
 */
public class CachingOMRSRepositoryProxyConnector extends OMRSRepositoryConnector
        implements VirtualConnectorExtension {
    private OMRSRepositoryConnector embeddedConnector = null;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public CachingOMRSRepositoryProxyConnector() {

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";
        auditLog.logMessage(methodName, FileOMRSAuditCode.REPOSITORY_SERVICE_STARTING.getMessageDefinition());
        synchronized (this) {
            if (metadataCollection == null) {
                // If the metadata collection has not yet been created, attempt to create it now
                try {
                    initializeMetadataCollection();
                } catch (RepositoryErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e);
                }
            }
            try {
                this.embeddedConnector.start();
            } catch (ConnectorCheckedException e) {
                throw new RuntimeException(e);
            }
        }


        auditLog.logMessage(methodName, FileOMRSAuditCode.REPOSITORY_SERVICE_STARTED.getMessageDefinition(getServerName()));

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void disconnect() {
        final String methodName = "disconnect";
        auditLog.logMessage(methodName, FileOMRSAuditCode.REPOSITORY_SERVICE_SHUTDOWN.getMessageDefinition(getServerName()));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public OMRSMetadataCollection getMetadataCollection() throws RepositoryErrorException {
        synchronized (this) {
            if (metadataCollection == null) {
                // If the metadata collection has not yet been created, attempt to create it now
                initializeMetadataCollection();
            }
        }
        return super.getMetadataCollection();
    }

    private void initializeMetadataCollection() throws RepositoryErrorException {

            metadataCollection = new CachingOMRSMetadataCollection(this,
                                                                   serverName,
                                                                   repositoryHelper,
                                                                   repositoryValidator,
                                                                   metadataCollectionId,
                                                                   embeddedConnector);



    }

    @Override
    public void initializeEmbeddedConnectors(List<Connector> embeddedConnectors) {
       if (embeddedConnectors == null || embeddedConnectors.isEmpty()) {
           // TODO error
           System.err.println("no embedded connectors supplied  ");
       } else if (embeddedConnectors.size() > 1) {
           // TODO error
           System.err.println("More than one embedded connectors supplied  ");
       } else {
           Connector connector = embeddedConnectors.get(0);
           if (connector instanceof  OMRSRepositoryConnector) {
               this.embeddedConnector = (OMRSRepositoryConnector) connector;

//               try {
//                   ((CachingOMRSMetadataCollection)metadataCollection).initializeEmbeddedRepositoryConnector(embeddedConnector);
//               } catch (ConnectionCheckedException e) {
//                   throw new RuntimeException(e);
//               } catch (ConnectorCheckedException e) {
//                   throw new RuntimeException(e);
//               } catch (RepositoryErrorException e) {
//                   throw new RuntimeException(e);
//               }
           }
       }
    }

    /**
     * Throws a ConnectorCheckedException based on the provided parameters.
     *
     * @param errorCode the error code for the exception
     * @param methodName the method name throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
     * @param params any additional parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(FileOMRSErrorCode errorCode, String methodName, Exception cause, String ...params) throws ConnectorCheckedException {
        if (cause == null) {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                                                this.getClass().getName(),
                                                methodName);
        } else {
            throw new ConnectorCheckedException(errorCode.getMessageDefinition(params),
                                                this.getClass().getName(),
                                                methodName,
                                                cause);
        }
    }
}
