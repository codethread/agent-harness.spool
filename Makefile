.PHONY: all build install open-config

all: build install open-config

GO_CLI := ./cli/cmd/todo
BIN := ./cli/bin/todo
CONFIG_HOME ?= $(if $(XDG_CONFIG_HOME),$(XDG_CONFIG_HOME),$(HOME)/.config)
CONFIG_DIR := $(CONFIG_HOME)/atom
CONFIG_FILE := $(CONFIG_DIR)/config.json

build:
	go build -o $(BIN) $(GO_CLI)

install:
	go install $(GO_CLI)

open-config:
	@if [ -z "$(EDITOR)" ]; then \
		echo "EDITOR is not set" >&2; \
		exit 1; \
	fi
	@mkdir -p "$(CONFIG_DIR)"
	@if [ ! -f "$(CONFIG_FILE)" ]; then \
		printf '{\n  "db": "%s/todo.sqlite",\n  "format": "human"\n}\n' "$(HOME)" > "$(CONFIG_FILE)"; \
	fi
	$(EDITOR) "$(CONFIG_FILE)"
