# Garuda Release — Agent Instructions for Cursor

## Execution Order (MANDATORY)

```
WS-1: G-01 → G-02 → G-03 (done) → G-04 → G-05
WS-2: G-06 → G-07 → G-08 → G-09
WS-3: G-10 → G-11 → G-12
WS-4: M-01 → M-02 → ... → M-12  (see docs/mdm/MDM_AGENT_INSTRUCTIONS.md)
WS-5: G-13 (done) → G-14
WS-6: G-15 → G-16
WS-7: G-17 → G-18
```

## Rules

1. Read the story file COMPLETELY before writing any code.
2. Each story lists exact files to create and modify. Do NOT touch unlisted files.
3. After each story, run the verification commands at the bottom.
4. After completing a story, update `docs/garuda/GARUDA_CHECKLIST.md`.
5. For MDM stories (M-01 to M-12), follow `docs/mdm/MDM_AGENT_INSTRUCTIONS.md`.

## Story Files — One Per Story

### WS-1: SaaS Gap Closure
```
docs/garuda/stories/G-01_TENANT_FILTER.md
docs/garuda/stories/G-02_RBAC_PREAUTHORIZE.md
docs/garuda/stories/G-03_SIDEBAR_FILTER.md          ← ALREADY DONE
docs/garuda/stories/G-04_AUDIT_ASPECT.md
docs/garuda/stories/G-05_SAAS_TESTS.md
```

### WS-2: Camera/Video Gap Closure
```
docs/garuda/stories/G-06_EXOPLAYER_DEP.md
docs/garuda/stories/G-07_ZONE_MQTT_SYNC.md
docs/garuda/stories/G-08_CAMERA_SYNC.md
docs/garuda/stories/G-09_ALERT_VIDEO_CLIP_DASHBOARD.md
```

### WS-3: Android Production Polish
```
docs/garuda/stories/G-10_PULL_TO_REFRESH.md
docs/garuda/stories/G-11_ANDROID_ALERT_CLIP.md
docs/garuda/stories/G-12_ANDROID_TESTS.md
```

### WS-4: MDM Kiosk Module
```
docs/mdm/stories/STORY_01_DB_MIGRATION.md
docs/mdm/stories/STORY_02_SERVICE_SKELETON.md
docs/mdm/stories/STORY_03_TELEMETRY_API.md
docs/mdm/stories/STORY_04_05_COMMANDS_OTA.md
docs/mdm/stories/STORY_06_07_08_ANDROID_TELEMETRY.md
docs/mdm/stories/STORY_09_10_ANDROID_KIOSK_OTA.md
docs/mdm/stories/STORY_11_DASHBOARD_PAGES.md
docs/mdm/stories/STORY_12_TESTS_DOCKER.md
```

### WS-5: E2E Testing
```
docs/garuda/stories/G-13_PREFLIGHT_DONE.md           ← ALREADY DONE
docs/garuda/stories/G-14_E2E_SUITE.md
```

### WS-6: Infrastructure
```
docs/garuda/stories/G-15_OPENVPN.md
docs/garuda/stories/G-16_TLS_HTTPS.md
```

### WS-7: Build & Release
```
docs/garuda/stories/G-17_GHCR_PUSH.md
docs/garuda/stories/G-18_DEPLOY_PROFILES.md
```
