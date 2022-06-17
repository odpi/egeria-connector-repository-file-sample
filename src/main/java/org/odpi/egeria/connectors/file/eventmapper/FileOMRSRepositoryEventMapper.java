/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.eventmapper;


import org.odpi.egeria.connectors.file.auditlog.FileOMRSAuditCode;
import org.odpi.egeria.connectors.file.auditlog.FileOMRSErrorCode;
import org.odpi.egeria.connectors.file.repositoryconnector.CachingOMRSRepositoryProxyConnector;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.EndpointProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDef;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.typedefs.TypeDefSummary;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryeventmapper.OMRSRepositoryEventMapperBase;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * FileOMRSRepositoryEventMapper supports the event mapper function for Apache File
 * when used as an open metadata repository.
 */
public class FileOMRSRepositoryEventMapper extends OMRSRepositoryEventMapperBase
//        implements OpenMetadataTopicListener
{

    private static final String DATA_FILE = "DataFile";
    private static final String CONNECTION = "Connection";
    private static final String CONNECTOR_TYPE = "ConnectorType";
    private static final String ENDPOINT = "Endpoint";
    private static final String CONNECTION_ENDPOINT = "ConnectionEndpoint";
    private static final String CONNECTION_CONNECTOR_TYPE = "ConnectionConnectorType";
    private static final String CONNECTION_TO_ASSET = "ConnectionToAsset";
    //    private static final Logger log = LoggerFactory.getLogger(FileOMRSRepositoryEventMapper.class);
    private final AtomicBoolean running = new AtomicBoolean(false);

    private Map<String, String> typeNameToGuidMap = null;


    private String userId = null;
    /**
     * Default polling refresh interval in milliseconds.
     */
    private int refreshInterval = 5000;
    private String qualifiedNamePrefix = "";
    protected String metadataCollectionId = null;
    protected String metadataCollectionName = null;
    protected OMRSMetadataCollection metadataCollection = null;

    private String repositoryName = null;

    private String folderLocation;

    final List<String> supportedTypeNames = Arrays.asList(new String[]{
            // entity types
            "DataStore", // super type of Datafile
            "Asset", // super type of Datastore
            "Referenceable", // super type of the others
            "OpenMetadataRoot", // super type of referenceable
            DATA_FILE,
            CONNECTION,
            CONNECTOR_TYPE,
            ENDPOINT,
            // relationship types
            CONNECTION_ENDPOINT,
            CONNECTION_CONNECTOR_TYPE,
            CONNECTION_TO_ASSET
            // classification types
            // none at this time
    });

    private PollingThread pollingThread;

    /**
     * Default constructor
     */
    public FileOMRSRepositoryEventMapper() {
        super();
//        this.sourceName = "FileOMRSRepositoryEventMapper";
    }

    /**
     * get folder location - synchronised as there is multithreaded access
     *
     * @return folder location as a string
     */
    synchronized public String getFolderLocation() {
        return folderLocation;
    }

    /**
     * set folder location - synchronised as there is multithreaded access
     *
     * @param folderLocation location of the folder
     */
    synchronized public void setFolderLocation(String folderLocation) {
        this.folderLocation = folderLocation;
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
            metadataCollection = this.repositoryConnector.getMetadataCollection();
            if (this.userId == null) {
                // default
                this.userId = "OMAGServer";
            }
            metadataCollectionId = metadataCollection.getMetadataCollectionId(this.userId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    synchronized public void start() throws ConnectorCheckedException {

        super.start();

        final String methodName = "start";
        repositoryName = this.repositoryConnector.getRepositoryName();
        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_STARTING.getMessageDefinition());

        if (!(repositoryConnector instanceof CachingOMRSRepositoryProxyConnector)) {
            raiseConnectorCheckedException(FileOMRSErrorCode.EVENT_MAPPER_IMPROPERLY_INITIALIZED, methodName, null, repositoryConnector.getServerName());
        }

        this.repositoryHelper = this.repositoryConnector.getRepositoryHelper();
        if (metadataCollection == null) {
            try {
                connectToFolder(methodName);
            } catch (RepositoryErrorException cause) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FAILED_TO_START_CONNECTOR, methodName, null);
            }
        }
        Map<String, Object> configurationProperties = connectionProperties.getConfigurationProperties();
         this.userId = connectionProperties.getUserId();
         if (this.userId == null) {
             // default
             this.userId = "OMAGServer";
         }
        if (configurationProperties != null) {
            Integer configuredRefreshInterval = (Integer) configurationProperties.get(FileOMRSRepositoryEventMapperProvider.REFRESH_TIME_INTERVAL);
            if (configuredRefreshInterval != null) {
                refreshInterval = configuredRefreshInterval * 1000;
            }
            String configuredQualifiedNamePrefix = (String) configurationProperties.get(FileOMRSRepositoryEventMapperProvider.QUALIFIED_NAME_PREFIX);
            if (configuredQualifiedNamePrefix != null) {
                qualifiedNamePrefix = configuredQualifiedNamePrefix;
            }
        }

        this.pollingThread = new PollingThread();
        pollingThread.start();
    }


    /**
     * Class to poll for file content
     */
    private class PollingThread implements Runnable {
        Thread worker = null;
        void start() {
            Thread worker = new Thread(this);
            worker.start();
        }

        void stop() {
            if (!running.compareAndSet(true, false)) {
                auditLog.logMessage("stop", FileOMRSAuditCode.POLLING_THREAD_INFO_ALREADY_STOPPED.getMessageDefinition());
            }
        }

        private List<EntityDetail> getEntitiesByType(String typeName) throws ConnectorCheckedException {
            String methodName = "getEntitiesByType(String typeName)";
            List<EntityDetail> entityDetails = null;
            try {
                entityDetails = getEntitiesByTypeGuid(typeName);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (PropertyErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (PagingErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PAGING_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (FunctionNotSupportedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, repositoryConnector.getServerName());
            }
            return entityDetails;
        }

        private List<Relationship> getRelationshipsForEntityHelper(
                String entityGUID,
                String relationshipTypeGUID) throws ConnectorCheckedException {
            String methodName = "getRelationshipsForEntityHelper";
            List<Relationship> relationships = null;
            try {
                relationships = metadataCollection.getRelationshipsForEntity(userId, entityGUID, relationshipTypeGUID, 0, null, null, null, null, 0);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (PropertyErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (PagingErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PAGING_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (FunctionNotSupportedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (EntityNotKnownException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, repositoryConnector.getServerName(), methodName, entityGUID);
            }
            return relationships;
        }

        private EntityDetail getEntityDetail(String guid) throws ConnectorCheckedException {
            String methodName = "getEntityDetail";
            EntityDetail entityDetail = null;
            try {
                entityDetail = metadataCollection.getEntityDetail(userId, guid);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (UserNotAuthorizedException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.USER_NOT_AUTHORIZED_EXCEPTION, methodName, e, repositoryConnector.getServerName(), methodName);
            } catch (EntityNotKnownException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e, repositoryConnector.getServerName(), methodName, guid);
            } catch (EntityProxyOnlyException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_PROXY_ONLY, methodName, e, repositoryConnector.getServerName(), methodName, guid);
            }
            return entityDetail;


        }

        void sendBatchEvent() throws ConnectorCheckedException {
            List<EntityDetail> dataFiles = getEntitiesByType(DATA_FILE);

            for (EntityDetail dataFile : dataFiles) {

                List<Relationship> relationshipList = new ArrayList<>();
                List<EntityDetail> entityList = new ArrayList<>();
                entityList.add(dataFile);
                String assetGUID = dataFile.getGUID();
                List<String> connectionGuids = populateEvent(CONNECTION_TO_ASSET, assetGUID, entityList, relationshipList);
                if (connectionGuids != null && connectionGuids.size() > 0) {
                    for (String connectionGUID : connectionGuids) {
                        populateEvent(CONNECTION_CONNECTOR_TYPE, connectionGUID, entityList, relationshipList);
                        populateEvent(CONNECTION_ENDPOINT, connectionGUID, entityList, relationshipList);
                    }
                }

                InstanceGraph instances = new InstanceGraph(entityList, relationshipList);

                // send the event
                repositoryEventProcessor.processInstanceBatchEvent("FileOMRSRepositoryEventMapper",
                                                                   repositoryConnector.getMetadataCollectionId(),
                                                                   repositoryConnector.getServerName(),
                                                                   repositoryConnector.getServerType(),
                                                                   repositoryConnector.getOrganizationName(),
                                                                   instances);
            }

        }

        private List<String> populateEvent(String relationshipTypeName, String startEntityGUID, List<EntityDetail> entityList, List<Relationship> relationshipList) throws ConnectorCheckedException {
            String methodName = "populateEvent";

            List<String> otherEndGuids = new ArrayList<>();
            TypeDefSummary typeDefSummary = repositoryHelper.getTypeDefByName(methodName, relationshipTypeName);
            String relationshipTypeGUID = typeDefSummary.getGUID();
            List<Relationship> connectorConnectorTypeRelationships = getRelationshipsForEntityHelper(startEntityGUID, relationshipTypeGUID);
            for (Relationship relationship : connectorConnectorTypeRelationships) {
                EntityProxy proxy = repositoryHelper.getOtherEnd(methodName,
                                                                 startEntityGUID,
                                                                 relationship);
                String guid = proxy.getGUID();
                EntityDetail otherEndEntity = getEntityDetail(guid);
                entityList.add(otherEndEntity);
                relationshipList.add(relationship);
                otherEndGuids.add(otherEndEntity.getGUID());
            }
            return otherEndGuids;

        }

        /**
         * Read File.
         */
        @Override
        public void run() {

            final String methodName = "run";
            if (running.compareAndSet(false, true)) {

                while (running.get()) {
                    try {
                        getRequiredTypes();
                        // call the repository connector to refresh its contents.
                        refreshRepository();
                        // send the batch event per asset
                        sendBatchEvent();
                        //  wait the polling interval.
                        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_PRE_WAIT.getMessageDefinition());
                        try {
                            Thread.sleep(refreshInterval);
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_POST_WAIT.getMessageDefinition());
                        } catch (InterruptedException e) {
                            // should not happen as there is only one thread
                            // if it happens then continue in the while
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_INTERRUPTED_EXCEPTION.getMessageDefinition());
                        }


                    } catch (ConnectorCheckedException e) {
                        if (e.getCause() == null) {
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_GOT_AN_EXCEPTION.getMessageDefinition(e.getMessage()));
                        } else {
                            auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_GOT_AN_EXCEPTION_WITH_CAUSE.getMessageDefinition(e.getMessage(), e.getCause().getMessage()));
                        }
                    } catch (Exception e) {
                        // catch everything else
                        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_POLL_LOOP_GOT_AN_EXCEPTION_WITH_CAUSE.getMessageDefinition(e.getMessage(), e.getCause().getMessage()));
                    } finally {
                        // stop the thread if we came out of the loop.
                        this.stop();
                    }
                }
            }
        }

        private void getRequiredTypes() throws ConnectorCheckedException {
            String methodName = "getRequiredTypes";
            final int supportedCount = supportedTypeNames.size();

            int typesAvailableCount = 0;
            int retryCount = 0;
            while ((typesAvailableCount != supportedCount) && retryCount < 10) {
                auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP.getMessageDefinition(typesAvailableCount + "", supportedCount + "", retryCount + ""));
                // only come out the while loop when we can get all of the supported types in one iteration.
                typesAvailableCount = 0;
                if (typeNameToGuidMap == null) {
                    typeNameToGuidMap = new HashMap<>();
                }
                // populate the type name to guid map
                for (String typeName : supportedTypeNames) {

                    TypeDef typeDef = repositoryHelper.getTypeDefByName("FileOMRSRepositoryEventMapper",
                                                                        typeName);
                    if (typeDef != null) {
                        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_FOUND_TYPE.getMessageDefinition(typeName));
                        typeNameToGuidMap.put(typeName, typeDef.getGUID());
                        typesAvailableCount++;
                    }
                }
                if (typesAvailableCount < supportedCount) {
                    //delay for 1 second and then retry

                    try {
                        Thread.sleep(1000);  // TODO Should this be in configuration?
                        retryCount++;
                    } catch (InterruptedException e) {
                        // should not happen as there is only one thread
                        // if it does happen it would result in a lower duration for the sleep
                        //
                        // Increment the retry count, in case this happens everytime
                        retryCount++;
                        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRING_TYPES_LOOP_INTERRUPTED_EXCEPTION.getMessageDefinition());
                    }
                } else if (typesAvailableCount == supportedCount) {
                    // log to say we have all the types we need
                    auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_ACQUIRED_ALL_TYPES.getMessageDefinition());

                }

                if (retryCount == 20) { // TODO  Should this be in configuration?
                    raiseConnectorCheckedException(FileOMRSErrorCode.EVENT_MAPPER_CANNOT_GET_TYPES, methodName, null );
                }
            }
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
                    Map<String, String> attributeMap = getDataFileProperties(baseName);

                    EntityDetail dataFileEntity = getEntityDetailSkeleton(methodName,
                                                                          DATA_FILE,
                                                                          baseName,
                                                                          baseCanonicalName,
                                                                          attributeMap);
                    issueSaveEntityReferenceCopy(dataFileEntity);

                    String name = baseName + "-connection";
                    String canonicalName = baseCanonicalName + "-connection";

                    EntityDetail connectionEntity = getEntityDetailSkeleton(methodName,
                                                                            CONNECTION,
                                                                            name,
                                                                            canonicalName);

                    issueSaveEntityReferenceCopy(connectionEntity);

                    name = baseName + "-" + CONNECTOR_TYPE;
                    canonicalName = baseCanonicalName + "-" + CONNECTOR_TYPE;
                    EntityDetail connectionTypeEntity = getEntityDetailSkeleton(methodName,
                                                                                CONNECTOR_TYPE,
                                                                                name,
                                                                                canonicalName);
                    issueSaveEntityReferenceCopy(connectionTypeEntity);


                    name = baseName + "-" + ENDPOINT;
                    canonicalName = baseCanonicalName + "-" + ENDPOINT;

                    EntityDetail endpointEntity = getEntityDetailSkeleton(methodName,
                                                                          ENDPOINT,
                                                                          name,
                                                                          canonicalName);
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

                    issueSaveEntityReferenceCopy(endpointEntity);

                    // create relationships

                    // entity guids used to create proxies
                    String connectionGuid = connectionEntity.getGUID();
                    String dataFileGuid = dataFileEntity.getGUID();
                    String connectionTypeGuid = connectionTypeEntity.getGUID();
                    String endPointGuid = endpointEntity.getGUID();

                    // create the 3 relationships
                    createReferenceRelationship(CONNECTION_TO_ASSET,
                                                connectionGuid,
                                                CONNECTION,
                                                dataFileGuid,
                                                DATA_FILE);

                    createReferenceRelationship(CONNECTION_CONNECTOR_TYPE,
                                                connectionGuid,
                                                CONNECTION,
                                                connectionTypeGuid,
                                                CONNECTOR_TYPE);

                    createReferenceRelationship(CONNECTION_ENDPOINT,
                                                connectionGuid,
                                                CONNECTION,
                                                endPointGuid,
                                                ENDPOINT
                                               );
                }
            }
        }

        private Map<String, String> getDataFileProperties(String name) {
            String methodName = "getDataFileProperties";

            Map<String, String> attributeMap = new HashMap<>();
            String fileType = null;
            int lastDotIndex = name.lastIndexOf(".");
            if (name.length() > 2 && lastDotIndex != -1 && lastDotIndex < name.length() - 1) {
                // if we can see a file type then add as an attribute
                fileType = name.substring(lastDotIndex + 1);
            }

            if (fileType != null) {
                attributeMap.put("fileType", fileType);
            }


            return attributeMap;
        }

        private void issueSaveEntityReferenceCopy(EntityDetail entityToAdd) throws ConnectorCheckedException {
            String methodName = "issueSaveEntityReferenceCopy";

            try {
                metadataCollection.saveEntityReferenceCopy(
                        userId,
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

        private void createReferenceRelationship(String relationshipTypeName, String end1GUID, String end1TypeName, String end2GUID, String end2TypeName) throws ConnectorCheckedException {
            String methodName = "createRelationship";


            Relationship relationship = null;
            try {
                relationship = repositoryHelper.getSkeletonRelationship(methodName,
                                                                        metadataCollectionId,
                                                                        InstanceProvenanceType.LOCAL_COHORT,
                                                                        userId,
                                                                        relationshipTypeName);
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
            }

            String connectionToAssetCanonicalName = end1GUID + "::" + relationshipTypeName + "::" + end2GUID;
            String relationshipGUID = null;
            try {
                relationshipGUID = Base64.getUrlEncoder().encodeToString(connectionToAssetCanonicalName.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENCODING_EXCEPTION, methodName, e, "connectionToAssetCanonicalName", connectionToAssetCanonicalName );
            }

            relationship.setGUID(relationshipGUID);
            //end 1
            EntityProxy entityProxy1 = getEntityProxySkeleton(end1GUID, end1TypeName);
            relationship.setEntityOneProxy(entityProxy1);

            //end 2
            EntityProxy entityProxy2 = getEntityProxySkeleton(end2GUID, end2TypeName);
            relationship.setEntityTwoProxy(entityProxy2);
            try {
                metadataCollection.saveRelationshipReferenceCopy(
                        userId,
                        relationship);
            } catch (InvalidParameterException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.INVALID_PARAMETER_EXCEPTION, methodName, e);
            } catch (RepositoryErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.REPOSITORY_ERROR_EXCEPTION, methodName, e);
            } catch (TypeErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, e);
            } catch (EntityNotKnownException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENTITY_NOT_KNOWN, methodName, e);
            } catch (PropertyErrorException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.PROPERTY_ERROR_EXCEPTION, methodName, e);
            } catch (HomeRelationshipException e) {
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

        }

        private EntityProxy getEntityProxySkeleton(String guid, String typeName) throws ConnectorCheckedException {
            String methodName = "getEntityProxySkeleton";
            EntityProxy proxy = new EntityProxy();
            TypeDefSummary typeDefSummary = repositoryHelper.getTypeDefByName("getEntityProxySkeleton", typeName);
            InstanceType type = null;
            try {
                if (typeDefSummary == null) {
                    throw new TypeErrorException(FileOMRSErrorCode.TYPEDEF_NAME_NOT_KNOWN.getMessageDefinition(repositoryName, methodName, typeName),
                                                 this.getClass().getName(),
                                                 methodName);
                }
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

        private EntityDetail getEntityDetailSkeleton(String originalMethodName,
                                                     String typeName,
                                                     String name,
                                                     String canonicalName
                                                    ) throws ConnectorCheckedException {
            return getEntityDetailSkeleton(originalMethodName,
                                           typeName,
                                           name,
                                           canonicalName,
                                           null);

        }

        private EntityDetail getEntityDetailSkeleton(String originalMethodName,
                                                     String typeName,
                                                     String name,
                                                     String canonicalName,
                                                     Map<String, String> attributeMap
                                                    ) throws ConnectorCheckedException {
            String methodName = "getEntityDetail";

            String guid = null;
            try {
                guid = Base64.getUrlEncoder().encodeToString(canonicalName.getBytes("UTF-8"));
            } catch (UnsupportedEncodingException e) {
                raiseConnectorCheckedException(FileOMRSErrorCode.ENCODING_EXCEPTION, methodName, e, "canonicalName",canonicalName );
            }


            InstanceProperties initialProperties = repositoryHelper.addStringPropertyToInstance(methodName,
                                                                                                null,
                                                                                                "name",
                                                                                                name,
                                                                                                methodName);
            initialProperties = repositoryHelper.addStringPropertyToInstance(methodName,
                                                                             initialProperties,
                                                                             "qualifiedName",
                                                                             qualifiedNamePrefix + canonicalName,
                                                                             methodName);
            if (attributeMap != null && !attributeMap.keySet().isEmpty()) {
                addTypeSpecificProperties(initialProperties, attributeMap);
            }

            EntityDetail entityToAdd = new EntityDetail();
            entityToAdd.setProperties(initialProperties);

            // set the provenance as local cohort
            entityToAdd.setInstanceProvenanceType(InstanceProvenanceType.LOCAL_COHORT);
            entityToAdd.setMetadataCollectionId(metadataCollectionId);
//            entityToAdd.setMetadataCollectionName(metadataCollectionName);

            TypeDef typeDef = repositoryHelper.getTypeDefByName(methodName, typeName);

            try {
                if (typeDef == null) {
                    throw new TypeErrorException(FileOMRSErrorCode.TYPEDEF_NAME_NOT_KNOWN.getMessageDefinition(metadataCollectionName, methodName, typeName),
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
            // for this sample we only support add and delete so there is only one version
            // if the name changes then this is an add and a delete
            entityToAdd.setVersion(1);

            return entityToAdd;

        }

        void addTypeSpecificProperties(InstanceProperties initialProperties, Map<String, String> attributeMap) {
            String methodName = "addTypeSpecificProperties";
            for (String attributeName : attributeMap.keySet()) {
                repositoryHelper.addStringPropertyToInstance(methodName,
                                                             initialProperties,
                                                             attributeName,
                                                             attributeMap.get(attributeName),
                                                             methodName);
            }
        }


        private List<EntityDetail> getEntitiesByTypeGuid(String typeName) throws
                                                                          InvalidParameterException,
                                                                          RepositoryErrorException,
                                                                          TypeErrorException,
                                                                          PropertyErrorException,
                                                                          PagingErrorException,
                                                                          FunctionNotSupportedException,
                                                                          UserNotAuthorizedException,
                                                                          ConnectorCheckedException {
            String methodName = "getEntitiesByTypeGuid";
            String typeGUID = typeNameToGuidMap.get(typeName);
            if (typeGUID == null) {
                raiseConnectorCheckedException(FileOMRSErrorCode.TYPE_ERROR_EXCEPTION, methodName, null, repositoryConnector.getServerName());
                return null;
            } else {
                return metadataCollection.findEntities(
                        userId,
                        typeGUID,
                        null,
                        null,
                        0,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0);
            }
        }
    }


    /**
     * {@inheritDoc}
     */
    @Override
    synchronized public void disconnect() throws ConnectorCheckedException {
        super.disconnect();
        final String methodName = "disconnect";
        pollingThread.stop();
        auditLog.logMessage(methodName, FileOMRSAuditCode.EVENT_MAPPER_SHUTDOWN.getMessageDefinition(repositoryConnector.getServerName()));
    }

    /**
     * Throws a ConnectorCheckedException based on the provided parameters.
     *
     * @param errorCode  the error code for the exception
     * @param methodName the method name throwing the exception
     * @param cause      the underlying cause of the exception (if any, otherwise null)
     * @param params     any additional parameters for formatting the error message
     * @throws ConnectorCheckedException always
     */
    private void raiseConnectorCheckedException(FileOMRSErrorCode errorCode, String methodName, Exception cause, String... params) throws ConnectorCheckedException {
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
     *
     * @param errorCode  the error code for the exception
     * @param methodName the name of the method throwing the exception
     * @param cause      the underlying cause of the exception (or null if none)
     * @param params     any parameters for formatting the error message
     * @throws RepositoryErrorException always
     */
    private void raiseRepositoryErrorException(FileOMRSErrorCode errorCode, String methodName, Throwable cause, String... params) throws RepositoryErrorException {
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
