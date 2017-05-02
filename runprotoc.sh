#!/bin/bash

#protoc --proto_path=. --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./hello.proto

#protoc --proto_path=. --proto_path=./github.com/westfield/identity/proto --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/identity/proto/data/identity_data.proto

protoc --proto_path=. --proto_path=./github.com/westfield/identity/proto --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/identity/proto/service/identity_service.proto

