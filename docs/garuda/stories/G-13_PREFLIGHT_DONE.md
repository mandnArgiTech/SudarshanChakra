# G-13: E2E Preflight checker — DONE

## Status

Deliverable is **[`e2e/preflight_check.py`](../../../e2e/preflight_check.py)** (eight check groups). Run it **before** the G-14 E2E suite so misconfigured hosts, missing tools, or down services fail in about a minute instead of deep into Playwright/Maestro runs. See also **[G-14_E2E_SUITE.md](G-14_E2E_SUITE.md)**.

## How to run

From the **repository root**:

```bash
python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml
```

Copy **[`e2e/config/e2e_config.example.yml`](../../../e2e/config/e2e_config.example.yml)** to `e2e/config/e2e_config.yml`, fill IPs and credentials, and **do not commit** secrets (keep the real file untracked or use farm-local paths).

### CLI flags

| Flag | Purpose |
|------|---------|
| `--config` | **Required.** Path to YAML config. |
| `--json` | Machine-readable report on stdout. |
| `--wait-for-water` | When `water.level_topic` is set, wait up to ~90s for an MQTT publish on that topic. |
| `--fix` | Accepted by the parser (see script); use failure output **`fix_hint`** lines for remediation. |

### Exit codes

| Code | Meaning |
|------|---------|
| 0 | All checks passed. |
| 1 | One or more checks failed. |
| 2 | Config file missing (script exits before the full report). |

Parse/load errors for a present but invalid file may surface as exceptions or failed checks depending on content.

## Dependencies (Group 1)

Install **PyYAML** and **paho-mqtt** for a normal workflow: **`pip install pyyaml paho-mqtt`** (or your e2e venv). **YAML configs require PyYAML**; without it the loader is not reliable for `.yml` content.

Other tools exercised include **docker**, **node** / **npx**, **ffprobe**, **adb**, **maestro** (see script for exact checks).

## Check groups (8)

| # | Group | What it validates |
|---|--------|-------------------|
| 1 | tools | Python 3.10+, PyYAML, paho-mqtt, docker, node, npx, Playwright-related presence, ffprobe |
| 2 | vm1 | API gateway :8080, `/actuator/health`, dashboard URL, PostgreSQL :5432, RabbitMQ :5672 / MQTT, broker connect, **auth login** (`auth.admin_user` / `admin_pass` required) |
| 3 | vm2 | Edge host :5000, Flask `/health`, `/api/cameras/status` |
| 4 | cameras* | Per-camera RTSP, ONVIF, snapshot via Edge — **skipped** if `edge.cameras` is empty |
| 5 | water* | ESP8266 HTTP/MQTT status — **skipped** if `water.esp8266_ip` is empty; optional MQTT level wait with `--wait-for-water` + `level_topic` |
| 6 | siren | ALSA audio device (lab hardware) |
| 7 | android | adb, emulator, debug APK path, Maestro CLI |
| 8 | resources | Disk space, Docker daemon |

\* **Conditional groups.** Total check count is **config-dependent**; “35+ checks” applies when the example-style config includes cameras and water.

## Environment expectations

Preflight targets an **E2E lab / farm** topology (reachable VM1/VM2, optional real cameras, ESP, audio, Android). It will **often fail** on a minimal developer laptop (no emulator, no ALSA, localhost-only URLs pointing at nothing)—that is expected.

**Security note:** HTTP checks use **TLS verification disabled** for convenience against lab URLs. Do not assume the same pattern is appropriate for production endpoints.

## Non-goals

- **Default GitHub Actions:** not wired here; full preflight needs farm networking and hardware. A future story could add a self-hosted runner or `workflow_dispatch` with a minimal config.
- **Playwright “installed” probe:** Group 1 checks **npx** presence twice with different labels; it does not run `npx playwright --version` (acceptable limitation).

## Verification

```bash
python3 e2e/preflight_check.py --config e2e/config/e2e_config.yml
```

All green is **environment-dependent**. Minimum sanity: script starts and prints all eight group headers.

## Garuda checklist

**Preflight checker (G-13)** — see [GARUDA_CHECKLIST.md](../GARUDA_CHECKLIST.md) (E2E TESTING).
