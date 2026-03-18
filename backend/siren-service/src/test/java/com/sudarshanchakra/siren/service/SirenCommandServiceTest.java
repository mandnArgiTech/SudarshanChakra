package com.sudarshanchakra.siren.service;

import com.sudarshanchakra.siren.dto.SirenRequest;
import com.sudarshanchakra.siren.model.SirenAction;
import com.sudarshanchakra.siren.repository.SirenActionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SirenCommandServiceTest {

    @Mock
    RabbitTemplate rabbitTemplate;

    @Mock
    SirenActionRepository sirenActionRepository;

    SirenCommandService serviceWithMq;
    SirenCommandService serviceNoMq;

    @BeforeEach
    void setUp() {
        serviceWithMq = new SirenCommandService(rabbitTemplate, sirenActionRepository);
        serviceNoMq = new SirenCommandService(null, sirenActionRepository);
        when(sirenActionRepository.save(any(SirenAction.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void triggerSiren_publishesAndSaves() {
        UUID aid = UUID.randomUUID();
        var req = SirenRequest.builder().nodeId("edge-1").alertId(aid).sirenUrl("http://x/a.mp3").build();
        var res = serviceWithMq.triggerSiren(req);
        assertThat(res.getStatus()).isEqualTo("triggered");
        assertThat(res.getNodeId()).isEqualTo("edge-1");
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class));
        verify(sirenActionRepository).save(any());
    }

    @Test
    void stopSiren_publishesAndSaves() {
        var req = SirenRequest.builder().nodeId("edge-1").build();
        var res = serviceWithMq.stopSiren(req);
        assertThat(res.getStatus()).isEqualTo("stopped");
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class));
    }

    @Test
    void triggerSiren_withoutRabbit_stillSaves() {
        var req = SirenRequest.builder().nodeId("n").build();
        serviceNoMq.triggerSiren(req);
        verify(sirenActionRepository).save(argThat((SirenAction a) -> "trigger".equals(a.getAction())));
    }

    @Test
    void triggerSiren_auditFields() {
        var req = SirenRequest.builder()
                .nodeId("node-a")
                .alertId(UUID.randomUUID())
                .sirenUrl("url")
                .build();
        serviceWithMq.triggerSiren(req);
        ArgumentCaptor<SirenAction> cap = ArgumentCaptor.forClass(SirenAction.class);
        verify(sirenActionRepository).save(cap.capture());
        assertThat(cap.getValue().getTargetNode()).isEqualTo("node-a");
        assertThat(cap.getValue().getTriggeredBySystem()).isTrue();
    }

    @Test
    void getHistory_delegates() {
        when(sirenActionRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(Page.empty());
        assertThat(serviceWithMq.getHistory(PageRequest.of(0, 10)).getTotalElements()).isEqualTo(0);
    }
}
