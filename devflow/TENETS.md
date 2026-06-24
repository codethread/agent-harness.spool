# Project Tenets

- **TEN-001**: This is alpha software.
  - All apis, contracts, db schemas, are subject to change. Changes can and should drop old ideas without migration plans should a better approach be presented.
- **TEN-001**: This is primarily an LLM coding agent tool.
  - All apis should favour their consumption (raw informative structure data over pretty ascii and layouts).
  - The apis exposed should offer flexibility over invariants
- **TEN-002**: Agents are trusted to not abuse their power.
  - In giving agents this tool, we expose helper functions along with clear guidance, such that they will use the 'blessed path' and not corrupt data or remove userland invariants without their user's sign-off. This allows us to keep the system malleable and flexible, without the need to constrain every possible misuse of the system
