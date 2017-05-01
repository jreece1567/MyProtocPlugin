/**
 *
 */
package com.myprotocplugin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

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

        // set up a map to record the AST
        entities.clear();
        entities.put(fdp.getPackage(), new ArrayList<GeneratedMessageV3>());

        // get enums and messages nested inside of top-level messages
        traverseAST(fdp.getPackage(), enums, messages);

        // the JSON structure we will fill in and write to the response-builder
        final JsonArray jsonArray = new JsonArray();

        // loop through the dictionary we've built from the AST
        final Iterator<String> itPackages = entities.keySet().iterator();
        while (itPackages.hasNext()) {
            final List<GeneratedMessageV3> descriptors = entities
                    .get(itPackages.next());
            for (final GeneratedMessageV3 descriptor : descriptors) {
                // process enums
                if (descriptor instanceof EnumDescriptorProto) {
                    final JsonObject json = new JsonObject();
                    json.add("name", new JsonPrimitive(
                            ((EnumDescriptorProto) descriptor).getName()));
                    json.add("type", new JsonPrimitive("Enum"));
                    json.add("filename", new JsonPrimitive(fdp.getName()));
                    json.add("package", new JsonPrimitive(fdp.getPackage()));
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
                    json.add("filename", new JsonPrimitive(fdp.getName()));
                    json.add("package", new JsonPrimitive(fdp.getPackage()));
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

    }

    /**
     * @param args
     *            command-line arguments
     */
    public static void main(final String[] args) {
        System.err.println("Demo protoc-plugin starting ...");
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

        } catch (final IOException e) {
            e.printStackTrace();
        }
        System.err.println("Demo protoc-plugin ending ...");
    }

}
