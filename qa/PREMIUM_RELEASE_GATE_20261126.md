# Premium Viewer release candidate — 2026-11-26

Base: `48c092a414830994e1531483273afb7a5a20b786` (PR 33). Separate debug-signed QA application ID `org.openarchive.telegram.qa.premium20261126`, not an in-place upgrade. Stable archives untouched.

92 ordinary Android instrumentation classes selected across 16 suites on API 30 and 35. Exclusions: ArchivePipeTestProvider is a fixture; SignedUpgradeSentinelTest requires compatible signed seed/verify packages. JVM tests, lint, offline manifest, signature and SHA-256 gate. Exact published binary is the API35 media job APK; other jobs test the same source, not necessarily byte-identical debug-signed binaries. No physical-device validation or stable signing claimed.

Publication only after all jobs succeed. Existing isolated tests may expose remaining regressions; failing tests must be resolved, not suppressed.
