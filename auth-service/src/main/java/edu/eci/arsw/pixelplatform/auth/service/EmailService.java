package edu.eci.arsw.pixelplatform.auth.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendVerificationCode(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Verifica tu cuenta en PixelPlatform");
            message.setText(
                    "Hola,\n\n" +
                    "Tu codigo de verificacion es: " + code + "\n\n" +
                    "Este codigo vence en 15 minutos.\n\n" +
                    "Si no creaste una cuenta en PixelPlatform, ignora este mensaje.\n\n" +
                    "-- El equipo de PixelPlatform"
            );
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error al enviar correo de verificacion a {}: {}", toEmail, e.getMessage(), e);
        }
    }

    public void sendInvitationEmail(String toEmail, String inviterName, String canvasName,
            String code, String joinLink) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(toEmail);
            message.setSubject("Te invitaron a un lienzo en PixelPlatform");
            message.setText(inviterName + " te ha invitado a unirte al lienzo '" + canvasName +
                    "'. Utiliza el siguiente codigo para unirte: " + code + ".\n\n" +
                    "Tambien podes unirte directamente con este enlace: " + joinLink);
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Error al enviar correo de invitacion a {}: {}", toEmail, e.getMessage(), e);
        }
    }
}
