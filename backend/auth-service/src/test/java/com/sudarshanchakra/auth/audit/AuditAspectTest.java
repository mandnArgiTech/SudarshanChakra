package com.sudarshanchakra.auth.audit;

import com.sudarshanchakra.auth.context.TenantContext;
import com.sudarshanchakra.auth.dto.FarmResponse;
import com.sudarshanchakra.auth.service.AuditService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditAspectTest {

    @Mock
    AuditService auditService;

    @InjectMocks
    AuditAspect auditAspect;

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void afterSuccess_logsWhenUserAndFarmPresent() {
        UUID user = UUID.randomUUID();
        UUID farm = UUID.randomUUID();
        TenantContext.set(farm, user, java.util.List.of());
        FarmResponse body = FarmResponse.builder().id(farm).name("F").slug("f").build();
        Auditable ann = mock(Auditable.class);
        when(ann.action()).thenReturn("farm.create");
        when(ann.entityType()).thenReturn("farm");
        when(ann.entityId()).thenReturn("");
        auditAspect.afterSuccess(null, ann, ResponseEntity.ok(body));

        ArgumentCaptor<String> action = ArgumentCaptor.forClass(String.class);
        verify(auditService).log(eq(farm), eq(user), action.capture(), eq("farm"), eq(farm.toString()), any(), any());
        assertThat(action.getValue()).isEqualTo("farm.create");
    }
}
