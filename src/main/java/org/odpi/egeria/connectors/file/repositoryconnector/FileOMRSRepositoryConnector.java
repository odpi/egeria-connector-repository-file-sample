/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.repositoryconnector;


import org.odpi.egeria.connectors.file.auditlog.FileOMRSAuditCode;
import org.odpi.egeria.connectors.file.auditlog.FileOMRSErrorCode;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.EntityDetail;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceStatus;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.InstanceProvenanceType;

//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class FileOMRSRepositoryConnector extends OMRSRepositoryConnector {

//    private static final Logger log = LoggerFactory.getLogger(org.odpi.egeria.connectors.file.repositoryconnector.FileOMRSRepositoryConnector.class);

    private final List<String> supportedAttributeTypeNames = Arrays.asList(new String[]{
            "object",
            "boolean",
            "byte",
            "char",
            "short",
            "int",
            "long",
            "float",
            "biginteger",
            "bigdecimal",
            "string",
            "date",
            "map<string,boolean>",
            "map<string,int>",
            "map<string,long>",
            "map<string,object>",
            "array<string>",
            "array<int>"
    });





    private final List<String> supportedTypeNames = Arrays.asList(new String[]{
                                                                                // entity types
                                                                                "DataStore", // super type of Datafile
                                                                                "Asset", // super type of Datastore
                                                                                "Referenceable", // super type of the others
                                                                                "OpenMetadataRoot", // super type of referenceable
                                                                                "DataFile",
                                                                                "Connection",
                                                                                "ConnectorType",
                                                                                "Endpoint",
                                                                                // relationship types
                                                                                "ConnectionEndpoint",
                                                                                "ConnectionConnectorType",
                                                                                "ConnectionToAsset"
                                                                                // classification types
                                                                                // none at this time
                                                                                });

    private String folderLocation;

    /**
     * Default constructor used by the OCF Connector Provider.
     */
    public FileOMRSRepositoryConnector() {

    }

    /**
     * get folder location - synchronised as there is multithreaded access
     * @return folder location as a string
     */
    synchronized public String getFolderLocation() {
        return folderLocation;
    }

    /**
     * set folder location - synchronised as there is multithreaded access
     * @param folderLocation location of the folder
     */
    synchronized public void setFolderLocation(String folderLocation) {
        this.folderLocation = folderLocation;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized void start() throws ConnectorCheckedException {

        super.start();
        final String methodName = "start";

        auditLog.logMessage(methodName, FileOMRSAuditCode.REPOSITORY_SERVICE_STARTING.getMessageDefinition());

        if (metadataCollection == null) {
            try {
                connectToFolder(methodName);
            } catch (RepositoryErrorException cause) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FAILED_TO_START_CONNECTOR, methodName, null);
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
        final String methodName = "getMetadataCollection";
        if (metadataCollection == null) {
            // If the metadata collection has not yet been created, attempt to create it now
            connectToFolder(methodName);
        }

        return super.getMetadataCollection();
    }

    public void refreshRepository() throws ConnectorCheckedException {
        String methodName = "refreshRepository";
        File folder = new File(getFolderLocation());

        if(!folder.exists()){
            raiseConnectorCheckedException(FileOMRSErrorCode.FOLDER_DOES_NOT_EXIST, methodName, null, folder.getName());
        } else if(!folder.isDirectory()) {
            raiseConnectorCheckedException(FileOMRSErrorCode.NOT_A_FOLDER, methodName, null, folder.getName());
        } else {
            File[] dataFiles = folder.listFiles();

            try {
                FileOMRSMetadataCollection fileMetadataCollection = (FileOMRSMetadataCollection)getMetadataCollection();
                fileMetadataCollection.getEmbeddedMetadataCollection();
            } catch( RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.UNABLE_TO_INITIALISE_CACHE, methodName, e);
            }
            InstanceProperties initialProperties=null;
            for (File dataFile:dataFiles) {
                // add data file entity
                String canonicalName= null;
                String name=null;
                    try {
                        canonicalName = dataFile.getCanonicalPath();
                        name = dataFile.getName();
                    } catch (IOException e) {
                        raiseConnectorCheckedException(FileOMRSErrorCode.IOEXCEPTION_ACCESSING_FILE, methodName, e);
                    }

                    initialProperties= repositoryHelper.addStringPropertyToInstance("refreshRepository",
                                                                                     null,
                                                                                     "name",
                                                                                     name,
                                                                                     "refreshRepository");
                    initialProperties = repositoryHelper.addStringPropertyToInstance("refreshRepository",
                                                                                     initialProperties,
                                                                                     "qualifiedName",
                                                                                     canonicalName,  // TODO prefix
                                                                                     "refreshRepository");
//                    initialProperties = repositoryHelper.addStringPropertyToInstance("refreshRepository",
//                                                                                     initialProperties,
//                                                                                     "guid",
//                                                                                     canonicalName,    //dodo generate a unique quid.
//                                                                                     "refreshRepository");
                    int lastDotIndex = name.lastIndexOf(".");
                    if (name.length() >2 && lastDotIndex != -1 && lastDotIndex < name.length()-1) {
                        // if we can see a file type then add then add as an attribute
                        String fileType = name.substring(lastDotIndex+1 );
                        repositoryHelper.addStringPropertyToInstance("refreshRepository",
                                                                     initialProperties,
                                                                     "fileType",
                                                                     fileType,
                                                                     "refreshRepository");
                    }


                /*
                    TODO add
                        final String attribute1Name            = "createTime";
                        final String attribute1Description     = "Creation time of the data store.";
                        final String attribute2Name            = "modifiedTime";
                        final String attribute2Description     = "Last known modification time.";
                 */
                EntityDetail entityToAdd= new EntityDetail();
                entityToAdd.setGUID(canonicalName);
                entityToAdd.setProperties(initialProperties);
                entityToAdd.setStatus(InstanceStatus.ACTIVE);
                Instant instant = Instant.now();
                long timeStampMillis = instant.toEpochMilli();
                entityToAdd.setVersion(timeStampMillis);
                // TODO set repository Name from config if we have one in the embedded connection
                String repositoryName= metadataCollectionName+"-embedded";
                TypeDef typeDef = repositoryHelper.getTypeDefByName(repositoryName, "DataFile");
                try {
                    InstanceType instanceType = repositoryHelper.getNewInstanceType(repositoryName, typeDef);
                    entityToAdd.setType(instanceType);
                } catch (TypeErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
                }

                // set the provenance as local cohort
                InstanceProvenanceType instanceProvenanceType = InstanceProvenanceType.LOCAL_COHORT;
                entityToAdd.setInstanceProvenanceType(instanceProvenanceType);
                entityToAdd.setMetadataCollectionId(metadataCollectionId);
                entityToAdd.setMetadataCollectionName(metadataCollectionName);

                try {
                     metadataCollection.saveEntityReferenceCopy(
                            "userId",
                             entityToAdd);
                } catch (InvalidParameterException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e);
                } catch (RepositoryErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e);
                } catch (TypeErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
                } catch (PropertyErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e);
//                } catch (ClassificationErrorException e) {
//                    raiseConnectorCheckedException(FileOMRSErrorCode.CLASSIFICATION_ERROR_EXCEPTION, methodName, e);
//                } catch (StatusNotSupportedException e) {
//                    raiseConnectorCheckedException(FileOMRSErrorCode.STATUS_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e);
                } catch (HomeEntityException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.HOME_ENTITY_ERROR_EXCEPTION, methodName, e);
                } catch (EntityConflictException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_CONFLICT_ERROR_EXCEPTION, methodName, e);
                } catch (InvalidEntityException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_ENTITY_ERROR_EXCEPTION, methodName, e);
                } catch (FunctionNotSupportedException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e);
                } catch (UserNotAuthorizedException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e);
                }
            }
        }
    }


    /**
     * Attempt to connect to the folder.
     *
     * @param methodName the method attempting to connect
     * @throws RepositoryErrorException if there is any issue connecting
     */
    synchronized private void connectToFolder(String methodName) throws RepositoryErrorException {
        EndpointProperties endpointProperties = connectionProperties.getEndpoint();
        if (endpointProperties == null) {
            raiseRepositoryErrorException(FileOMRSErrorCode.FOLDER_NOT_SUPPLIED_IN_CONFIG, methodName, null, "null");
        } else {
            setFolderLocation(endpointProperties.getAddress());
            metadataCollection = new FileOMRSMetadataCollection(this,
                                                                serverName,
                                                                repositoryHelper,
                                                                repositoryValidator,
                                                                metadataCollectionId,
                                                                supportedAttributeTypeNames,
                                                                supportedTypeNames,
                                                                auditLog

            );
        }
    }






    /**
     * Throws a ConnectorCheckedException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause the underlying cause of the exception (if any, null otherwise)
     * @param params any parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(FileOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws ConnectorCheckedException {
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

    /**
     * Throws a RepositoryErrorException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause the underlying cause of the exception (or null if none)
     * @param params any parameters for formatting the error message
     * @throws RepositoryErrorException always
     */
    private void raiseRepositoryErrorException(FileOMRSErrorCode errorCode, String methodName, Throwable cause, String ...params) throws RepositoryErrorException {
        if (cause == null) {
            throw new RepositoryErrorException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName);
        } else {
            throw new RepositoryErrorException(errorCode.getMessageDefinition(params),
                    this.getClass().getName(),
                    methodName,
                    cause);
        }
    }

}
