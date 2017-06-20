/**
 *
 */
package com.myprotocplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.google.api.AnnotationsProto;
import com.google.api.HttpRule;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumDescriptorProto;
import com.google.protobuf.DescriptorProtos.EnumValueDescriptorProto;
import com.google.protobuf.DescriptorProtos.FieldDescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodDescriptorProto;
import com.google.protobuf.DescriptorProtos.MethodOptions;
import com.google.protobuf.DescriptorProtos.ServiceDescriptorProto;
import com.google.protobuf.ExtensionRegistry;
import com.google.protobuf.GeneratedMessageV3;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.compiler.PluginProtos;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse;
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse.Builder;

/**
 * Simple protoc plugin, produces JSON representing a .proto file, as well as a
 * Swagger-2.0-format JSON file representing <b>all</b> entities (and
 * transitives) defined in a .proto file.
 * <ol>
 * <li>protoc is invoked with one or more proto files specified on the
 * command-line.</li>
 * <li>protoc invokes the plugin and supplies a pre-parsed AST representing the
 * proto file(s) and their transitive dependencies to the plugin via
 * <b>stdin</b> (in Java, <b>System.in</b>)</li>
 * <li>protoc plugins write their output to <b>stdout</b> (in Java,
 * <b>System.out</b>). One or more files may be written.
 * </ol>
 *
 * <p>
 * The process of developing a protoc plugin, working with the AST, and
 * generating output is very poorly documented. Googling around helps a bit,
 * though you have to know enough C++, Go, and Python to get much out of that.
 * The rest is discovered via trial-and-error. This is concerning, since it is
 * unclear whether this process will change as protoc evolves. Google should do
 * a much better job of creating/maintaining official protoc documentation so
 * that stable plugins can be built.
 * </p>
 *
 * @author jreece
 *
 */
public class Main {

    /**
     * for pretty-printing the JSON we generate
     */
    private static final Gson gson = new GsonBuilder().setPrettyPrinting()
            .disableHtmlEscaping().serializeNulls().create();

    /**
     * a map of package-name-->list-of-descriptors (which can be enums or
     * messages)
     */
    private static final HashMap<String, List<GeneratedMessageV3>> entities = new HashMap<String, List<GeneratedMessageV3>>();

    /**
     * a map of package-name-->list-of-descriptors (which are enum descriptors)
     */
    private static final HashMap<String, List<EnumDescriptorProto>> _enums = new HashMap<String, List<EnumDescriptorProto>>();

    /**
     * a map of package-name-->list-of-descriptors (which are message
     * descriptors)
     */
    private static final HashMap<String, List<DescriptorProto>> _messages = new HashMap<String, List<DescriptorProto>>();

    /**
     * a list-of-descriptors (which are service descriptors)
     */
    private static final List<ServiceDescriptorProto> _services = new ArrayList<ServiceDescriptorProto>();

    /**
     * a swagger-2.0-format JSON document which is built by this program.
     */
    private static final JsonObject swaggerDoc = new JsonObject();

    /**
     * Generate a skeleton of a Swagger-JSON, with all top-level element stubs
     *
     * @param serviceName
     *            the name of the service
     */
    private static void buildSwaggerSkeleton(final String serviceName) {

        swaggerDoc.add("swagger", new JsonPrimitive("2.0"));

        final JsonObject info = new JsonObject();
        info.add("title", new JsonPrimitive("WRS " + serviceName + " Service"));
        info.add("version", new JsonPrimitive("1.0"));
        info.add("description", new JsonPrimitive(""));
        info.add("x-last-modified-at", new JsonPrimitive(new Date().toString()));
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
        tag.add("name", new JsonPrimitive(serviceName.toUpperCase()));
        tags.add(tag);
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

        // System.err.println(gson.toJson(swaggerDoc));
    }

    /**
     * Recursively traverse the AST and build a dictionary of all Messages and
     * Enums that we find.
     *
     * @param packageName
     *            the current package name from the .proto
     * @param enums
     *            the list of enums declared in the .proto
     * @param messages
     *            the list of messages declared in the .proto
     */
    private static final void traverseAST(final String packageName,
            final List<EnumDescriptorProto> enums,
            final List<DescriptorProto> messages) {

        // add the entries we know about
        if (entities.get(packageName) == null) {
            entities.put(packageName, new ArrayList<GeneratedMessageV3>());
        }
        if (enums != null) {
            entities.get(packageName).addAll(enums);
        }
        if (messages != null) {
            entities.get(packageName).addAll(messages);
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
        if (messages != null) {
            _messages.get(packageName).addAll(messages);
        }

        // loop through the descriptors, recurse to find nested items
        for (final DescriptorProto message : messages) {

            final List<EnumDescriptorProto> _enums = message.getEnumTypeList();
            final List<DescriptorProto> _descriptors = message
                    .getNestedTypeList();

            traverseAST(packageName + "." + message.getName(), _enums,
                    _descriptors);
        }
    }

    /**
     * Generate the Meta-JSON
     *
     * @param name
     *            the service name
     * @return an array of meta JSON
     */
    private static final JsonArray generateMetaJSON(final String name) {
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
     * Generate Swagger JSON
     *
     * @param name
     *            the service name
     */
    private static final void generateSwaggerJSON(final String serviceName) {

        // get the list of enums
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

        // loop through the list of messages we've built from the AST
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
                    // handle array field
                    if ("LABEL_REPEATED".equals(field.getLabel().toString())) {

                        property.add("type", new JsonPrimitive("array"));
                        final JsonObject items = new JsonObject();
                        property.add("items", items);
                        if (field.hasTypeName()) {
                            final String typeName = field.getTypeName();
                            // System.err.println("Type: " + typeName);
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
                        // handle single-occurrence (non-array) field
                        if (field.hasTypeName()) {
                            final String typeName = field.getTypeName();
                            // System.err.println("Type: " + typeName);
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
                // extractOptions(method);
                final String methodName = method.getName();
                final String inParam = method.getInputType();
                final String outParam = method.getOutputType();
                final JsonObject verbs = new JsonObject();
                paths.add("/" + methodName, verbs);
                final JsonObject verb = new JsonObject();
                verbs.add("post", verb);
                final JsonArray tags = new JsonArray();
                tags.add(new JsonPrimitive(serviceName.toUpperCase()));
                verb.add("tags", tags);
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

    private static void extractOptions(final MethodDescriptorProto method) {

        final ExtensionRegistry registry = ExtensionRegistry.newInstance();
        AnnotationsProto.registerAllExtensions(registry);

        if (method.hasOptions()
                && (method.getOptions().toString().length() > 0)) {
            final MethodOptions options = method.getOptions();
            System.err.println("method.getName: " + method.getName());
            System.err
                    .println("options.toString: [" + options.toString() + "]");
            System.err.println("options.getSerializedSize: "
                    + options.getSerializedSize());
            System.err.println("options.isInitialized: "
                    + options.isInitialized());
            System.err
            .println("options.hasExtension(AnnotationsProto.http) is "
                    + options.hasExtension(AnnotationsProto.http));
            if (options.getExtension(AnnotationsProto.http) != null) {

                System.err
                .println("options.getExtention(AnnotationsProto.http) is not null");
            }
            final byte[] bytes = options.toByteArray();
            System.err.println("options.toByteArray(): " + bytesToHex(bytes));
            System.err.println("options.toByteArray().length: " + bytes.length);
            HttpRule rule = options.getExtension(AnnotationsProto.http);
            System.err.println("options.getExtension(AnnotationsProto.http): "
                    + rule.toString());
            try {
                rule = HttpRule.parser().parseFrom(
                        CodedInputStream.newInstance(bytes), registry);
                System.err
                        .println("HttpRule.parser().parseFrom(CodedInputStream.newInstance(bytes), registry): "
                                + rule.toString());
                System.err.println("rule.isInitialized(): "
                        + rule.isInitialized());
                System.err.println("rule.getPatternCase().toString(): "
                        + rule.getPatternCase().toString());
                System.err.println("rule.getSelector(): " + rule.getSelector());
                System.err.println("Unknown: "
                        + rule.getUnknownFields().toString());
                System.err.println("InitError: "
                        + rule.getInitializationErrorString());
                System.err.println("BodyBytes: "
                        + rule.getBodyBytes().toString());
                System.err.println("SelBytes: "
                        + rule.getSelectorBytes().toString());
                System.err.println("AddBind: "
                        + rule.getAdditionalBindingsCount());
                System.err.println("AllField: "
                        + rule.getAllFields().toString());
                System.err.println("Default: "
                        + rule.getDefaultInstanceForType().toString());
                System.err.println("Fields: " + rule.getAllFields().toString());
            } catch (final InvalidProtocolBufferException e) {
                e.printStackTrace();
            }
        }

    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();

    public static String bytesToHex(final byte[] bytes) {
        final char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            final int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[(j * 2) + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
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
        // get the list of services
        final List<ServiceDescriptorProto> services = fdp.getServiceList();
        _services.addAll(services);
        // if (fdp.getExtensionCount() > 0) {
        // System.err.println("Extensions: "
        // + fdp.getExtensionList().toString());
        // }

        // set up a map to record the AST
        entities.clear();
        entities.put(fdp.getPackage(), new ArrayList<GeneratedMessageV3>());

        // get enums and messages nested inside of top-level messages
        traverseAST(fdp.getPackage(), enums, messages);

        // the JSON structure we will fill in and write to the response-builder
        final JsonArray jsonArray = generateMetaJSON(fdp.getName());

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
     * @return the name of the service (eg. 'identity', 'messaging', etc.)
     */
    private static final String generate(final CodeGeneratorRequest request,
            final Builder responseBuilder) {

        String protoFileName = "";
        // process each .proto file passed in the request
        for (final FileDescriptorProto fdp : request.getProtoFileList()) {

            // generate something from the .proto file, add that to the
            // response-builder
            protoFileName = fdp.getName();
            System.err.println("Processing: " + fdp.getName());
            processFile(fdp, responseBuilder);
        }
        protoFileName = protoFileName.substring(
                protoFileName.lastIndexOf("/") + 1).replaceAll(
                "_service.proto", "");
        return protoFileName.substring(0, 1).toUpperCase()
                + protoFileName.substring(1);
    }

    /**
     * Plugin entry point. Note that command-line arguments are not used. Protoc
     * communicates with plugins via System.in and System.out only.
     *
     * @param args
     *            command-line arguments
     */
    public static void main(final String[] args) {
        // System.err.println("Demo protoc-plugin starting ...");
        String serviceName = "";

        try {
            // get the request from System.in (stdin)
            final CodeGeneratorRequest codeGeneratorRequest = CodeGeneratorRequest
                    .parseFrom(System.in);

            // make a builder for the response
            final Builder codeGeneratorResponseBuilder = CodeGeneratorResponse
                    .newBuilder();

            // generate whatever we are going to generate from the request,
            // write to the response-builder
            serviceName = generate(codeGeneratorRequest,
                    codeGeneratorResponseBuilder);

            // write the response to System.out (stdout)
            codeGeneratorResponseBuilder.build().writeTo(System.out);

            // generate the swagger-2.0 document from the proto-descriptor
            // information we have
            buildSwaggerSkeleton(serviceName);
            generateSwaggerJSON(serviceName);

            // write the swagger-doc to System.err - cannot use System.out since
            // protoc uses that channel.
            System.err.println(gson.toJson(swaggerDoc));

        } catch (final IOException e) {
            e.printStackTrace();
        }

        // System.err.println("Demo protoc-plugin ending ...");
    }

}
