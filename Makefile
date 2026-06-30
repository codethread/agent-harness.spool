.PHONY: all build install bootstrap open-config

all: build install

GO_CLI := ./cli/cmd/strand
MILL_CLI := ./cli/cmd/mill
BIN := ./cli/bin/strand
MILL_BIN := ./cli/bin/mill

# Optional explicit disposable world; leave empty to use repo-local bootstrap via `strand init`.
BOOTSTRAP_CONFIG_DIR ?=

# Default config-dir for local workflows (repo `.skein` when not explicitly overridden).
CONFIG_DIR := $(if $(strip $(BOOTSTRAP_CONFIG_DIR)),$(BOOTSTRAP_CONFIG_DIR),$(CURDIR)/.skein)
CONFIG_FILE := $(CONFIG_DIR)/config.json
AGENTS_FILE := $(CONFIG_DIR)/AGENTS.md
CLAUDE_FILE := $(CONFIG_DIR)/CLAUDE.md

build:
	go build -o $(BIN) $(GO_CLI)
	go build -o $(MILL_BIN) $(MILL_CLI)

install:
	go install $(GO_CLI)
	go install $(MILL_CLI)

# Bootstrap matching mill-routed repo-first CLI flow: install strand/mill and run config-only `strand init`.
# Requires a running `mill start` in the current XDG state world.
bootstrap:
	go install $(GO_CLI)
	go install $(MILL_CLI)
	@mill status >/dev/null 2>&1 || { echo "make bootstrap requires a running mill; start one with: mill start" >&2; exit 1; }
	@if [ -n "$(BOOTSTRAP_CONFIG_DIR)" ]; then \
		strand --config-dir "$(BOOTSTRAP_CONFIG_DIR)" init --source "$(CURDIR)"; \
	else \
		strand init --source "$(CURDIR)"; \
	fi
	@if [ ! -e "$(AGENTS_FILE)" ] && [ ! -L "$(AGENTS_FILE)" ]; then \
		printf '%s\n' 'Always read $(CURDIR)/docs/skein.md from repo root before running commands.' > "$(AGENTS_FILE)"; \
	fi
	@if [ ! -e "$(CLAUDE_FILE)" ] && [ ! -L "$(CLAUDE_FILE)" ]; then \
		ln -sf AGENTS.md "$(CLAUDE_FILE)"; \
	fi

open-config: bootstrap
	@if [ -z "$(EDITOR)" ]; then \
		echo "EDITOR is not set" >&2; \
		exit 1; \
	fi
	$(EDITOR) "$(CONFIG_FILE)"
