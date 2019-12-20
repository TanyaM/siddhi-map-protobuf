/*
 * Copyright (c)  2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.siddhi.extension.map.protobuf.utils;

import io.siddhi.core.config.SiddhiAppContext;
import io.siddhi.core.exception.SiddhiAppCreationException;
import io.siddhi.query.api.SiddhiApp;
import io.siddhi.query.api.annotation.Annotation;
import io.siddhi.query.api.definition.Attribute;
import io.siddhi.query.api.definition.StreamDefinition;
import io.siddhi.query.api.exception.SiddhiAppValidationException;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Class to hold the static util methods needed.
 */
public class ProtobufUtils {
    public static String getServiceName(String path, String siddhiAppName, String streamID) {
        List<String> urlParts = new ArrayList<>(Arrays.asList(path.substring(1).split(ProtobufConstants
                .PORT_SERVICE_SEPARATOR)));
        if (urlParts.contains(ProtobufConstants.EMPTY_STRING)) {
            throw new SiddhiAppValidationException(siddhiAppName + ": " + streamID + ": Malformed URL. There should " +
                    "not be any empty parts in the URL between two '/'");
        }
        if (urlParts.size() < 2) {
            throw new SiddhiAppValidationException(siddhiAppName + ": " + streamID + ": Malformed URL. After port " +
                    "number at least two sections should be available separated by '/' as in 'grpc://<host>:<port>/" +
                    "<ServiceName>/<MethodName>'");
        }
        return urlParts.get(ProtobufConstants.PATH_SERVICE_NAME_POSITION);
    }

    public static String getMethodName(String path, String siddhiAppName, String streamID) {
        List<String> urlParts = new ArrayList<>(Arrays.asList(path.split(ProtobufConstants.PORT_SERVICE_SEPARATOR)));
        urlParts.removeAll(Collections.singletonList(ProtobufConstants.EMPTY_STRING));
        if (urlParts.size() < 2) {
            throw new SiddhiAppValidationException(siddhiAppName + ": " + streamID + ": Malformed URL. After port " +
                    "number at least two sections should be available separated by '/' as in 'grpc://<host>:<port>/" +
                    "<ServiceName>/<MethodName>'");
        }
        return urlParts.get(ProtobufConstants.PATH_METHOD_NAME_POSITION);
    }

    public static Class getDataType(Attribute.Type type) {
        switch (type) {
            case STRING: {
                return String.class;
            }
            case INT: {
                return Integer.TYPE;
            }
            case LONG: {
                return Long.TYPE;
            }
            case BOOL: {
                return Boolean.TYPE;
            }
            case DOUBLE: {
                return Double.TYPE;
            }
            case FLOAT: {
                return Float.TYPE;
            }
        }
        return null; // won't reach here
    }

    public static String protobufFieldsWithTypes(Field[] protobufFields) {
        StringBuilder variableNamesWithType = new StringBuilder("{ ");
        for (Field field : protobufFields) {
            if (field.getName().equals("bitField0_")) {
                continue;
            }
            String name = field.getName().substring(0, field.getName().length() - 1);
            String[] tempFieldNameArray = field.getType().getTypeName().split("\\.");
            String type = tempFieldNameArray[tempFieldNameArray.length - 1];
            if (type.equals("Object")) {
                type = "String";
                // as Object
            } else if (type.equals("MapField")) {
                type = "Map";
            } else if (List.class.isAssignableFrom(field.getType())) {
                type = "List";
            }
            variableNamesWithType.append("\"'").append(name).append("' : ").append(type).append("\" , ");

        }
        variableNamesWithType = new StringBuilder(variableNamesWithType.substring(0,
                variableNamesWithType.length() - 2));
        variableNamesWithType.append("}");
        return variableNamesWithType.toString();
    }

    public static List<String> getRPCmethodList(String serviceReference, String siddhiAppName, String streamID) {
        //require full serviceName
        List<String> rpcMethodNameList = new ArrayList<>();
        String[] serviceReferenceArray = serviceReference.split("\\.");
        String serviceName = serviceReferenceArray[serviceReferenceArray.length - 1];
        String stubReference = serviceReference + ProtobufConstants.GRPC_PROTOCOL_NAME_UPPERCAMELCASE
                + ProtobufConstants.DOLLAR_SIGN + serviceName + ProtobufConstants.STUB_NAME;
        Method[] methodsInStub;
        try {
            methodsInStub = Class.forName(stubReference).getMethods(); //get all methods in stub Inner class
        } catch (ClassNotFoundException e) {
            throw new SiddhiAppCreationException(siddhiAppName + ": " + streamID + ": " +
                    "Invalid service name provided in url, provided service name : '" + serviceName + "',"
                    + e.getMessage(), e);
        }
        for (Method method : methodsInStub) {
            if (method.getDeclaringClass().getName().equals(stubReference)) { // check if the method belongs
                // to blocking stub, other methods that does not belongs to blocking stub are not rpc methods
                rpcMethodNameList.add(method.getName());
            }
        }
        return rpcMethodNameList;
    }

    public static String toUpperCamelCase(String attributeName) {
        return attributeName.substring(0, 1).toUpperCase() + attributeName.substring(1);
    }

    /**
     * Remove underscore for comparability.
     *
     * @param attributeName
     * @return a String with replacing underscores with uppercase letters.
     */
    public static String removeUnderscore(String attributeName) {
        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = false;
        for (char c : attributeName.toCharArray()) {
            if (c == '_') {
                capitalizeNext = true;
            } else {
                if (capitalizeNext) {
                    sb.append(Character.toUpperCase(c));
                    capitalizeNext = false;
                } else {
                    sb.append(c);
                }
            }
        }
        return sb.toString();
    }

    public static String getURL(SiddhiAppContext siddhiAppContext, String annotationCategory,
                                String annotationType, String urlName, String mappingName, String id) {
        SiddhiApp siddhiApp = siddhiAppContext.getSiddhiApp();
        for (StreamDefinition streamDefinition : siddhiApp.getStreamDefinitionMap().values()) {
            for (Annotation annotation : streamDefinition.getAnnotations()) {
                if (annotationCategory.equalsIgnoreCase(annotation.getName()) &&
                        annotationType.equalsIgnoreCase(annotation.getElement("type")) &&
                        id.equalsIgnoreCase(annotation.getElement(mappingName))) {
                    return annotation.getElement(urlName);
                }
            }
        }
        return null;
    }

}
