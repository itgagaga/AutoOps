# Third-party source

`agentscope-java-main/` contains a snapshot of [AgentScope Java](https://github.com/agentscope-ai/agentscope-java), distributed under the Apache License 2.0 license. Its license text is included at [`agentscope-java-main/LICENSE`](agentscope-java-main/LICENSE). Retain upstream copyright and license headers when modifying or redistributing this directory.

This notice covers the bundled third-party source only. The license for the AutoOps code must be selected by its rights holder before public release.

Local changes to the Builder and DataAgent examples remove bundled database connection values and JWT signing-secret defaults, disable automatic demo-account seeding, and load credentials from ignored private YAML files. Blank `.example` templates are provided alongside the example applications.
