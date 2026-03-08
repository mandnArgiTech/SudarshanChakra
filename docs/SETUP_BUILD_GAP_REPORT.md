# setup_and_build_all.sh — Gap Report

**Run date:** 2026-03-08  
**Environment:** Linux, Docker available, no NVIDIA GPU, Node 18.19.1

---

## Summary

| Step | Status | Notes |
|------|--------|------|
| 1. Required tooling | ✅ Pass (after fixes) | `npm` and `rg` were missing — installed via `apt install npm ripgrep` |
| 2. GPU / Docker | ⚠️ Warning | `nvidia-smi` not found (expected without GPU). Docker OK. |
| 3. Python venv | ✅ Pass (after fix) | `python3.12-venv` was missing — installed via `apt install python3.12-venv` |
| 4. Dashboard deps | ✅ Pass | `npm install` succeeded. Warnings: Node 18 vs required ^20/^22; deprecated deps; 4 moderate audit issues |
| 5. Start infra | ✅ Pass | PostgreSQL and RabbitMQ containers started; images pulled |
| 6. Wait for infra | ✅ Pass (after fix) | RabbitMQ starts with `RABBITMQ_ERLANG_COOKIE` + named volume |
| 7+ | Not reached | RabbitMQ topology init, backend build, dashboard build, edge build, etc. |

---

## Gaps Identified

### 1. Missing system packages (fixable on first run)

- **npm** — not in PATH or not installed. Install: `apt install npm`.
- **rg (ripgrep)** — required by script for container checks. Install: `apt install ripgrep`.
- **python3.12-venv** — venv creation failed on Debian/Ubuntu without it. Install: `apt install python3.12-venv`.

### 2. RabbitMQ container crash — **FIXED**

- **Error:** `Error when reading /var/lib/rabbitmq/.erlang.cookie: eacces`
- **Cause:** RabbitMQ cannot read its cookie file (permission denied). Fix: set `RABBITMQ_ERLANG_COOKIE` in env and use named volume `rabbitmq_data:/var/lib/rabbitmq` (done in script).
- **Impact:** RabbitMQ now starts successfully; script can proceed past Step 6.

### 3. Node version (non-blocking)

- Dashboard and some deps expect Node **^20.19.0 || ^22.13.0 || >=24**; current is **v18.19.1**.
- `npm install` and Step 4 completed, but you may see engine warnings and future build/lint issues. Prefer Node 20+ or 22+ where possible.

### 4. No NVIDIA GPU

- `nvidia-smi` not found. Script continues with a warning. Edge AI inference will need a host with NVIDIA drivers and GPU for production.

### 5. Android / Firmware

- Run used `SKIP_ANDROID=1`. With `SKIP_ANDROID=0`, script requires `ANDROID_HOME` set and valid Android SDK.
- Firmware step requires `arduino-cli`; script skips with a warning if not found.

---

## Recommended next steps

1. **Fix RabbitMQ startup** (required for full script success):  
   Use a named volume for `/var/lib/rabbitmq` and/or fix permissions (and user) for any bind-mounted data dir so RabbitMQ can read `.erlang.cookie`.
2. **Optionally** add a preflight check in the script for `npm`, `rg`, and `python3 -m venv` (or `python3.12-venv`), with a clear message to install the missing package.
3. **Optionally** document in README or DEVELOPER_GUIDE: required system packages (`npm`, `ripgrep`, `python3.12-venv`) and recommended Node version (20+).
4. Re-run with RabbitMQ fixed:  
   `SKIP_ANDROID=1 ./setup_and_build_all.sh`  
   to verify backend build, dashboard build, edge validation, and AlertManagement checks.
