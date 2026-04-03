# Garuda Release — Agent Instructions for Cursor

## Execution Order

```
WS-1: G-01 → G-02 → G-03 → G-04 → G-05     (SaaS gaps, ~3 days)
WS-2: G-06 → G-07 → G-08 → G-09             (Camera gaps, ~2 days)
WS-3: G-10 → G-11 → G-12                     (Android polish, ~1.5 days)
WS-4: M-01 → M-02 → ... → M-12              (MDM, see docs/mdm/, ~7 days)
WS-5: G-13 (done) → G-14                     (E2E tests, ~2 days)
WS-6: G-15 → G-16                            (VPN/TLS, ~1 day)
WS-7: G-17 → G-18                            (Release packaging, ~1 day)
```

## Rules

1. Read the story COMPLETELY before writing any code.
2. Each story lists exact files to create and modify. Do NOT touch unlisted files.
3. Read the reference files listed — they show existing patterns to follow.
4. After each story, run the verification commands.
5. For MDM stories (M-01 to M-12), follow `docs/mdm/MDM_AGENT_INSTRUCTIONS.md`.
6. After completing a story, update `docs/garuda/GARUDA_CHECKLIST.md` — change `[ ]` to `[x]` for completed items.

## Story Locations

| Stories | Location |
|:--------|:---------|
| G-01 to G-18 | `docs/garuda/stories/GARUDA_STORIES.md` |
| M-01 to M-12 | `docs/mdm/stories/STORY_*.md` |

## Key Reference Files

| Purpose | File |
|:--------|:-----|
| JPA entity pattern | `backend/auth-service/.../model/Farm.java` |
| Controller pattern | `backend/auth-service/.../controller/FarmController.java` |
| Service pattern | `backend/auth-service/.../service/FarmService.java` |
| JWT filter | `backend/auth-service/.../config/JwtAuthFilter.java` |
| Security config | `backend/auth-service/.../config/SecurityConfig.java` |
| Tenant context | `backend/auth-service/.../context/TenantContext.java` |
| Module constants | `backend/auth-service/.../support/ModuleConstants.java` |
| Gateway routes | `backend/api-gateway/src/main/resources/application.yml` |
| Android Room DB | `android/app/src/main/java/.../data/db/AppDatabase.kt` |
| Android NavGraph | `android/app/src/main/java/.../ui/navigation/NavGraph.kt` |
| Android build deps | `android/app/build.gradle.kts` |
| Android manifest | `android/app/src/main/AndroidManifest.xml` |
| Dashboard sidebar | `dashboard/src/components/Layout/Sidebar.tsx` |
| Dashboard routes | `dashboard/src/App.tsx` |
| Dashboard mockups | `docs/mdm/mdm-dashboard-mockups.jsx` |
| Android mockups | `docs/mdm/mdm-android-mockups.jsx` |
| Camera mockups | `docs/mockups/camera-video-mockups.jsx` |
| SaaS mockups | `docs/mockups/saas-screens-mockup.jsx` |
