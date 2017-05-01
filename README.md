# MyProtocPlugin

Overview
========

This is a very simple demo of a Protocol Buffer Compiler (protoc) **plugin** written in Java.
The demo processes one or more .proto files as provided by protoc, and generates JSON representing the enums and messages defined there.

## Building

This is a standard Maven project - **mvn clean package** will build the demo as a shaded JAR.

## Scripts and sample .proto

There are two shell scripts in this project.

	plugin_wrapper.sh

This script wraps up the invocation of the plugin-JAR file so that it can be executed by protoc.

	runprotoc.sh

This script runs the protoc compiler, using the demo plugin.

A sample **hello.proto** file is provided.

## Running

When the **runprotoc.sh** script is executed, it will run protoc, which will invoke the plugin, which will write a JSON file to the project's **./output** folder.

## Prerequisites
The **protoc** compiler must be installed and available on your **PATH**

Download the protoc binary, if necessary:
See the <a href="https://github.com/google/protobuf/releases">Protobuf Releases</a> page.
The protoc version used in this demo can be <a href="https://github.com/google/protobuf/releases/download/v3.2.0/protoc-3.2.0-osx-x86_64.zip">downloaded here</a>.

## Frameworks
The following Java packages are used:<br/>
<ul>
<li><a href="https://github.com/google/protobuf/tree/v3.2.0/java">Protobuf-Java</a></li>
<li><a href="https://sites.google.com/site/gson/Home">GSON</a></li>
</ul><br/>

