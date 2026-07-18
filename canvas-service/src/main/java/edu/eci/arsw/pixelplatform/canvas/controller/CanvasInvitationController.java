package edu.eci.arsw.pixelplatform.canvas.controller;

import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.InvitationResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.JoinCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.LeaveCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.MembershipResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.RespondInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.service.CanvasInvitationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/canvases")
public class CanvasInvitationController {

    private final CanvasInvitationService canvasInvitationService;

    public CanvasInvitationController(CanvasInvitationService canvasInvitationService) {
        this.canvasInvitationService = canvasInvitationService;
    }

    @PostMapping("/{canvasId}/invitations")
    public ResponseEntity<?> createInvitation(@PathVariable UUID canvasId,
                                              @Valid @RequestBody CreateInvitationRequest request,
                                              HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.requesterId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            InvitationResponse response = canvasInvitationService.createInvitation(canvasId, request, authHeader);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{canvasId}/invitations/{invitationId}/respond")
    public ResponseEntity<?> respondToInvitation(@PathVariable UUID canvasId,
                                                  @PathVariable UUID invitationId,
                                                  @Valid @RequestBody RespondInvitationRequest request,
                                                  HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.userId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            return ResponseEntity.ok(canvasInvitationService.respondToInvitation(canvasId, invitationId, request, authHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{canvasId}/invitations")
    public ResponseEntity<?> listInvitations(@PathVariable UUID canvasId,
                                              @RequestParam String requesterId,
                                              HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(requesterId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            return ResponseEntity.ok(canvasInvitationService.listInvitations(canvasId, requesterId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/invitations/mine")
    public ResponseEntity<?> listMyInvitations(@RequestParam String userId,
                                                HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(userId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        return ResponseEntity.ok(canvasInvitationService.listMyInvitations(userId));
    }

    @PostMapping("/join")
    public ResponseEntity<?> joinCanvas(@Valid @RequestBody JoinCanvasRequest request,
                                        HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.userId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            CanvasResponse response = canvasInvitationService.joinCanvas(request, authHeader);
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{canvasId}/members")
    public ResponseEntity<?> listMembers(@PathVariable UUID canvasId,
                                         @RequestParam String requesterId,
                                         HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(requesterId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            List<MembershipResponse> members = canvasInvitationService.listMembers(canvasId, requesterId);
            return ResponseEntity.ok(members);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{canvasId}/members/{userId}")
    public ResponseEntity<?> removeMember(@PathVariable UUID canvasId,
                                          @PathVariable String userId,
                                          @RequestParam String requesterId,
                                          HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(requesterId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            canvasInvitationService.removeMember(canvasId, userId, requesterId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{canvasId}/leave")
    public ResponseEntity<?> leaveCanvas(@PathVariable UUID canvasId,
                                         @Valid @RequestBody LeaveCanvasRequest request,
                                         HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(request.userId())) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            canvasInvitationService.leaveCanvas(canvasId, request.userId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{canvasId}/invitations/{invitationId}")
    public ResponseEntity<?> cancelInvitation(@PathVariable UUID canvasId,
                                              @PathVariable UUID invitationId,
                                              @RequestParam String requesterId,
                                              HttpServletRequest httpRequest) {
        String verifiedUserId = (String) httpRequest.getAttribute("verifiedUserId");
        if (!verifiedUserId.equals(requesterId)) {
            return ResponseEntity.status(403).body(Map.of("error",
                    "El usuario del token no coincide con el userId de la peticion"));
        }
        try {
            String authHeader = httpRequest.getHeader("Authorization");
            canvasInvitationService.cancelInvitation(canvasId, invitationId, requesterId, authHeader);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
