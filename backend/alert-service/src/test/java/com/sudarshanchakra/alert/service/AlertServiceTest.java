package com.sudarshanchakra.alert.service;

import com.sudarshanchakra.alert.dto.AlertUpdateRequest;
import com.sudarshanchakra.alert.model.Alert;
import com.sudarshanchakra.alert.repository.AlertRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    AlertRepository alertRepository;

    @InjectMocks
    AlertService alertService;

    UUID id;
    Alert alert;

    @BeforeEach
    void setUp() {
        id = UUID.randomUUID();
        alert = Alert.builder()
                .id(id)
                .nodeId("n1")
                .cameraId("c1")
                .zoneId("z1")
                .zoneName("Z")
                .zoneType("intrusion")
                .priority("high")
                .detectionClass("person")
                .confidence(0.9f)
                .status("new")
                .build();
    }

    @Test
    void getById_returnsAlert() {
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));
        assertThat(alertService.getById(id).getNodeId()).isEqualTo("n1");
    }

    @Test
    void getById_notFound_throws() {
        when(alertRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> alertService.getById(id))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void getAlerts_filtered() {
        Pageable p = PageRequest.of(0, 20);
        when(alertRepository.findFiltered("high", null, null, p))
                .thenReturn(new PageImpl<>(List.of(alert)));
        Page<?> page = alertService.getAlerts("high", null, null, p);
        assertThat(page.getTotalElements()).isEqualTo(1);
    }

    @Test
    void acknowledge_updatesStatus() {
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
        var req = new AlertUpdateRequest();
        req.setNotes("ok");
        var r = alertService.acknowledge(id, req);
        assertThat(r.getStatus()).isEqualTo("acknowledged");
        assertThat(r.getNotes()).isEqualTo("ok");
    }

    @Test
    void acknowledge_notFound_throws() {
        when(alertRepository.findById(id)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> alertService.acknowledge(id, null))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void resolve_updatesStatus() {
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
        var r = alertService.resolve(id, null);
        assertThat(r.getStatus()).isEqualTo("resolved");
    }

    @Test
    void markFalsePositive_updatesStatus() {
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
        var r = alertService.markFalsePositive(id, null);
        assertThat(r.getStatus()).isEqualTo("false_positive");
    }

    @Test
    void acknowledge_nullNotes_ok() {
        when(alertRepository.findById(id)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
        alertService.acknowledge(id, null);
        verify(alertRepository).save(any());
    }
}
