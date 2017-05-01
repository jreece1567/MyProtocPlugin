protoc --proto_path=. --proto_path=./github.com/westfield/identity/proto --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output ./github.com/westfield/identity/proto/service/identity_service.proto

