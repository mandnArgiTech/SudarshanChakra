package com.sudarshanchakra.device.service;

import com.sudarshanchakra.device.model.Camera;
import com.sudarshanchakra.device.model.EdgeNode;
import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.model.Zone;
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

    public List<WorkerTag> getTagsByFarmId(UUID farmId) {
        return workerTagRepository.findByFarmId(farmId);
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
        log.info("Creating worker tag: {}", tag.getTagId());
        return workerTagRepository.save(tag);
    }
}
