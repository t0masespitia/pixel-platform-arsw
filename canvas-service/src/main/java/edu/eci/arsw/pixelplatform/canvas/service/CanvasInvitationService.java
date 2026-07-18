package edu.eci.arsw.pixelplatform.canvas.service;

import edu.eci.arsw.pixelplatform.canvas.client.AuthServiceClient;
import edu.eci.arsw.pixelplatform.canvas.client.ChatServiceClient;
import edu.eci.arsw.pixelplatform.canvas.dto.CanvasResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.CreateInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.InvitationResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.JoinCanvasRequest;
import edu.eci.arsw.pixelplatform.canvas.dto.MembershipResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.MyInvitationResponse;
import edu.eci.arsw.pixelplatform.canvas.dto.RespondInvitationRequest;
import edu.eci.arsw.pixelplatform.canvas.model.Canvas;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasInvitation;
import edu.eci.arsw.pixelplatform.canvas.model.CanvasMembership;
import edu.eci.arsw.pixelplatform.canvas.model.InvitationStatus;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasInvitationRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasMembershipRepository;
import edu.eci.arsw.pixelplatform.canvas.repository.CanvasRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class CanvasInvitationService {

    private static final Logger log = LoggerFactory.getLogger(CanvasInvitationService.class);

    private final CanvasRepository canvasRepository;
    private final CanvasInvitationRepository canvasInvitationRepository;
    private final CanvasMembershipRepository canvasMembershipRepository;
    private final AuthServiceClient authServiceClient;
    private final ChatServiceClient chatServiceClient;
    private final CanvasService canvasService;
    private final String frontendBaseUrl;

    public CanvasInvitationService(CanvasRepository canvasRepository,
                                   CanvasInvitationRepository canvasInvitationRepository,
                                   CanvasMembershipRepository canvasMembershipRepository,
                                   AuthServiceClient authServiceClient,
                                   ChatServiceClient chatServiceClient,
                                   CanvasService canvasService,
                                   @Value("${frontend.base-url}") String frontendBaseUrl) {
        this.canvasRepository = canvasRepository;
        this.canvasInvitationRepository = canvasInvitationRepository;
        this.canvasMembershipRepository = canvasMembershipRepository;
        this.authServiceClient = authServiceClient;
        this.chatServiceClient = chatServiceClient;
        this.canvasService = canvasService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public InvitationResponse createInvitation(UUID canvasId, CreateInvitationRequest request, String authHeader) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));

        if (!canvas.isPrivate()) {
            throw new IllegalArgumentException("Solo los lienzos privados admiten invitaciones");
        }
        if (!request.requesterId().equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno del lienzo puede invitar");
        }
        if (request.targetUserId().equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("No puedes invitarte a ti mismo");
        }
        if (canvasMembershipRepository.existsByCanvasIdAndUserId(canvasId, request.targetUserId())) {
            throw new IllegalArgumentException("Este usuario ya es miembro del lienzo");
        }

        var existing = canvasInvitationRepository.findByCanvasIdAndTargetUserId(canvasId, request.targetUserId());
        CanvasInvitation invitation;
        if (existing.isPresent()) {
            CanvasInvitation inv = existing.get();
            if (inv.getStatus() == InvitationStatus.PENDING) {
                throw new IllegalArgumentException("Ya existe una invitacion pendiente para este usuario");
            }
            deleteInvitationMessageIfPresent(authHeader, inv.getId());
            inv.setCode(generateUniqueCode());
            inv.setStatus(InvitationStatus.PENDING);
            inv.setRespondedAt(null);
            inv.setCreatedAt(Instant.now());
            invitation = canvasInvitationRepository.save(inv);
        } else {
            invitation = CanvasInvitation.builder()
                    .id(UUID.randomUUID())
                    .canvasId(canvasId)
                    .targetUserId(request.targetUserId())
                    .invitedByUserId(request.requesterId())
                    .code(generateUniqueCode())
                    .status(InvitationStatus.PENDING)
                    .createdAt(Instant.now())
                    .build();
            invitation = canvasInvitationRepository.save(invitation);
        }

        try {
            Map<String, Object> inviterProfile = authServiceClient.getUserProfile(authHeader, request.requesterId());
            String inviterName = inviterProfile.get("firstName") + " " + inviterProfile.get("lastName");
            String content = inviterName + " te ha invitado a unirte al lienzo '" + canvas.getName() +
                    "'. Utiliza el siguiente codigo para unirte: " + invitation.getCode() + ".";
            chatServiceClient.sendInvitationMessage(authHeader, request.targetUserId(), canvasId,
                    canvas.getName(), invitation.getId(), content);
        } catch (Exception e) {
            log.warn("No se pudo notificar la invitacion por chat para canvasId={} targetUserId={}: {}",
                    canvasId, request.targetUserId(), e.getMessage());
        }

        return toInvitationResponse(invitation);
    }

    public InvitationResponse respondToInvitation(UUID canvasId, UUID invitationId,
            RespondInvitationRequest request, String authHeader) {
        CanvasInvitation invitation = canvasInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitacion no encontrada"));
        if (!invitation.getCanvasId().equals(canvasId)) {
            throw new IllegalArgumentException("La invitacion no pertenece a este lienzo");
        }
        if (!invitation.getTargetUserId().equals(request.userId())) {
            throw new IllegalArgumentException("No autorizado para responder esta invitacion");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Esta invitacion ya fue respondida");
        }
        if (Boolean.TRUE.equals(request.accept())) {
            if (!canvasMembershipRepository.existsByCanvasIdAndUserId(canvasId, request.userId())) {
                CanvasMembership membership = CanvasMembership.builder()
                        .id(UUID.randomUUID())
                        .canvasId(canvasId)
                        .userId(request.userId())
                        .joinedAt(Instant.now())
                        .build();
                canvasMembershipRepository.save(membership);
            }
            invitation.setStatus(InvitationStatus.ACCEPTED);
        } else {
            invitation.setStatus(InvitationStatus.REJECTED);
        }
        invitation.setRespondedAt(Instant.now());
        canvasInvitationRepository.save(invitation);
        deleteInvitationMessageIfPresent(authHeader, invitation.getId());
        return toInvitationResponse(invitation);
    }

    public List<InvitationResponse> listInvitations(UUID canvasId, String requesterId) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (!requesterId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno puede ver las invitaciones");
        }
        return canvasInvitationRepository.findByCanvasId(canvasId).stream()
                .map(this::toInvitationResponse)
                .collect(Collectors.toList());
    }

    public List<MyInvitationResponse> listMyInvitations(String userId) {
        return canvasInvitationRepository.findByTargetUserIdAndStatus(userId, InvitationStatus.PENDING)
                .stream()
                .map(inv -> {
                    String canvasName = canvasRepository.findById(inv.getCanvasId())
                            .map(Canvas::getName).orElse("Lienzo eliminado");
                    return new MyInvitationResponse(inv.getId(), inv.getCanvasId(), canvasName,
                            inv.getInvitedByUserId(), inv.getCode(), inv.getCreatedAt());
                })
                .collect(Collectors.toList());
    }

    public CanvasResponse joinCanvas(JoinCanvasRequest request, String authHeader) {
        CanvasInvitation invitation = canvasInvitationRepository.findByCode(request.code())
                .orElseThrow(() -> new IllegalArgumentException("Codigo invalido"));
        if (!invitation.getTargetUserId().equals(request.userId())) {
            throw new IllegalArgumentException("Este codigo no te pertenece");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Este codigo ya fue utilizado o ya no es valido");
        }
        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setRespondedAt(Instant.now());
        canvasInvitationRepository.save(invitation);
        if (!canvasMembershipRepository.existsByCanvasIdAndUserId(invitation.getCanvasId(), request.userId())) {
            CanvasMembership membership = CanvasMembership.builder()
                    .id(UUID.randomUUID())
                    .canvasId(invitation.getCanvasId())
                    .userId(request.userId())
                    .joinedAt(Instant.now())
                    .build();
            canvasMembershipRepository.save(membership);
        }
        deleteInvitationMessageIfPresent(authHeader, invitation.getId());
        return canvasService.getCanvas(invitation.getCanvasId());
    }

    public List<MembershipResponse> listMembers(UUID canvasId, String requesterId) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));

        if (!requesterId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno puede ver los miembros");
        }

        return canvasMembershipRepository.findByCanvasId(canvasId).stream()
                .map(m -> new MembershipResponse(m.getUserId(), m.getJoinedAt()))
                .collect(Collectors.toList());
    }

    public void removeMember(UUID canvasId, String userId, String requesterId) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));

        if (!requesterId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno puede expulsar miembros");
        }
        if (userId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("El dueno no puede ser expulsado de su propio lienzo");
        }

        CanvasMembership membership = canvasMembershipRepository
                .findByCanvasIdAndUserId(canvasId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Ese usuario no es miembro de este lienzo"));

        canvasMembershipRepository.delete(membership);
    }

    public void leaveCanvas(UUID canvasId, String userId) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (userId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("El dueno no puede abandonar su propio lienzo");
        }
        CanvasMembership membership = canvasMembershipRepository
                .findByCanvasIdAndUserId(canvasId, userId)
                .orElseThrow(() -> new IllegalArgumentException("No eres miembro de este lienzo"));
        canvasMembershipRepository.delete(membership);
    }

    public void cancelInvitation(UUID canvasId, UUID invitationId, String requesterId, String authHeader) {
        Canvas canvas = canvasRepository.findById(canvasId)
                .orElseThrow(() -> new IllegalArgumentException("Lienzo no encontrado"));
        if (!requesterId.equals(canvas.getOwnerId())) {
            throw new IllegalArgumentException("Solo el dueno puede cancelar invitaciones");
        }
        CanvasInvitation invitation = canvasInvitationRepository.findById(invitationId)
                .orElseThrow(() -> new IllegalArgumentException("Invitacion no encontrada"));
        if (!invitation.getCanvasId().equals(canvasId)) {
            throw new IllegalArgumentException("La invitacion no pertenece a este lienzo");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new IllegalArgumentException("Solo se pueden cancelar invitaciones pendientes");
        }
        deleteInvitationMessageIfPresent(authHeader, invitation.getId());
        canvasInvitationRepository.delete(invitation);
    }

    private void deleteInvitationMessageIfPresent(String authHeader, UUID invitationId) {
        try {
            chatServiceClient.deleteInvitationMessages(authHeader, invitationId);
        } catch (Exception e) {
            log.warn("No se pudo eliminar el mensaje de invitacion invitationId={}: {}",
                    invitationId, e.getMessage());
        }
    }

    private InvitationResponse toInvitationResponse(CanvasInvitation invitation) {
        String joinLink = frontendBaseUrl + "/join?code=" + invitation.getCode();
        return new InvitationResponse(
                invitation.getId(),
                invitation.getCanvasId(),
                invitation.getTargetUserId(),
                invitation.getInvitedByUserId(),
                invitation.getCode(),
                joinLink,
                invitation.getStatus(),
                invitation.getCreatedAt()
        );
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 5; i++) {
            String code = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            if (canvasInvitationRepository.findByCode(code).isEmpty()) {
                return code;
            }
        }
        throw new IllegalStateException("No se pudo generar un codigo unico");
    }
}
