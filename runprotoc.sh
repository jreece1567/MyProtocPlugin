#!/bin/bash

#protoc --proto_path=. --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./hello.proto

#protoc --proto_path=. --proto_path=./github.com/westfield/identity/proto --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/identity/proto/data/identity_data.proto

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/identity --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/identity/service/identity_service.proto 2>identity_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/location --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/location/service/location_service.proto 2>location_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/messaging --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/messaging/service/messaging_service.proto 2>messaging_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/payment --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/payment/service/payment_service.proto 2>payment_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/product --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/product/service/product_service.proto 2>product_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/receipt --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/receipt/service/receipt_service.proto 2>receipt_swagger.json

protoc --proto_path=. --proto_path=./github.com/westfield/protobuf/databunker --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/protobuf/databunker/service/databunker_service.proto 2>databunker_swagger.json

