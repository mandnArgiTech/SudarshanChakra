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
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class DeviceService {

    private final EdgeNodeRepository edgeNodeRepository;
    private final CameraRepository cameraRepository;
    private final ZoneRepository zoneRepository;
    private final WorkerTagRepository workerTagRepository;
    private final ObjectProvider<RabbitTemplate> rabbitTemplate;

    public DeviceService(EdgeNodeRepository edgeNodeRepository,
                         CameraRepository cameraRepository,
                         ZoneRepository zoneRepository,
                         WorkerTagRepository workerTagRepository,
                         ObjectProvider<RabbitTemplate> rabbitTemplate) {
        this.edgeNodeRepository = edgeNodeRepository;
        this.cameraRepository = cameraRepository;
        this.zoneRepository = zoneRepository;
        this.workerTagRepository = workerTagRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

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
        Zone saved = zoneRepository.save(zone);
        publishZoneReload("zone_created", saved.getId(), saved.getCameraId());
        return saved;
    }

    @Transactional
    public void deleteZone(String id) {
        Zone zone = zoneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Zone not found: " + id));
        String cameraId = zone.getCameraId();
        zoneRepository.deleteById(id);
        log.info("Deleted zone: {}", id);
        publishZoneReload("zone_deleted", id, cameraId);
    }

    private void publishZoneReload(String event, String zoneId, String cameraId) {
        RabbitTemplate rt = rabbitTemplate.getIfAvailable();
        if (rt == null) {
            log.warn("RabbitMQ not available — zone reload not published for {}", zoneId);
            return;
        }
        String payload = String.format(
                "{\"event\":\"%s\",\"zone_id\":\"%s\",\"camera_id\":\"%s\"}",
                event, zoneId, cameraId);
        // farm.events matches mqtt.exchange in rabbitmq.conf so MQTT edge clients receive this
        // as topic farm/admin/reload_config (routing key uses dots).
        rt.convertAndSend("farm.events", "farm.admin.reload_config", payload);
        log.info(
                "Zone reload published → exchange farm.events key farm.admin.reload_config (MQTT farm/admin/reload_config): {}",
                payload);
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
