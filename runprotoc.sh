protoc --proto_path=. --proto_path=../identity/proto --plugin=protoc-gen-code=./plugin_wrapper.sh --code_out=./output hello.proto 

