package com.sudarshanchakra.auth.audit;

import com.sudarshanchakra.auth.context.TenantContext;
import com.sudarshanchakra.auth.dto.FarmResponse;
import com.sudarshanchakra.auth.service.AuditService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ExpressionParser spelParser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void afterSuccess(JoinPoint joinPoint, Auditable auditable, Object result) {
        try {
            UUID userId = TenantContext.getUserId();
            if (userId == null) {
                log.debug("Skip audit {}: no user in context", auditable.action());
                return;
            }
            Object payload = unwrapControllerResult(result);
            String entityIdStr = resolveEntityId(joinPoint, auditable, result, payload);
            UUID farmId = TenantContext.getFarmId();
            if (farmId == null && payload instanceof FarmResponse fr) {
                farmId = fr.getId();
            }
            if (farmId == null && "farm".equals(auditable.entityType()) && entityIdStr != null) {
                try {
                    farmId = UUID.fromString(entityIdStr);
                } catch (IllegalArgumentException ignored) {
                    // leave null
                }
            }
            if (farmId == null) {
                log.debug("Skip audit {}: could not resolve farm_id", auditable.action());
                return;
            }
            String ip = clientIp();
            auditService.log(
                    farmId,
                    userId,
                    auditable.action(),
                    auditable.entityType(),
                    entityIdStr,
                    Map.of(),
                    ip);
        } catch (Exception e) {
            log.warn("Audit logging failed for {}: {}", auditable.action(), e.getMessage());
        }
    }

    private static Object unwrapControllerResult(Object result) {
        if (result instanceof ResponseEntity<?> re) {
            return re.getBody();
        }
        return result;
    }

    private String resolveEntityId(JoinPoint joinPoint, Auditable auditable, Object result, Object payload) {
        if (StringUtils.hasText(auditable.entityId())) {
            EvaluationContext ctx = buildContext(joinPoint, result);
            Object value = spelParser.parseExpression(auditable.entityId()).getValue(ctx);
            if (value == null) {
                return null;
            }
            if (value instanceof UUID u) {
                return u.toString();
            }
            return value.toString();
        }
        if (payload == null) {
            return null;
        }
        try {
            Method m = payload.getClass().getMethod("getId");
            Object id = m.invoke(payload);
            return id == null ? null : id.toString();
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    private EvaluationContext buildContext(JoinPoint joinPoint, Object result) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        ctx.setVariable("result", result);
        MethodSignature sig = (MethodSignature) joinPoint.getSignature();
        Method method = sig.getMethod();
        Object[] args = joinPoint.getArgs();
        String[] names = nameDiscoverer.getParameterNames(method);
        if (names != null) {
            for (int i = 0; i < names.length && i < args.length; i++) {
                ctx.setVariable(names[i], args[i]);
            }
        }
        return ctx;
    }

    private String clientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) {
            return null;
        }
        HttpServletRequest req = attrs.getRequest();
        return req.getRemoteAddr();
    }
}
