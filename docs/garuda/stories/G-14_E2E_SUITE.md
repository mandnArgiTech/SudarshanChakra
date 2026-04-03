# G-14: E2E Test Suite Execution

Implement tests from `docs/E2E_REAL_HARDWARE_TEST_PLAN.md`.

## Files to CREATE
```
e2e/
├── playwright/
│   ├── playwright.config.ts
│   ├── helpers/login.ts
│   ├── cameras.spec.ts          (Suite 3: 7 tests)
│   ├── ptz.spec.ts              (Suite 4: 8 tests)
│   ├── zones.spec.ts            (Suite 5: 6 tests)
│   ├── alerts.spec.ts           (Suite 6: 10 tests)
│   ├── siren.spec.ts            (Suite 7: 5 tests)
│   ├── water.spec.ts            (Suite 8: 8 tests)
│   └── video.spec.ts            (Suite 10: 6 tests)
├── maestro/flows/
│   ├── server_config.yaml
│   ├── login.yaml
│   ├── alert_notification.yaml
│   ├── water_check.yaml
│   └── full_android_flow.yaml   (Suite 11: 8 tests)
├── tests/
│   ├── test_01_health.py        (Suite 1: 8 tests)
│   ├── test_02_auth.py          (Suite 2: 6 tests)
│   └── test_09_pump.py          (Suite 9: 4 tests)
├── helpers/
│   ├── mqtt_helper.py
│   ├── onvif_verify.py
│   └── audio_verify.py
└── run_full_e2e.sh
```

Refer to `docs/E2E_REAL_HARDWARE_TEST_PLAN.md` for exact test definitions per suite.

## Verification
```bash
python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml  # All green first
./e2e/run_full_e2e.sh --config e2e/config/e2e_config.yml
# Expected: 68 tests across 11 suites
```

---

