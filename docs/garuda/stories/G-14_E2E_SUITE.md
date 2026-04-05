# G-14: E2E test suite execution

## Status (implementation landed)

This story wires the **orchestrator**, **pytest** modules (Suites 1, 2, API smoke, 9, optional hardware), **Playwright** package (Suites 3–6, 7–8 UI smoke, 10), **Maestro** flow scaffolds (Suite 11), and **Python helpers** aligned with [docs/E2E_REAL_HARDWARE_TEST_PLAN.md](../../E2E_REAL_HARDWARE_TEST_PLAN.md).

**68/68 strict assertions** require a **farm/lab** (real cameras, ESP, ALSA, emulator). CI and laptops typically run **subset tiers** with **skips** — see below.

## Layout (created / updated)

```
e2e/
├── run_full_e2e.sh          # preflight → pytest → Playwright → Maestro (optional)
├── run_e2e.sh               # legacy docker hint + exec run_full_e2e.sh
├── requirements.txt         # pytest, PyYAML, paho-mqtt (websocket-client optional)
├── conftest.py
├── test_full_stack.py       # legacy launcher → pytest e2e/tests
├── preflight_check.py       # (G-13)
├── config/e2e_config.example.yml
├── helpers/
│   ├── api_client.py        # HTTP + YAML load + TCP
│   ├── mqtt_helper.py
│   ├── onvif_verify.py      # optional onvif-zeep on farm runners
│   └── audio_verify.py      # arecord / RMS
├── tests/
│   ├── test_01_health.py    # Suite 1 (8)
│   ├── test_02_auth.py      # Suite 2 (6) — needs auth.* in e2e_config
│   ├── test_09_pump.py      # Suite 9 (API slice + skips)
│   ├── test_api_integration.py  # gateway CRUD smoke (ex test_full_stack)
│   └── test_hardware_gated.py   # E2E_RUN_HARDWARE=1
├── playwright/
│   ├── package.json
│   ├── playwright.config.ts
│   ├── helpers/login.ts
│   ├── cameras.spec.ts … video.spec.ts
│   └── .gitignore
└── maestro/flows/
    ├── server_config.yaml   # not executed by run_full_e2e.sh (documentation)
    ├── login.yaml
    ├── alert_notification.yaml
    ├── water_check.yaml
    └── full_android_flow.yaml
```

## Verification

```bash
pip install -r e2e/requirements.txt
# Copy e2e/config/e2e_config.example.yml → e2e/config/e2e_config.yml and fill IPs.

python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml   # farm: all green first

# API-only inner loop (no preflight):
PYTHONPATH=. E2E_API=http://127.0.0.1:8080 python3 -m pytest e2e/tests -v --tb=short

# Full orchestrator (from repo root):
./e2e/run_full_e2e.sh --config e2e/config/e2e_config.yml

# Options:
#   --skip-preflight | --skip-pytest | --skip-playwright | --skip-maestro
# Env: E2E_SKIP_PLAYWRIGHT=1  E2E_RUN_MAESTRO=1  E2E_RUN_HARDWARE=1  E2E_REAL_CAMERAS=1 …
```

**Playwright** reads `E2E_DASHBOARD_URL`, `E2E_ADMIN_USER`, `E2E_ADMIN_PASS` (exported from YAML by `run_full_e2e.sh` when PyYAML is installed).

**Maestro** runs only when **`E2E_RUN_MAESTRO=1`** and `maestro` is on `PATH` (avoids failing default `./e2e/run_full_e2e.sh` on dev machines without an emulator).

## Expected counts (honest)

| Tier | When | Outcome |
|------|------|---------|
| Pytest `e2e/tests` | Gateway down | almost all **skipped** |
| Pytest | Stack + `E2E_API` / config | Suite 1–2 + integration tests **run**; hardware tests skip unless env |
| Playwright | Creds + reachable dashboard | smoke tests **run**; farm-only tests **skipped** until env flags set |
| Maestro | `E2E_RUN_MAESTRO=1` + device | flows run (tune selectors to your Compose UI) |

## Garuda checklist

See [GARUDA_CHECKLIST.md](../GARUDA_CHECKLIST.md) — **Playwright** / **Maestro** scaffolds are marked done; **real camera / ESP / siren** rows stay open until farm verification.

## References

- [E2E_REAL_HARDWARE_TEST_PLAN.md](../../E2E_REAL_HARDWARE_TEST_PLAN.md)
- [G-13_PREFLIGHT_DONE.md](G-13_PREFLIGHT_DONE.md)
