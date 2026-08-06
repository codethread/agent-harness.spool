.PHONY: test fmt-check lint lint-clj lint-splint lint-conventions reflect-check quality identity-check release-check

test:
	clojure -M:test
	test/verify-release_test.sh

fmt-check:
	clojure -M:format

lint: lint-clj lint-splint lint-conventions

lint-clj:
	clojure -M:lint/clj-kondo

lint-splint:
	clojure -M:lint/splint

lint-conventions:
	clojure -M:lint/conventions

reflect-check:
	clojure -M:reflect-check

quality: fmt-check lint reflect-check test

identity-check:
	bin/identity-check

release-check:
	bin/verify-release --mode pre-tag --source-root "$(CURDIR)" \
		--core-release release/msr04-release.json \
		--kanban-release release/msr05-release.json
