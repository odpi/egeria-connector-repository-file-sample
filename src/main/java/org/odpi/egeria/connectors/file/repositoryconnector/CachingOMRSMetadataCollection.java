/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.repositoryconnector;

import org.odpi.egeria.connectors.file.auditlog.FileOMRSErrorCode;

import org.odpi.openmetadata.frameworks.connectors.Connector;
import org.odpi.openmetadata.frameworks.connectors.ConnectorBroker;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectionCheckedException;
import org.odpi.openmetadata.frameworks.connectors.ffdc.ConnectorCheckedException;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.Connection;
import org.odpi.openmetadata.frameworks.connectors.properties.beans.ConnectorType;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSDynamicTypeMetadataCollectionBase;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSMetadataCollection;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryConnector;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchClassifications;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.search.SearchProperties;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.OMRSFixedTypeMetadataCollectionBase;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.MatchCriteria;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.SequencingOrder;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.properties.instances.*;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryHelper;
import org.odpi.openmetadata.repositoryservices.connectors.stores.metadatacollectionstore.repositoryconnector.OMRSRepositoryValidator;
import org.odpi.openmetadata.frameworks.auditlog.AuditLog;
import org.odpi.openmetadata.repositoryservices.ffdc.exception.*;


import java.util.*;

public class CachingOMRSMetadataCollection extends OMRSDynamicTypeMetadataCollectionBase {

    OMRSRepositoryConnector embeddedConnector = null;
    OMRSMetadataCollection embeddedMetadataCollection = null;

    /**
     * @param parentConnector      connector that this metadata collection supports.
     *                             The connector has the information to call the metadata repository.
     * @param repositoryName       name of this repository.
     * @param repositoryHelper     helper that provides methods to repository connectors and repository event mappers
     *                             to build valid type definitions (TypeDefs), entities and relationships.
     * @param repositoryValidator  validator class for checking open metadata repository objects and parameters
     * @param metadataCollectionId unique identifier for the repository
     *
     * @throws RepositoryErrorException RepositoryErrorException error occured in the repository
     */

    public CachingOMRSMetadataCollection(CachingOMRSRepositoryProxyConnector parentConnector,
                                         String repositoryName,
                                         OMRSRepositoryHelper repositoryHelper,
                                         OMRSRepositoryValidator repositoryValidator,
                                         String metadataCollectionId,
                                         OMRSRepositoryConnector embeddedConnector
                                        ) throws  RepositoryErrorException {
        super(parentConnector,
              repositoryName,
              repositoryHelper,
              repositoryValidator,
              metadataCollectionId);

        this.metadataCollectionId = metadataCollectionId;

        embeddedConnector.setMetadataCollectionName(metadataCollectionName+"-embedded");
        embeddedConnector.setRepositoryHelper(repositoryHelper);
        embeddedConnector.setRepositoryValidator(repositoryValidator);
        // this needs to be done last as it creates the embedded metadata collection if there is not one
        embeddedConnector.setMetadataCollectionId(metadataCollectionId+"-embedded");
        this.embeddedConnector = embeddedConnector;
        this.embeddedMetadataCollection = this.embeddedConnector.getMetadataCollection();
    }

//    private OMRSRepositoryConnector initializeEmbeddedRepositoryConnector(OMRSRepositoryHelper  repositoryHelper,
//                                                                          OMRSRepositoryValidator repositoryValidator,
//                                                                          Connection connection)
//                                                                            throws ConnectionCheckedException ,ConnectorCheckedException {
//
////        Connection connection = new Connection();
////        ConnectorType connectorType = new ConnectorType();
////        connection.setConnectorType(connectorType);
////        connectorType.setConnectorProviderClassName("org.odpi.openmetadata.adapters.repositoryservices.inmemory.repositoryconnector.InMemoryOMRSRepositoryConnectorProvider");   // TODO get from config.
//
//        ConnectorBroker connectorBroker = new ConnectorBroker();
//        OMRSRepositoryConnector embeddedConnector = (OMRSRepositoryConnector) connectorBroker.getConnector(connection);
//        embeddedConnector.setRepositoryHelper(repositoryHelper);
//        embeddedConnector.setRepositoryValidator(repositoryValidator);
//        // this collection id is never stored in an entity as we ony populate the repo with reference copies
//        embeddedConnector.setMetadataCollectionId(metadataCollectionId+"-embedded");
//        embeddedConnector.setMetadataCollectionName(metadataCollectionName+"-embedded");
//        embeddedConnector.start();
//
//        return embeddedConnector;
//    }
    /**
     * Throw a RepositoryErrorException using the provided parameters.
     * @param errorCode the error code for the exception
     * @param methodName the method throwing the exception
     * @param cause the underlying cause of the exception (if any, otherwise null)
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
//    private EntityDetail setOuterMetadataCollectionInformation(EntityDetail entity) {
//        if (entity == null) {
//            return null;
//        }
//        entity.setMetadataCollectionId(metadataCollectionId);
//        entity.setMetadataCollectionName(metadataCollectionName);
//        return entity;
//    }
//    private EntitySummary setOuterMetadataCollectionInformation(EntitySummary entity) {
//        if (entity == null) {
//            return null;
//        }
//        entity.setMetadataCollectionId(metadataCollectionId);
//        entity.setMetadataCollectionName(metadataCollectionName);
//        return entity;
//    }
//    private List<EntityDetail> setOuterMetadataCollectionInformation(List<EntityDetail> entities) {
//        if (entities == null) {
//            return null;
//        }
//        List<EntityDetail> list = new ArrayList<>();
//        for (EntityDetail entityDetail: entities) {
//            EntityDetail newEntityDetail = setOuterMetadataCollectionInformation( entityDetail);
//            list.add(newEntityDetail);
//        }
//        return list;
//    }


    // ** Delegate all the collection methods we care about to the embedded connector collection.
    // Currently only read orientated calls in groups 3 and 4 are implmented. See OMRSMetadataCollection class for the meaning of the groups.

    @Override
    public EntityDetail isEntityKnown(String userId, String guid) throws InvalidParameterException, RepositoryErrorException, UserNotAuthorizedException {
//        EntityDetail entityDetail = embeddedMetadataCollection.isEntityKnown(userId, guid);
//        return setOuterMetadataCollectionInformation(entityDetail);
        return embeddedMetadataCollection.isEntityKnown(userId, guid);
    }

    @Override
    public EntitySummary getEntitySummary(String userId, String guid) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, UserNotAuthorizedException {
//        EntitySummary entitySummary = embeddedMetadataCollection.getEntitySummary(userId, guid);
//        return setOuterMetadataCollectionInformation(entitySummary);
        return embeddedMetadataCollection.getEntitySummary(userId, guid);
    }

    @Override
    public EntityDetail getEntityDetail(String userId, String guid) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, EntityProxyOnlyException, UserNotAuthorizedException {
//        EntityDetail entityDetail =  embeddedMetadataCollection.getEntityDetail(userId, guid);
//        return setOuterMetadataCollectionInformation(entityDetail);
        return embeddedMetadataCollection.getEntityDetail(userId, guid);
    }

    @Override
    public EntityDetail getEntityDetail(String userId, String guid, Date asOfTime) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, EntityProxyOnlyException, FunctionNotSupportedException, UserNotAuthorizedException {
//        EntityDetail entityDetail = embeddedMetadataCollection.getEntityDetail(userId, guid, asOfTime);
//        return setOuterMetadataCollectionInformation(entityDetail);
        return embeddedMetadataCollection.getEntityDetail(userId, guid, asOfTime);
    }

//    @Override
//    public List<EntityDetail> getEntityDetailHistory(String userId, String guid, Date fromTime, Date toTime, int startFromElement, int pageSize, HistorySequencingOrder sequencingOrder) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, EntityProxyOnlyException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return embeddedMetadataCollection.getEntityDetailHistory(userId, guid, fromTime, toTime, startFromElement, pageSize, sequencingOrder);
//    }

    @Override
    public List<Relationship> getRelationshipsForEntity(String userId, String entityGUID, String relationshipTypeGUID, int fromRelationshipElement, List<InstanceStatus> limitResultsByStatus, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, EntityNotKnownException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getRelationshipsForEntity(userId, entityGUID, relationshipTypeGUID, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize);
    }

    @Override
    public List<EntityDetail> findEntities(String userId, String entityTypeGUID, List<String> entitySubtypeGUIDs, SearchProperties matchProperties, int fromEntityElement, List<InstanceStatus> limitResultsByStatus, SearchClassifications matchClassifications, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {

//        List<EntityDetail> entityDetailList = embeddedMetadataCollection.findEntities(userId,
//                                                           entityTypeGUID,
//                                                           entitySubtypeGUIDs,
//                                                           matchProperties,
//                                                           fromEntityElement,
//                                                           limitResultsByStatus,
//                                                           matchClassifications,
//                                                           asOfTime,
//                                                           sequencingProperty,
//                                                           sequencingOrder,
//                                                           pageSize);
//        return setOuterMetadataCollectionInformation(entityDetailList);
        return embeddedMetadataCollection.findEntities(userId,
                                                           entityTypeGUID,
                                                           entitySubtypeGUIDs,
                                                           matchProperties,
                                                           fromEntityElement,
                                                           limitResultsByStatus,
                                                           matchClassifications,
                                                           asOfTime,
                                                           sequencingProperty,
                                                           sequencingOrder,
                                                           pageSize);
    }

    @Override
    public List<EntityDetail> findEntitiesByProperty(String userId, String entityTypeGUID, InstanceProperties matchProperties, MatchCriteria matchCriteria, int fromEntityElement, List<InstanceStatus> limitResultsByStatus, List<String> limitResultsByClassification, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findEntitiesByProperty(userId, entityTypeGUID, matchProperties, matchCriteria, fromEntityElement, limitResultsByStatus,  limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder,  pageSize);
    }

    @Override
    public List<EntityDetail> findEntitiesByClassification(String userId, String entityTypeGUID, String classificationName, InstanceProperties matchClassificationProperties, MatchCriteria matchCriteria, int fromEntityElement, List<InstanceStatus> limitResultsByStatus, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, ClassificationErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findEntitiesByClassification(userId, entityTypeGUID, classificationName,  matchClassificationProperties, matchCriteria, fromEntityElement, limitResultsByStatus, asOfTime, sequencingProperty,  sequencingOrder, pageSize);
    }

    @Override
    public List<EntityDetail> findEntitiesByPropertyValue(String userId, String entityTypeGUID, String searchCriteria, int fromEntityElement, List<InstanceStatus> limitResultsByStatus, List<String> limitResultsByClassification, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findEntitiesByPropertyValue(userId, entityTypeGUID, searchCriteria, fromEntityElement, limitResultsByStatus, limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder, pageSize);
        }

    @Override
    public Relationship isRelationshipKnown(String userId, String guid) throws InvalidParameterException, RepositoryErrorException, UserNotAuthorizedException {
        return embeddedMetadataCollection.isRelationshipKnown(userId, guid);
    }

    @Override
    public Relationship getRelationship(String userId, String guid) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getRelationship(userId, guid);
    }

    @Override
    public Relationship getRelationship(String userId, String guid, Date asOfTime) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getRelationship(userId, guid, asOfTime);
    }

//    @Override
//    public List<Relationship> getRelationshipHistory(String userId, String guid, Date fromTime, Date toTime, int startFromElement, int pageSize, HistorySequencingOrder sequencingOrder) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return embeddedMetadataCollection.getRelationshipHistory( userId,  guid,  fromTime,  toTime, startFromElement, pageSize, sequencingOrder);
//    }

    @Override
    public List<Relationship> findRelationships(String userId, String relationshipTypeGUID, List<String> relationshipSubtypeGUIDs, SearchProperties matchProperties, int fromRelationshipElement, List<InstanceStatus> limitResultsByStatus, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findRelationships(userId, relationshipTypeGUID, relationshipSubtypeGUIDs,  matchProperties, fromRelationshipElement, limitResultsByStatus,  asOfTime, sequencingProperty, sequencingOrder, pageSize) ;
    }

    @Override
    public List<Relationship> findRelationshipsByProperty(String userId, String relationshipTypeGUID, InstanceProperties matchProperties, MatchCriteria matchCriteria, int fromRelationshipElement, List<InstanceStatus> limitResultsByStatus, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findRelationshipsByProperty(userId, relationshipTypeGUID, matchProperties, matchCriteria, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty, sequencingOrder, pageSize) ;

        }

    @Override
    public List<Relationship> findRelationshipsByPropertyValue(String userId, String relationshipTypeGUID, String searchCriteria, int fromRelationshipElement, List<InstanceStatus> limitResultsByStatus, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.findRelationshipsByPropertyValue(userId,  relationshipTypeGUID, searchCriteria, fromRelationshipElement, limitResultsByStatus, asOfTime, sequencingProperty,  sequencingOrder, pageSize);
    }

    @Override
    public InstanceGraph getLinkingEntities(String userId, String startEntityGUID, String endEntityGUID, List<InstanceStatus> limitResultsByStatus, Date asOfTime) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, PropertyErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getLinkingEntities(userId, startEntityGUID, endEntityGUID, limitResultsByStatus, asOfTime);
        }

    @Override
    public InstanceGraph getEntityNeighborhood(String userId, String entityGUID, List<String> entityTypeGUIDs, List<String> relationshipTypeGUIDs, List<InstanceStatus> limitResultsByStatus, List<String> limitResultsByClassification, Date asOfTime, int level) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, EntityNotKnownException, PropertyErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getEntityNeighborhood(userId,  entityGUID,  entityTypeGUIDs, relationshipTypeGUIDs, limitResultsByStatus, limitResultsByClassification, asOfTime,  level);
    }

    @Override
    public List<EntityDetail> getRelatedEntities(String userId, String startEntityGUID, List<String> entityTypeGUIDs, int fromEntityElement, List<InstanceStatus> limitResultsByStatus, List<String> limitResultsByClassification, Date asOfTime, String sequencingProperty, SequencingOrder sequencingOrder, int pageSize) throws InvalidParameterException, TypeErrorException, RepositoryErrorException, EntityNotKnownException, PropertyErrorException, PagingErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
        return embeddedMetadataCollection.getRelatedEntities(userId, startEntityGUID, entityTypeGUIDs, fromEntityElement, limitResultsByStatus, limitResultsByClassification, asOfTime, sequencingProperty, sequencingOrder, pageSize);
    }

//    @Override
//    public EntityDetail addEntity(String userId, String entityTypeGUID, InstanceProperties initialProperties, List<Classification> initialClassifications, InstanceStatus initialStatus) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, ClassificationErrorException, StatusNotSupportedException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }

//    @Override
//    public void addEntityProxy(String userId, EntityProxy entityProxy) throws InvalidParameterException, RepositoryErrorException, FunctionNotSupportedException, UserNotAuthorizedException {
//
//    }
//
//    @Override
//    public EntityDetail updateEntityStatus(String userId, String entityGUID, InstanceStatus newStatus) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, StatusNotSupportedException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail updateEntityProperties(String userId, String entityGUID, InstanceProperties properties) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, PropertyErrorException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail undoEntityUpdate(String userId, String entityGUID) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail deleteEntity(String userId, String typeDefGUID, String typeDefName, String obsoleteEntityGUID) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public void purgeEntity(String userId, String typeDefGUID, String typeDefName, String deletedEntityGUID) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, EntityNotDeletedException, UserNotAuthorizedException, FunctionNotSupportedException {
//
//    }
//
//    @Override
//    public EntityDetail restoreEntity(String userId, String deletedEntityGUID) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, EntityNotDeletedException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail classifyEntity(String userId, String entityGUID, String classificationName, InstanceProperties classificationProperties) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, ClassificationErrorException, PropertyErrorException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail declassifyEntity(String userId, String entityGUID, String classificationName) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, ClassificationErrorException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail updateEntityClassification(String userId, String entityGUID, String classificationName, InstanceProperties properties) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, ClassificationErrorException, PropertyErrorException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public Relationship addRelationship(String userId, String relationshipTypeGUID, InstanceProperties initialProperties, String entityOneGUID, String entityTwoGUID, InstanceStatus initialStatus) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, EntityNotKnownException, StatusNotSupportedException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public Relationship updateRelationshipStatus(String userId, String relationshipGUID, InstanceStatus newStatus) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, StatusNotSupportedException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public Relationship updateRelationshipProperties(String userId, String relationshipGUID, InstanceProperties properties) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, PropertyErrorException, UserNotAuthorizedException, FunctionNotSupportedException {
//        return null;
//    }
//
//    @Override
//    public Relationship undoRelationshipUpdate(String userId, String relationshipGUID) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public Relationship deleteRelationship(String userId, String typeDefGUID, String typeDefName, String obsoleteRelationshipGUID) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public void purgeRelationship(String userId, String typeDefGUID, String typeDefName, String deletedRelationshipGUID) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, RelationshipNotDeletedException, UserNotAuthorizedException, FunctionNotSupportedException {
//
//    }
//
//    @Override
//    public Relationship restoreRelationship(String userId, String deletedRelationshipGUID) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, RelationshipNotDeletedException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail reIdentifyEntity(String userId, String typeDefGUID, String typeDefName, String entityGUID, String newEntityGUID) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public EntityDetail reTypeEntity(String userId, String entityGUID, TypeDefSummary currentTypeDefSummary, TypeDefSummary newTypeDefSummary) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, ClassificationErrorException, EntityNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public Relationship reIdentifyRelationship(String userId, String typeDefGUID, String typeDefName, String relationshipGUID, String newRelationshipGUID) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
//    @Override
//    public Relationship reTypeRelationship(String userId, String relationshipGUID, TypeDefSummary currentTypeDefSummary, TypeDefSummary newTypeDefSummary) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, RelationshipNotKnownException, FunctionNotSupportedException, UserNotAuthorizedException {
//        return null;
//    }
//
    @Override
    public void saveEntityReferenceCopy(String userId, EntityDetail entity) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, PropertyErrorException, HomeEntityException, EntityConflictException, InvalidEntityException, FunctionNotSupportedException, UserNotAuthorizedException {
          embeddedMetadataCollection.saveEntityReferenceCopy(userId,entity);
    }
//
//    @Override
//    public void purgeEntityReferenceCopy(String userId, String entityGUID, String typeDefGUID, String typeDefName, String homeMetadataCollectionId) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, HomeEntityException, FunctionNotSupportedException, UserNotAuthorizedException {
//
//    }
//
//    @Override
//    public void refreshEntityReferenceCopy(String userId, String entityGUID, String typeDefGUID, String typeDefName, String homeMetadataCollectionId) throws InvalidParameterException, RepositoryErrorException, EntityNotKnownException, HomeEntityException, FunctionNotSupportedException, UserNotAuthorizedException {
//
//    }
//
    @Override
    public void saveRelationshipReferenceCopy(String userId, Relationship relationship) throws InvalidParameterException, RepositoryErrorException, TypeErrorException, EntityNotKnownException, PropertyErrorException, HomeRelationshipException, RelationshipConflictException, InvalidRelationshipException, FunctionNotSupportedException, UserNotAuthorizedException {
        embeddedMetadataCollection.saveRelationshipReferenceCopy(userId, relationship);
    }
//
//    @Override
//    public void purgeRelationshipReferenceCopy(String userId, String relationshipGUID, String typeDefGUID, String typeDefName, String homeMetadataCollectionId) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, HomeRelationshipException, FunctionNotSupportedException, UserNotAuthorizedException {
//
//    }
//
//    @Override
//    public void refreshRelationshipReferenceCopy(String userId, String relationshipGUID, String typeDefGUID, String typeDefName, String homeMetadataCollectionId) throws InvalidParameterException, RepositoryErrorException, RelationshipNotKnownException, HomeRelationshipException, FunctionNotSupportedException, UserNotAuthorizedException {
//
//    }

}
