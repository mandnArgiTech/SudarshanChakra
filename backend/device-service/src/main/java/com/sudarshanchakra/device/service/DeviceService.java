package com.sudarshanchakra.device.service;

import com.sudarshanchakra.device.model.Camera;
import com.sudarshanchakra.device.model.EdgeNode;
import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.model.Zone;
import com.sudarshanchakra.jwt.TenantContext;
import com.sudarshanchakra.device.repository.CameraRepository;
import com.sudarshanchakra.device.repository.EdgeNodeRepository;
import com.sudarshanchakra.device.repository.WorkerTagRepository;
import com.sudarshanchakra.device.repository.ZoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final CameraRepository cameraRepository;
    private final ZoneRepository zoneRepository;
    private final WorkerTagRepository workerTagRepository;

    // Edge Nodes
    public List<EdgeNode> getAllNodes() {
        return edgeNodeRepository.findAll();
    }

    public EdgeNode getNodeById(String id) {
        return edgeNodeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Edge node not found: " + id));
    }

    @Transactional
    public EdgeNode createNode(EdgeNode node) {
        if (edgeNodeRepository.existsById(node.getId())) {
            throw new IllegalArgumentException("Edge node already exists: " + node.getId());
        }
        applyTenantFarmIdToNode(node);
        log.info("Creating edge node: {}", node.getId());
        return edgeNodeRepository.save(node);
    }

    @Transactional
    public EdgeNode updateNode(String id, EdgeNode updates) {
        EdgeNode node = getNodeById(id);
        if (updates.getDisplayName() != null) node.setDisplayName(updates.getDisplayName());
        if (updates.getVpnIp() != null) node.setVpnIp(updates.getVpnIp());
        if (updates.getLocalIp() != null) node.setLocalIp(updates.getLocalIp());
        if (updates.getStatus() != null) node.setStatus(updates.getStatus());
        if (updates.getHardwareInfo() != null) node.setHardwareInfo(updates.getHardwareInfo());
        if (updates.getConfig() != null) node.setConfig(updates.getConfig());
        return edgeNodeRepository.save(node);
    }

    // Cameras
    public List<Camera> getAllCameras() {
        return cameraRepository.findAll();
    }

    public List<Camera> getCamerasByNodeId(String nodeId) {
        return cameraRepository.findByNodeId(nodeId);
    }

    public Camera getCameraById(String id) {
        return cameraRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Camera not found: " + id));
    }

    @Transactional
    public Camera createCamera(Camera camera) {
        if (cameraRepository.existsById(camera.getId())) {
            throw new IllegalArgumentException("Camera already exists: " + camera.getId());
        }
        verifyNodeInTenant(camera.getNodeId());
        log.info("Creating camera: {}", camera.getId());
        return cameraRepository.save(camera);
    }

    // Zones
    public List<Zone> getAllZones() {
        return zoneRepository.findAll();
    }

    public List<Zone> getZonesByCameraId(String cameraId) {
        return zoneRepository.findByCameraId(cameraId);
    }

    public Zone getZoneById(String id) {
        return zoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + id));
    }

    @Transactional
    public Zone createZone(Zone zone) {
        if (zoneRepository.existsById(zone.getId())) {
            throw new IllegalArgumentException("Zone already exists: " + zone.getId());
        }
        Camera cam = cameraRepository.findById(zone.getCameraId())
                .orElseThrow(() -> new IllegalArgumentException("Camera not found: " + zone.getCameraId()));
        verifyNodeInTenant(cam.getNodeId());
        log.info("Creating zone: {}", zone.getId());
        return zoneRepository.save(zone);
    }

    @Transactional
    public void deleteZone(String id) {
        if (!zoneRepository.existsById(id)) {
            throw new IllegalArgumentException("Zone not found: " + id);
        }
        zoneRepository.deleteById(id);
        log.info("Deleted zone: {}", id);
    }

    // Worker Tags
    public List<WorkerTag> getAllTags() {
        return workerTagRepository.findAll();
    }

    public WorkerTag getTagById(String tagId) {
        return workerTagRepository.findById(tagId)
                .orElseThrow(() -> new IllegalArgumentException("Worker tag not found: " + tagId));
    }

    @Transactional
    public WorkerTag createTag(WorkerTag tag) {
        if (workerTagRepository.existsById(tag.getTagId())) {
            throw new IllegalArgumentException("Worker tag already exists: " + tag.getTagId());
        }
        if (!TenantContext.isSuperAdmin()) {
            UUID farmId = TenantContext.getFarmId();
            if (farmId == null) {
                throw new IllegalStateException("Missing tenant context");
            }
            tag.setFarmId(farmId);
        } else if (tag.getFarmId() == null) {
            throw new IllegalArgumentException("farmId is required for platform operator");
        }
        log.info("Creating worker tag: {}", tag.getTagId());
        return workerTagRepository.save(tag);
    }

    private void applyTenantFarmIdToNode(EdgeNode node) {
        if (!TenantContext.isSuperAdmin()) {
            UUID farmId = TenantContext.getFarmId();
            if (farmId == null) {
                throw new IllegalStateException("Missing tenant context");
            }
            node.setFarmId(farmId);
        } else if (node.getFarmId() == null) {
            throw new IllegalArgumentException("farmId is required for platform operator");
        }
    }

    private void verifyNodeInTenant(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId is required");
        }
        if (TenantContext.isSuperAdmin()) {
            return;
        }
        EdgeNode n = edgeNodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalArgumentException("Edge node not found: " + nodeId));
        UUID ctxFarm = TenantContext.getFarmId();
        if (ctxFarm == null || !ctxFarm.equals(n.getFarmId())) {
            throw new IllegalArgumentException("Edge node not in tenant scope: " + nodeId);
        }
    }
}
