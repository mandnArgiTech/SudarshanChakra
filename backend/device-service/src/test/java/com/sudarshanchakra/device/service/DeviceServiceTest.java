package com.sudarshanchakra.device.service;

import com.sudarshanchakra.device.model.Camera;
import com.sudarshanchakra.device.model.EdgeNode;
import com.sudarshanchakra.device.model.WorkerTag;
import com.sudarshanchakra.device.model.Zone;
import com.sudarshanchakra.device.repository.CameraRepository;
import com.sudarshanchakra.device.repository.EdgeNodeRepository;
import com.sudarshanchakra.device.repository.WorkerTagRepository;
import com.sudarshanchakra.device.repository.ZoneRepository;
import com.sudarshanchakra.jwt.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeviceServiceTest {

    @Mock
    EdgeNodeRepository edgeNodeRepository;
    @Mock
    CameraRepository cameraRepository;
    @Mock
    ZoneRepository zoneRepository;
    @Mock
    WorkerTagRepository workerTagRepository;
    @Mock
    ObjectProvider<RabbitTemplate> rabbitTemplateProvider;

    DeviceService deviceService;

    UUID farmId;
    EdgeNode node;

    @BeforeEach
    void setUp() {
        farmId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        node = EdgeNode.builder().id("e1").farmId(farmId).displayName("E1").build();
        TenantContext.set(farmId, false);
        deviceService = new DeviceService(
                edgeNodeRepository, cameraRepository, zoneRepository,
                workerTagRepository, rabbitTemplateProvider);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void getNodeById_found() {
        when(edgeNodeRepository.findById("e1")).thenReturn(Optional.of(node));
        assertThat(deviceService.getNodeById("e1").getDisplayName()).isEqualTo("E1");
    }

    @Test
    void getNodeById_missing() {
        when(edgeNodeRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.getNodeById("x"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createNode_success() {
        when(edgeNodeRepository.existsById("e2")).thenReturn(false);
        when(edgeNodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        EdgeNode n = EdgeNode.builder().id("e2").farmId(farmId).build();
        assertThat(deviceService.createNode(n).getId()).isEqualTo("e2");
    }

    @Test
    void createNode_duplicate() {
        when(edgeNodeRepository.existsById("e1")).thenReturn(true);
        assertThatThrownBy(() -> deviceService.createNode(node))
                .hasMessageContaining("already exists");
    }

    @Test
    void updateNode_changesFields() {
        when(edgeNodeRepository.findById("e1")).thenReturn(Optional.of(node));
        when(edgeNodeRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        EdgeNode upd = EdgeNode.builder().displayName("New").vpnIp("10.0.0.1").build();
        EdgeNode out = deviceService.updateNode("e1", upd);
        assertThat(out.getDisplayName()).isEqualTo("New");
        assertThat(out.getVpnIp()).isEqualTo("10.0.0.1");
    }

    @Test
    void getAllNodes() {
        when(edgeNodeRepository.findAll()).thenReturn(List.of(node));
        assertThat(deviceService.getAllNodes()).hasSize(1);
    }

    @Test
    void createCamera_success() {
        Camera c = Camera.builder().id("cam-1").nodeId("e1").name("C").rtspUrl("rtsp://x").build();
        when(edgeNodeRepository.findById("e1")).thenReturn(Optional.of(node));
        when(cameraRepository.existsById("cam-1")).thenReturn(false);
        when(cameraRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(deviceService.createCamera(c).getId()).isEqualTo("cam-1");
    }

    @Test
    void getCameraById_missing() {
        when(cameraRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.getCameraById("x"))
                .hasMessageContaining("Camera not found");
    }

    @Test
    void createZone_success() {
        Zone z = Zone.builder()
                .id("z1")
                .cameraId("cam-1")
                .name("Z")
                .zoneType("line")
                .priority("high")
                .polygon("{}")
                .build();
        Camera cam = Camera.builder().id("cam-1").nodeId("e1").name("C").rtspUrl("rtsp://x").build();
        when(cameraRepository.findById("cam-1")).thenReturn(Optional.of(cam));
        when(edgeNodeRepository.findById("e1")).thenReturn(Optional.of(node));
        when(zoneRepository.existsById("z1")).thenReturn(false);
        when(zoneRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(deviceService.createZone(z).getId()).isEqualTo("z1");
    }

    @Test
    void deleteZone_success() {
        Zone z = Zone.builder().id("z1").cameraId("cam-1").name("Z").zoneType("line").priority("high").polygon("{}").build();
        when(zoneRepository.findById("z1")).thenReturn(Optional.of(z));
        deviceService.deleteZone("z1");
        verify(zoneRepository).deleteById("z1");
    }

    @Test
    void deleteZone_missing() {
        when(zoneRepository.findById("z1")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.deleteZone("z1"))
                .hasMessageContaining("not found");
    }

    @Test
    void createTag_success() {
        WorkerTag t = WorkerTag.builder().tagId("T1").farmId(farmId).workerName("W").build();
        when(workerTagRepository.existsById("T1")).thenReturn(false);
        when(workerTagRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        assertThat(deviceService.createTag(t).getTagId()).isEqualTo("T1");
    }

    @Test
    void getTagById_missing() {
        when(workerTagRepository.findById("x")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> deviceService.getTagById("x"))
                .hasMessageContaining("Worker tag not found");
    }
}
