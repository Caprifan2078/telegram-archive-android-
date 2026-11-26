# Local analyst model upgrade (QA)

Runtime: LiteRT-LM Android 0.18.0, explicit opt-in QA package, CPU 2 threads, 4096 context, 768 output tokens for questions and 512 for block scans. No new upload path; the existing digest-pinned model downloader is unchanged.
New pinned community conversions: Qwen3.5 0.8B INT8 (964566992 bytes), Qwen3.5 2B INT8 (2119744416 bytes, pre-external-weights revision), LFM2.5 2.6B INT4 (1668151680 bytes). Existing choices and custom file remain. Stable selection keys migrate the old custom index.
Sources:
- https://huggingface.co/litert-community/Qwen3.5-0.8B/commit/ff11c109e8e56797cd1ee5befa856975bb465afc
- https://huggingface.co/litert-community/Qwen3.5-2B/blob/895dd827bca539c532b359d9fec3b4fe512f8426/litertlm_manifest.json
- https://huggingface.co/litert-community/LFM2.5-2.6B/commit/74de0c552676734655e3d282070ffc6ea78cda5d
- https://github.com/google-ai-edge/LiteRT-LM/tree/v0.18.0
Known limitations: mixed48 0.8B requires 0.20 or nightly and is not offered here; GPU not enabled; real model RAM/latency/Russian answer quality not measured. Hybrid state issue (#3165) means a new engine per full-scan block, potentially expensive; old Qwen3 remains suitable for long scans. Conservative free-memory check is an estimate, not a guarantee. Emulator regression does not establish actual native inference with multi-GB weights; test physical device separately.
