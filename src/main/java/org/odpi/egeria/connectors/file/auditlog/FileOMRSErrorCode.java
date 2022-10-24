/* SPDX-License-Identifier: Apache-2.0 */
/* Copyright Contributors to the ODPi Egeria project. */
package org.odpi.egeria.connectors.file.auditlog;

import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageDefinition;
import org.odpi.openmetadata.frameworks.auditlog.messagesets.ExceptionMessageSet;

public enum FileOMRSErrorCode implements ExceptionMessageSet {

    FOLDER_NOT_SUPPLIED_IN_CONFIG(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-001 ",
            "The endpoint was not supplied in the connector configuration \"{0}\"",
            "Connector unable to continue",
            "Supply a valid folder location in the configuration endpoint."),
    FAILED_TO_START_CONNECTOR(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-002 ",
            "The same file connector failed to start",
            "Connector is unable to be used",
            "Review your configuration to ensure it is valid."),
    FOLDER_DOES_NOT_EXIST(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-003 ",
            "The endpoint {0} in the configuration does not exist on the file system. ",
            "Connector is unable to be used",
            "Review the Endpoint in the configuration to ensure it exists and points to a folder that exists."),
    NOT_A_FOLDER(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-004 ",
            "The endpoint {0} in the configuration exists on the file system, but is not a folder. ",
            "Connector is unable to be used",
            "Review the Endpoint in the configuration to ensure it exists and points to a folder that exists."),
    IOEXCEPTION_ACCESSING_FILE(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-005 ",
            "An IOException occurred when accessing the folder  ",
            "Connector is unable to be used",
            "Review the Endpoint in the configuration to ensure it is valid and readable. Check the logs and debug."),
    INVALID_PARAMETER_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-006 ",
            "Invalid parameter exception",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),

    REPOSITORY_ERROR_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-007 ",
            "Repository error excpption",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),
    TYPE_ERROR_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-008 ",
            "Type error exception",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),
    PROPERTY_ERROR_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-009 ",
            "Property error exception",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),
    PAGING_ERROR_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-010 ",
            "Paging error exception",
            "Connector is unable to be used",
            "Review the configuration around paging. Check the logs and debug."),
    FUNCTION_NOT_SUPPORTED_ERROR_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-01 ",
            "Function not supported error exception",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),
    USER_NOT_AUTHORIZED_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-012 ",
            "user not authorized error exception",
            "Connector is unable to be used",
            "Review the configuration. Check the logs and debug."),
    EVENT_MAPPER_IMPROPERLY_INITIALIZED(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-013 ",
            "The event mapper has been improperly initialized for repository {1}",
            "The system will be unable to process any events",
            "Check the system logs and diagnose or report the problem."),
    ENCODING_EXCEPTION(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-014 ",
            "The event mapper failed to encode '{0}' with value '{1}' to create a guid",
            "The system will shutdown the server",
            "Debug the cause of the encoding error."),
    EVENT_MAPPER_CANNOT_GET_TYPES(400, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-400-015 ",
            "The event mapper failed to obtain the types, so cannot proceed ",
            "The system will shutdown the server",
            "ensure you are using a repository that supports the required types."),
    ENTITY_NOT_KNOWN(404, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-404-001 ",
            "On Server {0} for request {1}, the entity identified with guid {0} is not known to the open metadata repository {2}",
            "The system is unable to retrieve the properties for the requested entity because the supplied guid is not recognized.",
            "The guid is supplied by the caller to the server.  It may have a logic problem that has corrupted the guid, or the entity has been deleted since the guid was retrieved."),

    TYPEDEF_NAME_NOT_KNOWN(404, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-404-002",
            "On Server {0} for request {1}, the TypeDef unique name {2} passed is not known to this repository connector",
            "The system is unable to retrieve the properties for the requested TypeDef because the supplied identifiers are not recognized.",
            "The identifier is supplied by the caller.  It may have a logic problem that has corrupted the identifier, or the TypeDef has been deleted since the identifier was retrieved."),
    ENTITY_PROXY_ONLY(404, "FILE-OMRS-FILE-EVENT-MAPPER-ERROR-404-003",
            "On server {0} for request {1}, a specific entity instance for guid {2}, but this but only a proxy version of the entity is in the metadata collection",
            "The system is unable to return the entity as it is only a proxy.",
            "The guid identifier is supplied by the caller. Amend the caller to supply a guid assoicated with an Entity rather than a proxy."),

    ;

    final private ExceptionMessageDefinition messageDefinition;

    /**
     * The constructor for FileOMRSErrorCode expects to be passed one of the enumeration rows defined in
     * FileOMRSErrorCode above.   For example:
     * <p>
     * FileOMRSErrorCode   errorCode = FileOMRSErrorCode.NULL_INSTANCE;
     * <p>
     * This will expand out to the 5 parameters shown below.
     *
     * @param newHTTPErrorCode  - error code to use over REST calls
     * @param newErrorMessageId - unique Id for the message
     * @param newErrorMessage   - text for the message
     * @param newSystemAction   - description of the action taken by the system when the error condition happened
     * @param newUserAction     - instructions for resolving the error
     */
    FileOMRSErrorCode(int newHTTPErrorCode, String newErrorMessageId, String newErrorMessage, String newSystemAction, String newUserAction) {
        this.messageDefinition = new ExceptionMessageDefinition(newHTTPErrorCode,
                newErrorMessageId,
                newErrorMessage,
                newSystemAction,
                newUserAction);
    }

    /**
     * Retrieve a message definition object for an exception.  This method is used when there are no message inserts.
     *
     * @return message definition object.
     */
    @Override
    public ExceptionMessageDefinition getMessageDefinition() {
        return messageDefinition;
    }


    /**
     * Retrieve a message definition object for an exception.  This method is used when there are values to be inserted into the message.
     *
     * @param params array of parameters (all strings).  They are inserted into the message according to the numbering in the message text.
     * @return message definition object.
     */
    @Override
    public ExceptionMessageDefinition getMessageDefinition(String... params) {
        messageDefinition.setMessageParameters(params);
        return messageDefinition;
    }

    /**
     * toString() JSON-style
     *
     * @return string description
     */
    @Override
    public String toString() {
        return "FileOMRSErrorCode{" +
                "messageDefinition=" + messageDefinition +
                '}';
    }

}
