.PHONY: test fmt-check lint lint-clj lint-splint lint-conventions reflect-check quality

test:
	clojure -M:test

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
