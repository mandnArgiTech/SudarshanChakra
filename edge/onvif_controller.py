#!/usr/bin/env python3
"""
onvif_controller.py — ONVIF PTZ Camera Controller
=====================================================
Wraps ``onvif-zeep`` to provide pan/tilt/zoom control and camera
capability discovery for ONVIF-compliant cameras (TP-Link VIGI,
Tapo with ONVIF enabled, etc.).

Thread-safe: all methods lock around the zeep client, which is
not thread-safe by default.
"""

import logging
import threading
from typing import Dict, List, Optional

log = logging.getLogger("onvif_controller")

try:
    from onvif import ONVIFCamera
    ONVIF_AVAILABLE = True
except ImportError:
    ONVIF_AVAILABLE = False
    log.warning("onvif-zeep not installed — PTZ control disabled. "
                "Install with: pip install onvif-zeep")


class OnvifController:
    """Controls a single ONVIF camera (PTZ + device info)."""

    def __init__(self, host: str, port: int = 80,
                 user: str = "admin", password: str = ""):
        self.host = host
        self.port = port
        self._lock = threading.Lock()
        self._cam = None
        self._ptz = None
        self._media = None
        self._profile_token = None
        self._has_ptz = False
        self._device_info: Dict = {}
        self._connected = False

        if not ONVIF_AVAILABLE:
            return

        try:
            self._cam = ONVIFCamera(host, port, user, password)
            self._media = self._cam.create_media_service()
            profiles = self._media.GetProfiles()
            if profiles:
                self._profile_token = profiles[0].token

            try:
                self._ptz = self._cam.create_ptz_service()
                self._has_ptz = True
            except Exception:
                self._has_ptz = False

            try:
                info = self._cam.devicemgmt.GetDeviceInformation()
                self._device_info = {
                    "manufacturer": getattr(info, "Manufacturer", ""),
                    "model": getattr(info, "Model", ""),
                    "firmware": getattr(info, "FirmwareVersion", ""),
                    "serial": getattr(info, "SerialNumber", ""),
                    "hardware_id": getattr(info, "HardwareId", ""),
                }
            except Exception:
                pass

            self._connected = True
            log.info("ONVIF connected to %s:%d (PTZ=%s, model=%s)",
                     host, port, self._has_ptz,
                     self._device_info.get("model", "unknown"))
        except Exception as e:
            log.error("ONVIF connection failed for %s:%d: %s", host, port, e)

    def get_capabilities(self) -> dict:
        if not self._connected:
            return {"supported": False, "error": "Not connected"}

        result = {
            "supported": True,
            "has_ptz": self._has_ptz,
            "host": self.host,
            "device_info": self._device_info,
        }

        if self._has_ptz and self._ptz and self._profile_token:
            try:
                with self._lock:
                    node = self._ptz.GetNodes()[0] if self._ptz.GetNodes() else None
                if node:
                    spaces = node.SupportedPTZSpaces
                    result["can_pan"] = bool(
                        getattr(spaces, "ContinuousPanTiltVelocitySpace", None))
                    result["can_tilt"] = result["can_pan"]
                    result["can_zoom"] = bool(
                        getattr(spaces, "ContinuousZoomVelocitySpace", None))
            except Exception as e:
                log.debug("PTZ node query failed: %s", e)
                result["can_pan"] = True
                result["can_tilt"] = True
                result["can_zoom"] = True

        return result

    def continuous_move(self, pan: float = 0, tilt: float = 0,
                        zoom: float = 0) -> bool:
        if not self._has_ptz or not self._ptz:
            return False
        try:
            with self._lock:
                req = self._ptz.create_type("ContinuousMove")
                req.ProfileToken = self._profile_token
                req.Velocity = {
                    "PanTilt": {"x": pan, "y": tilt},
                    "Zoom": {"x": zoom},
                }
                self._ptz.ContinuousMove(req)
            return True
        except Exception as e:
            log.error("ContinuousMove failed: %s", e)
            return False

    def absolute_move(self, x: float = 0, y: float = 0,
                      z: float = 0) -> bool:
        if not self._has_ptz or not self._ptz:
            return False
        try:
            with self._lock:
                req = self._ptz.create_type("AbsoluteMove")
                req.ProfileToken = self._profile_token
                req.Position = {
                    "PanTilt": {"x": x, "y": y},
                    "Zoom": {"x": z},
                }
                self._ptz.AbsoluteMove(req)
            return True
        except Exception as e:
            log.error("AbsoluteMove failed: %s", e)
            return False

    def stop(self) -> bool:
        if not self._has_ptz or not self._ptz:
            return False
        try:
            with self._lock:
                self._ptz.Stop({
                    "ProfileToken": self._profile_token,
                    "PanTilt": True,
                    "Zoom": True,
                })
            return True
        except Exception as e:
            log.error("Stop failed: %s", e)
            return False

    def get_presets(self) -> List[dict]:
        if not self._has_ptz or not self._ptz:
            return []
        try:
            with self._lock:
                presets = self._ptz.GetPresets(
                    {"ProfileToken": self._profile_token})
            return [
                {
                    "token": str(p.token),
                    "name": getattr(p, "Name", f"Preset {p.token}"),
                }
                for p in (presets or [])
            ]
        except Exception as e:
            log.error("GetPresets failed: %s", e)
            return []

    def goto_preset(self, token: str) -> bool:
        if not self._has_ptz or not self._ptz:
            return False
        try:
            with self._lock:
                self._ptz.GotoPreset({
                    "ProfileToken": self._profile_token,
                    "PresetToken": token,
                })
            return True
        except Exception as e:
            log.error("GotoPreset failed: %s", e)
            return False

    def set_preset(self, name: str) -> Optional[str]:
        """Save current position as a named preset. Returns token or None."""
        if not self._has_ptz or not self._ptz:
            return None
        try:
            with self._lock:
                result = self._ptz.SetPreset({
                    "ProfileToken": self._profile_token,
                    "PresetName": name,
                })
            token = str(result) if result else None
            log.info("Preset saved: %s = %s", name, token)
            return token
        except Exception as e:
            log.error("SetPreset failed: %s", e)
            return None


class OnvifManager:
    """
    Manages OnvifController instances for multiple cameras.
    Keyed by camera_id; ONVIF config comes from camera config JSON.
    """

    def __init__(self):
        self._controllers: Dict[str, OnvifController] = {}

    def register(self, camera_id: str, onvif_host: str,
                 onvif_port: int = 80, onvif_user: str = "admin",
                 onvif_pass: str = ""):
        if camera_id in self._controllers:
            return
        ctrl = OnvifController(onvif_host, onvif_port, onvif_user, onvif_pass)
        self._controllers[camera_id] = ctrl

    def get(self, camera_id: str) -> Optional[OnvifController]:
        return self._controllers.get(camera_id)

    def list_cameras(self) -> List[str]:
        return list(self._controllers.keys())
