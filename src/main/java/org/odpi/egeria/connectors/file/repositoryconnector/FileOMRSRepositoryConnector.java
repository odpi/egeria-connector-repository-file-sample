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
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;

import java.io.UnsupportedEncodingException;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.Instant;
import java.io.IOException;
import java.util.*;

/**
 * This sample repository connector is implmented to show a polling pattern against a file system.
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
 * The reference copies will containt the outer metadata collection id as their home.
 *
 * Queries to this connector will come to the outer collection and be delegated down to the embedded collection , the results of the queries are amended to contain
 * the outer metadata collection id.
 *
 *
 */
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
//    private String embeddedRepositoryName =null;
//    private String embeddedMetadataCollectionId = null;

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

        if (!folder.exists()) {
            raiseConnectorCheckedException(FileOMRSErrorCode.FOLDER_DOES_NOT_EXIST, methodName, null, folder.getName());
        } else if (!folder.isDirectory()) {
            raiseConnectorCheckedException(FileOMRSErrorCode.NOT_A_FOLDER, methodName, null, folder.getName());
        } else {
            File[] dataFiles = folder.listFiles();

            try {
                FileOMRSMetadataCollection fileMetadataCollection = (FileOMRSMetadataCollection) getMetadataCollection();
                fileMetadataCollection.getEmbeddedMetadataCollection();
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.UNABLE_TO_INITIALISE_CACHE, methodName, e);
            }

            for (File dataFile : dataFiles) {
                // add data file entity
                String baseCanonicalName = null;
                String baseName = null;
                try {
                    baseCanonicalName = dataFile.getCanonicalPath();
                    baseName = dataFile.getName();
                } catch (IOException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.IOEXCEPTION_ACCESSING_FILE, methodName, e);
                }
                int lastDotIndex = baseName.lastIndexOf(".");
                String fileType = null;
                if (baseName.length() > 2 && lastDotIndex != -1 && lastDotIndex < baseName.length() - 1) {
                    // if we can see a file type then add then add as an attribute
                    fileType = baseName.substring(lastDotIndex + 1);
                }
                String dataFileGuid = null;
                try {
                    dataFileGuid = Base64.getUrlEncoder().encodeToString(baseCanonicalName.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error UnsupportedEncodingException " + e.getMessage());
                }
                EntityDetail dataFileEntity = getEntityDetailSkeleton(methodName,
                                                                      "DataFile",
                                                                      dataFileGuid,
                                                                      baseName,
                                                                      baseCanonicalName,
                                                                      fileType);
                issueSaveEntityReferenceCopy(dataFileEntity);

                String name = baseName + "-connection";
                String canonicalName = baseCanonicalName + "-connection";
                String connectionGuid = null;
                try {
                    connectionGuid = Base64.getUrlEncoder().encodeToString(canonicalName.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error UnsupportedEncodingException " + e.getMessage());
                }
                EntityDetail connectionEntity = getEntityDetailSkeleton(methodName,
                                                                        "Connection",
                                                                        connectionGuid,
                                                                        name,
                                                                        canonicalName,
                                                                        null);
                System.err.println("connection reference copy is " + connectionEntity.toString() );
                // TODO add more connection attributes?
                issueSaveEntityReferenceCopy(connectionEntity);

                name = baseName + "-connectortype";
                canonicalName = baseCanonicalName + "-connectortype";
                String connectorTypeGuid = null;
                try {
                    connectorTypeGuid = Base64.getUrlEncoder().encodeToString(canonicalName.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error UnsupportedEncodingException " + e.getMessage());
                }
                EntityDetail connectionTypeEntity = getEntityDetailSkeleton(methodName,
                                                                            "ConnectorType",
                                                                            connectorTypeGuid,
                                                                            name,
                                                                            canonicalName,
                                                                            null);
                // TODO add more connection attributes?
                issueSaveEntityReferenceCopy(connectionTypeEntity);


                name = baseName + "-endpoint";
                canonicalName = baseCanonicalName + "-endpoint";
                String endpointGuid = null;
                try {
                    endpointGuid = Base64.getUrlEncoder().encodeToString(canonicalName.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error UnsupportedEncodingException " + e.getMessage());
                }
                EntityDetail endpointEntity = getEntityDetailSkeleton(methodName,
                                                                      "Endpoint",
                                                                      endpointGuid,
                                                                      name,
                                                                      canonicalName,
                                                                      null);
                InstanceProperties instanceProperties = endpointEntity.getProperties();
                repositoryHelper.addStringPropertyToInstance(methodName,
                                                             null,
                                                             "protocol",
                                                             "file",
                                                             methodName);
                repositoryHelper.addStringPropertyToInstance(methodName,
                                                             null,
                                                             "networkAddress",
                                                             baseCanonicalName,
                                                             methodName);
                endpointEntity.setProperties(instanceProperties);


                // TODO add more endpoint attributes
                issueSaveEntityReferenceCopy(endpointEntity);

                // create relationships

//                        "ConnectionEndpoint",
//                        "ConnectionConnectorType",
//                        "ConnectionToAsset"
                Relationship connectionToAsset = null;
                try {
                    connectionToAsset = repositoryHelper.getSkeletonRelationship(methodName,
                                                                                 metadataCollectionId,
                                                                                 InstanceProvenanceType.LOCAL_COHORT,
                                                                                 "userId",
                                                                                 "ConnectionToAsset");
                } catch (TypeErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
                }

                String connectionToAssetCanonicalName = dataFileGuid + "::ConnectionToAsset::" + connectionGuid;
                String connectionToAssetGuid = null;
                try {
                    connectionToAssetGuid = Base64.getUrlEncoder().encodeToString(connectionToAssetCanonicalName.getBytes("UTF-8"));
                } catch (UnsupportedEncodingException e) {
                    System.err.println("Error UnsupportedEncodingException " + e.getMessage());
                }

                connectionToAsset.setGUID(connectionToAssetGuid);
                //end 1
                EntityProxy entityProxy1 = getEntityProxySkeleton(connectionGuid, "Connection");
                connectionToAsset.setEntityOneProxy(entityProxy1);

                //end 2
                EntityProxy entityProxy2 = getEntityProxySkeleton(dataFileGuid, "DataFile");
                connectionToAsset.setEntityTwoProxy(entityProxy2);
                System.err.println("connectionToAsset reference copy is " + connectionToAsset.toString() );
                try {
                    metadataCollection.saveRelationshipReferenceCopy(
                            "userId",
                            connectionToAsset);
                } catch (InvalidParameterException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e);
                } catch (RepositoryErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e);
                } catch (TypeErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
                } catch (EntityNotKnownException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e);
                }   catch (PropertyErrorException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e);
                }   catch (HomeRelationshipException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.HOME_RELATIONSHIP_ERROR_EXCEPTION, methodName, e);
                } catch (RelationshipConflictException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.RELATIONSHIP_CONFLICT_ERROR_EXCEPTION, methodName, e);
                } catch (InvalidRelationshipException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_RELATIONSHIP_ERROR_EXCEPTION, methodName, e);
                } catch (FunctionNotSupportedException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e);
                } catch (UserNotAuthorizedException e) {
                    raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e);
                }

                /*
                    TODO add
                        final String attribute1Name            = "createTime";
                        final String attribute1Description     = "Creation time of the data store.";
                        final String attribute2Name            = "modifiedTime";
                        final String attribute2Description     = "Last known modification time.";
                 */

            }
        }
    }
    private EntityProxy getEntityProxySkeleton(String guid, String typeName) throws ConnectorCheckedException
    {
        String methodName = "getEntityProxySkeleton";
        EntityProxy proxy = new EntityProxy();
        TypeDefSummary typeDefSummary = repositoryHelper.getTypeDefByName(methodName, typeName);
        InstanceType type = null;
        try {
            type = repositoryHelper.getNewInstanceType(methodName, typeDefSummary);
        } catch (TypeErrorException e) {
            raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
        }
        proxy.setType(type);
        proxy.setGUID(guid);
        proxy.setMetadataCollectionId(metadataCollectionId);
        proxy.setMetadataCollectionName(metadataCollectionName);
        return proxy;
    }

    private EntityDetail  getEntityDetailSkeleton(String originalMethodName,
                                                  String typeName,
                                                  String guid,
                                                  String name,
                                                  String canonicalName,
                                                  String fileType) throws ConnectorCheckedException {
        String methodName = "getEntityDetail";

        InstanceProperties       initialProperties= repositoryHelper.addStringPropertyToInstance(methodName,
                                                                                                 null,
                                                                                                 "name",
                                                                                                 name,
                                                                                                 methodName);
        initialProperties = repositoryHelper.addStringPropertyToInstance(methodName,
                                                                         initialProperties,
                                                                         "qualifiedName",
                                                                         canonicalName,  // TODO prefix
                                                                         methodName);

        if (fileType != null) {
            repositoryHelper.addStringPropertyToInstance(methodName,
                                                         initialProperties,
                                                         "fileType",
                                                         fileType,
                                                         methodName);
        }
        EntityDetail entityToAdd =new EntityDetail();
        entityToAdd.setProperties(initialProperties);

        // set the provenance as local cohort
        entityToAdd.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
        entityToAdd.setMetadataCollectionId(metadataCollectionId);
        entityToAdd.setMetadataCollectionName(metadataCollectionName);

        TypeDef typeDef = repositoryHelper.getTypeDefByName(repositoryName, typeName);

        try {
            if (typeDef == null) {
                System.err.println("Typename cannot be found " + typeName);
                throw new TypeErrorException(FileOMRSErrorCode.TYPEDEF_NAME_NOT_KNOWN.getMessageDefinition(typeName, originalMethodName, methodName),
                                             this.getClass().getName(),
                                             originalMethodName);
            }
            InstanceType instanceType = repositoryHelper.getNewInstanceType(repositoryName, typeDef);
            entityToAdd.setType(instanceType);
        } catch (TypeErrorException e) {
            raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
        }

        entityToAdd.setGUID(guid);
        entityToAdd.setStatus(InstanceStatus.ACTIVE);
        Instant instant = Instant.now();
        long timeStampMillis = instant.toEpochMilli();
        entityToAdd.setVersion(timeStampMillis);

        return entityToAdd;

    }
    private void issueSaveEntityReferenceCopy(EntityDetail entityToAdd) throws ConnectorCheckedException {
        String methodName = "issueSaveEntityReferenceCopy";
        System.err.println("issueSaveEntityReferenceCopy " +entityToAdd);

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
            //TODO get from embedded connecion config if there.
//            embeddedRepositoryName= metadataCollectionName+"-embedded";
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
