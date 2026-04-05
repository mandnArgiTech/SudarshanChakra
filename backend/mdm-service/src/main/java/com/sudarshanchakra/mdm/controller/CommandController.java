package com.sudarshanchakra.mdm.controller;

import com.sudarshanchakra.mdm.dto.CommandRequest;
import com.sudarshanchakra.mdm.model.MdmCommand;
import com.sudarshanchakra.mdm.service.CommandDispatchService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mdm/commands")
@RequiredArgsConstructor
public class CommandController {

    private final CommandDispatchService commandService;

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN','MANAGER')")
    public ResponseEntity<MdmCommand> issueCommand(@Valid @RequestBody CommandRequest request) {
        MdmCommand cmd = commandService.dispatch(request);
        return ResponseEntity.ok(cmd);
    }

    @GetMapping("/{deviceId}")
    public ResponseEntity<List<MdmCommand>> getCommandHistory(@PathVariable UUID deviceId) {
        return ResponseEntity.ok(commandService.getHistory(deviceId));
    }
}
