/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Edge Flask base URL for /api/snapshot/<camera_id> (e.g. http://192.168.1.10:5000) */
  readonly VITE_EDGE_SNAPSHOT_BASE?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
