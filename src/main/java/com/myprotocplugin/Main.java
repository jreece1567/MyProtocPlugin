/**
 *
 */
package com.myprotocplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.compiler.PluginProtos;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.Builder;

/**
 * Simple protoc plugin, produces JSON representing a .proto file.
 *
 * @author jreece
 *
 */
public class Main {

    // for pretty-printing the JSON we generate
    private static final Gson gson = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().serializeNulls().create();

    // a map of package-name-->list-of-descriptors (which can be enums or
    // messages)
    private static final HashMap<String, List<GeneratedMessageV3>> entities = new HashMap<String, List<GeneratedMessageV3>>();

    private static final HashMap<String, List<EnumDescriptorProto>> _enums = new HashMap<String, List<EnumDescriptorProto>>();
    private static final HashMap<String, List<DescriptorProto>> _messages = new HashMap<String, List<DescriptorProto>>();
    private static final List<ServiceDescriptorProto> _services = new ArrayList<ServiceDescriptorProto>();

    private static final JsonObject swaggerDoc = new JsonObject();

    private static void buildSwaggerSkeleton(final String serviceName) {

        // final JsonObject swaggerDoc = new JsonObject();

        swaggerDoc.add("swagger", new JsonPrimitive("2.0"));

        final JsonObject info = new JsonObject();
        info.add("title", new JsonPrimitive(serviceName));
        info.add("version", new JsonPrimitive("1.0"));
        info.add("description", new JsonPrimitive(""));
        info.add("x-last-modified-at", new JsonPrimitive(""));
        final JsonObject license = new JsonObject();
        license.add("name", new JsonPrimitive("Apache 2 License"));
        license.add("url", new JsonPrimitive(
                "http://www.apache.org/licenses/LICENSE-2.0"));
        info.add("license", license);
        final JsonObject contact = new JsonObject();
        contact.add("name", new JsonPrimitive("Westfield Support"));
        contact.add("url", new JsonPrimitive("http://www.westfieldstatus.com"));
        contact.add("email", new JsonPrimitive("help@westfieldsupport.com"));
        info.add("contact", contact);
        swaggerDoc.add("info", info);

        final JsonArray tags = new JsonArray();
        final JsonObject tag = new JsonObject();
        tag.add("name", new JsonPrimitive(""));
        swaggerDoc.add("tags", tags);

        final JsonArray schemes = new JsonArray();
        schemes.add(new JsonPrimitive("http"));
        schemes.add(new JsonPrimitive("https"));
        swaggerDoc.add("schemes", schemes);

        swaggerDoc.add("basePath", new JsonPrimitive("/"));

        final JsonArray consumes = new JsonArray();
        consumes.add(new JsonPrimitive("application/json"));
        swaggerDoc.add("consumes", consumes);

        final JsonArray produces = new JsonArray();
        produces.add(new JsonPrimitive("application/json"));
        swaggerDoc.add("produces", produces);

        final JsonObject securityDefinitions = new JsonObject();
        final JsonObject apiKey = new JsonObject();
        apiKey.add("description", new JsonPrimitive("A Westfield API-key."));
        apiKey.add("type", new JsonPrimitive("apiKey"));
        apiKey.add("name", new JsonPrimitive("api_key"));
        apiKey.add("in", new JsonPrimitive("query"));
        securityDefinitions.add("api_key", apiKey);
        swaggerDoc.add("securityDefinitions", securityDefinitions);

        final JsonArray access = new JsonArray();
        access.add(new JsonPrimitive(""));
        swaggerDoc.add("x-service-access", access);

        final JsonObject definitions = new JsonObject();
        swaggerDoc.add("definitions", definitions);

        final JsonObject paths = new JsonObject();
        swaggerDoc.add("paths", paths);

        System.err.println(gson.toJson(swaggerDoc));
    }

    /**
     * Recursively traverse the AST and build a dictionary
     *
     * @param packageName
     *            the current package name from the .proto
     * @param enums
     *            the list of enums declared in the .proto
     * @param descriptors
     *            the list of messages declared in the .proto
     */
    private static final void traverseAST(final String packageName,
            final List<EnumDescriptorProto> enums,
            final List<DescriptorProto> descriptors) {

        // add the entries we know about
        if (entities.get(packageName) == null) {
            entities.put(packageName, new ArrayList<GeneratedMessageV3>());
        }
        if (enums != null) {
            entities.get(packageName).addAll(enums);
        }
        if (descriptors != null) {
            entities.get(packageName).addAll(descriptors);
        }
        if (_enums.get(packageName) == null) {
            _enums.put(packageName, new ArrayList<EnumDescriptorProto>());
        }
        if (enums != null) {
            _enums.get(packageName).addAll(enums);
        }
        if (_messages.get(packageName) == null) {
            _messages.put(packageName, new ArrayList<DescriptorProto>());
        }
        if (descriptors != null) {
            _messages.get(packageName).addAll(descriptors);
        }

        // loop through the descriptors, recurse to find nested enums and
        // messages
        for (final DescriptorProto descriptor : descriptors) {

            final List<EnumDescriptorProto> _enums = descriptor
                    .getEnumTypeList();
            final List<DescriptorProto> _descriptors = descriptor
                    .getNestedTypeList();

            traverseAST(packageName + "." + descriptor.getName(), _enums,
                    _descriptors);
        }
    }

    private static void processServices(
            final List<ServiceDescriptorProto> services) {

        _services.addAll(services);

        for (final ServiceDescriptorProto service : services) {
            final String serviceName = service.getName();
            System.err.println("Service:" + serviceName);
            final List<MethodDescriptorProto> methods = service.getMethodList();
            for (final MethodDescriptorProto method : methods) {
                final String methodName = method.getName();
                final String inParam = method.getInputType();
                final String outParam = method.getOutputType();
                System.err.println("Method: " + methodName + "(" + inParam
                        + ") returns " + outParam);
            }
        }
    }

    /**
     * Generate the Meta-JSON
     *
     * @param name
     *            the service name
     * @return an array of meta JSON
     */
    private static final JsonArray generateMeta(final String name) {
        final JsonArray jsonArray = new JsonArray();

        // loop through the dictionary we've built from the AST
        final Iterator<String> itPackages = entities.keySet().iterator();
        while (itPackages.hasNext()) {
            final String pkgName = itPackages.next();
            final List<GeneratedMessageV3> descriptors = entities.get(pkgName);
            for (final GeneratedMessageV3 descriptor : descriptors) {
                // process enums
                if (descriptor instanceof EnumDescriptorProto) {
                    final JsonObject json = new JsonObject();
                    json.add("name", new JsonPrimitive(
                            ((EnumDescriptorProto) descriptor).getName()));
                    json.add("type", new JsonPrimitive("Enum"));
                    json.add("filename", new JsonPrimitive(name));
                    json.add("package", new JsonPrimitive(pkgName));
                    final JsonArray values = new JsonArray();
                    final List<EnumValueDescriptorProto> valueList = ((EnumDescriptorProto) descriptor)
                            .getValueList();
                    for (final EnumValueDescriptorProto enumValue : valueList) {
                        final JsonObject value = new JsonObject();
                        value.add("name",
                                new JsonPrimitive(enumValue.getName()));
                        value.add("value",
                                new JsonPrimitive(enumValue.getNumber()));
                        values.add(value);
                    }
                    json.add("values", values);
                    jsonArray.add(json);
                }
                // process messages
                if (descriptor instanceof DescriptorProto) {
                    final JsonObject json = new JsonObject();
                    json.add("name", new JsonPrimitive(
                            ((DescriptorProto) descriptor).getName()));
                    json.add("type", new JsonPrimitive("Message"));
                    json.add("filename", new JsonPrimitive(name));
                    json.add("package", new JsonPrimitive(pkgName));
                    final JsonArray properties = new JsonArray();
                    final List<FieldDescriptorProto> propertyList = ((DescriptorProto) descriptor)
                            .getFieldList();
                    for (final FieldDescriptorProto property : propertyList) {
                        final JsonObject field = new JsonObject();
                        field.add("name", new JsonPrimitive(property.getName()));
                        if ("LABEL_REPEATED".equals(property.getLabel()
                                .toString())) {

                            field.add("type", new JsonPrimitive("array"));
                            final JsonObject items = new JsonObject();
                            if (property.hasTypeName()) {
                                items.add(
                                        "type",
                                        new JsonPrimitive(property
                                                .getTypeName()));
                            } else {
                                items.add("type", new JsonPrimitive(property
                                        .getType().name()));
                            }
                            field.add("items", items);
                        } else {
                            if (property.hasTypeName()) {
                                field.add(
                                        "type",
                                        new JsonPrimitive(property
                                                .getTypeName()));
                            } else {
                                field.add("type", new JsonPrimitive(property
                                        .getType().name()));
                            }
                        }
                        properties.add(field);
                    }
                    json.add("properties", properties);
                    jsonArray.add(json);
                }
            }
        }

        return jsonArray;
    }

    /**
     * Generate Swagger
     *
     * @param name
     *            the service name
     */
    private static final void generateSwagger(final String name) {

        // get a list of enums
        final Map<String, EnumDescriptorProto> enums = new HashMap<String, EnumDescriptorProto>();
        final Iterator<String> itEnums = _enums.keySet().iterator();
        while (itEnums.hasNext()) {
            final String pkgName = itEnums.next();
            final List<EnumDescriptorProto> descriptors = _enums.get(pkgName);
            for (final EnumDescriptorProto descriptor : descriptors) {
                // process enums
                enums.put("." + pkgName + "." + descriptor.getName(),
                        (descriptor));
            }
        }

        // loop through the dictionary we've built from the AST
        final Iterator<String> itPackages = _messages.keySet().iterator();
        while (itPackages.hasNext()) {
            final String pkgName = itPackages.next();
            final List<DescriptorProto> descriptors = _messages.get(pkgName);
            for (final DescriptorProto descriptor : descriptors) {
                // process messages
                final JsonObject definitions = swaggerDoc
                        .getAsJsonObject("definitions");
                final JsonObject definition = new JsonObject();
                definitions.add(descriptor.getName(), definition);
                definition.add("type", new JsonPrimitive("object"));
                definition
                        .add("additionalProperties", new JsonPrimitive(false));
                final JsonObject properties = new JsonObject();
                definition.add("properties", properties);
                final List<FieldDescriptorProto> fieldList = descriptor
                        .getFieldList();
                for (final FieldDescriptorProto field : fieldList) {
                    final JsonObject property = new JsonObject();
                    properties.add(field.getName(), property);
                    if ("LABEL_REPEATED".equals(field.getLabel().toString())) {

                        property.add("type", new JsonPrimitive("array"));
                        final JsonObject items = new JsonObject();
                        property.add("items", items);
                        if (field.hasTypeName()) {
                            final String typeName = field.getTypeName();
                            System.err.println("Type: " + typeName);
                            if (enums.containsKey(typeName)) {
                                // it's an Enum
                                final EnumDescriptorProto eDescriptor = enums
                                        .get(typeName);
                                items.add("type", new JsonPrimitive("integer"));
                                items.add("format", new JsonPrimitive("int32"));
                                final List<EnumValueDescriptorProto> valueList = eDescriptor
                                        .getValueList();
                                final StringBuilder sb = new StringBuilder();
                                final JsonArray values = new JsonArray();
                                for (final EnumValueDescriptorProto enumValue : valueList) {
                                    sb.append(enumValue.getName() + "="
                                            + enumValue.getNumber() + ", ");
                                    values.add(new JsonPrimitive(enumValue
                                            .getNumber()));
                                }
                                items.add("enum", values);
                                items.add(
                                        "description",
                                        new JsonPrimitive(
                                                sb.toString()
                                                        .substring(
                                                                0,
                                                                sb.toString()
                                                                        .length() - 2)));

                            } else {
                                // it's a $ref to another Message
                                final String refName = "#/definitions/"
                                        + typeName.substring(typeName
                                                .lastIndexOf(".") + 1);
                                items.add("$ref", new JsonPrimitive(refName));
                            }
                        } else {
                            final String typ = field.getType().name();
                            if ("TYPE_STRING".equals(typ)) {
                                items.add("type", new JsonPrimitive("string"));
                            } else if ("TYPE_DOUBLE".equals(typ)) {
                                items.add("type", new JsonPrimitive("number"));
                                items.add("format", new JsonPrimitive("double"));
                            } else if ("TYPE_INT32".equals(typ)) {
                                items.add("type", new JsonPrimitive("integer"));
                                items.add("format", new JsonPrimitive("int32"));
                            } else if ("TYPE_INT64".equals(typ)) {
                                items.add("type", new JsonPrimitive("integer"));
                                items.add("format", new JsonPrimitive("int64"));
                            }
                        }
                    } else {
                        if (field.hasTypeName()) {
                            final String typeName = field.getTypeName();
                            System.err.println("Type: " + typeName);
                            if (enums.containsKey(typeName)) {
                                // it's an Enum
                                final EnumDescriptorProto eDescriptor = enums
                                        .get(typeName);
                                property.add("type", new JsonPrimitive(
                                        "integer"));
                                property.add("format", new JsonPrimitive(
                                        "int32"));
                                final List<EnumValueDescriptorProto> valueList = eDescriptor
                                        .getValueList();
                                final StringBuilder sb = new StringBuilder();
                                final JsonArray values = new JsonArray();
                                for (final EnumValueDescriptorProto enumValue : valueList) {
                                    sb.append(enumValue.getName() + "="
                                            + enumValue.getNumber() + ", ");
                                    values.add(new JsonPrimitive(enumValue
                                            .getNumber()));
                                }
                                property.add("enum", values);
                                property.add(
                                        "description",
                                        new JsonPrimitive(
                                                sb.toString()
                                                        .substring(
                                                                0,
                                                                sb.toString()
                                                                        .length() - 2)));
                            } else {
                                // it's a $ref to another Message
                                final String refName = "#/definitions/"
                                        + typeName.substring(typeName
                                                .lastIndexOf(".") + 1);
                                property.add("$ref", new JsonPrimitive(refName));
                            }
                        } else {
                            final String typ = field.getType().name();
                            if ("TYPE_STRING".equals(typ)) {
                                property.add("type",
                                        new JsonPrimitive("string"));
                            } else if ("TYPE_DOUBLE".equals(typ)) {
                                property.add("type",
                                        new JsonPrimitive("number"));
                                property.add("format", new JsonPrimitive(
                                        "double"));
                            } else if ("TYPE_INT32".equals(typ)) {
                                property.add("type", new JsonPrimitive(
                                        "integer"));
                                property.add("format", new JsonPrimitive(
                                        "int32"));
                            } else if ("TYPE_INT64".equals(typ)) {
                                property.add("type", new JsonPrimitive(
                                        "integer"));
                                property.add("format", new JsonPrimitive(
                                        "int64"));
                            }
                        }
                    }
                }
                swaggerDoc.add("definitions", definitions);
            }

        }

        // process services
        final JsonObject paths = swaggerDoc.getAsJsonObject("paths");

        for (final ServiceDescriptorProto service : _services) {
            final List<MethodDescriptorProto> methods = service.getMethodList();
            for (final MethodDescriptorProto method : methods) {
                final String methodName = method.getName();
                final String inParam = method.getInputType();
                final String outParam = method.getOutputType();
                final JsonObject verbs = new JsonObject();
                paths.add("/" + methodName, verbs);
                final JsonObject verb = new JsonObject();
                verbs.add("post", verb);
                final JsonArray parameters = new JsonArray();
                verb.add("parameters", parameters);
                final JsonObject parameter = new JsonObject();
                parameter.add("name", new JsonPrimitive("data"));
                parameter.add("in", new JsonPrimitive("body"));
                parameter.add("required", new JsonPrimitive(true));
                final JsonObject inSchema = new JsonObject();
                inSchema.add("$ref", new JsonPrimitive("#/definitions/"
                        + inParam.substring(inParam.lastIndexOf(".") + 1)));
                parameter.add("schema", inSchema);
                parameters.add(parameter);
                final JsonObject responses = new JsonObject();
                verb.add("responses", responses);
                final JsonObject response = new JsonObject();
                responses.add("200", response);
                final JsonObject outSchema = new JsonObject();
                response.add("description", new JsonPrimitive("Success"));
                outSchema.add("$ref", new JsonPrimitive("#/definitions/"
                        + outParam.substring(outParam.lastIndexOf(".") + 1)));
                response.add("schema", outSchema);
                final JsonArray security = new JsonArray();
                final JsonObject secure = new JsonObject();
                secure.add("api_key", new JsonArray());
                security.add(secure);
                verb.add("security", security);
            }
        }

        swaggerDoc.add("paths", paths);

    }

    /**
     * Process a single file in the .proto file list supplied by protoc, write
     * whatever we've generated to the response-builder.
     *
     * @param fdp
     *            the .proto file descriptor, as supplied by the protoc compiler
     * @param responseBuilder
     *            the buider to contain the response
     */
    private static final void processFile(final FileDescriptorProto fdp,
            final Builder responseBuilder) {

        // get top-level enums and messages
        final List<EnumDescriptorProto> enums = fdp.getEnumTypeList();
        final List<DescriptorProto> messages = fdp.getMessageTypeList();
        final List<ServiceDescriptorProto> services = fdp.getServiceList();
        processServices(services);

        // set up a map to record the AST
        entities.clear();
        entities.put(fdp.getPackage(), new ArrayList<GeneratedMessageV3>());

        // get enums and messages nested inside of top-level messages
        traverseAST(fdp.getPackage(), enums, messages);

        // the JSON structure we will fill in and write to the response-builder
        final JsonArray jsonArray = generateMeta(fdp.getName());

        // add the JSON we've built to the response
        final PluginProtos.CodeGeneratorResponse.File.Builder f = PluginProtos.CodeGeneratorResponse.File
                .newBuilder();
        f.setName(fdp.getName() + ".json");
        f.setContent(gson.toJson(jsonArray));
        responseBuilder.addFile(f);

    }

    /**
     * Generate code (in this case JSON) from a protoc 'CodeGeneratorRequest'
     *
     * @param request
     *            the request, as supplied by the protoc compiler
     * @param responseBuilder
     *            the response-builder, to which we will write whatever we
     *            generate
     */
    private static final void generate(final CodeGeneratorRequest request,
            final Builder responseBuilder) {

        // process each .proto file passed in the request
        for (final FileDescriptorProto fdp : request.getProtoFileList()) {
            // generate something from the .proto file, add that to the
            // response-builder
            System.err.println("Processing: " + fdp.getName());
            processFile(fdp, responseBuilder);
        }
        generateSwagger("Identity");

    }

    /**
     * @param args
     *            command-line arguments
     */
    public static void main(final String[] args) {
        // System.err.println("Demo protoc-plugin starting ...");
        buildSwaggerSkeleton("Identity");

        try {
            // get the request from System.in (stdin)
            final CodeGeneratorRequest codeGeneratorRequest = CodeGeneratorRequest
                    .parseFrom(System.in);

            // make a builder for the response
            final Builder codeGeneratorResponseBuilder = CodeGeneratorResponse
                    .newBuilder();

            // generate whatever we are going to generate from the request,
            // write to the response-builder
            generate(codeGeneratorRequest, codeGeneratorResponseBuilder);

            // write the response to System.out (stdout)
            codeGeneratorResponseBuilder.build().writeTo(System.out);

            System.err.println(gson.toJson(swaggerDoc));

        } catch (final IOException e) {
            e.printStackTrace();
        }

        // System.err.println("Entities: " + entities.keySet() + "\n");
        // System.err.println("Messages: " + _messages.keySet() + "\n");
        // System.err.println("Enums: " + _enums.keySet() + "\n");

        // System.err.println("Demo protoc-plugin ending ...");
    }

}
