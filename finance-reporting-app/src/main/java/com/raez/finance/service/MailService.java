package com.raez.finance.service;

import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;

/**
 * Sends password-reset emails using SMTP settings from {@link GlobalSettingsService}.
 */
public final class MailService {

    private MailService() {}

    public static void sendPasswordResetEmail(String toEmail, String token, String accountUsername)
            throws MessagingException {
        GlobalSettingsService gs = GlobalSettingsService.getInstance();
        if (!gs.isSmtpEnabled()) {
            throw new IllegalStateException("SMTP is disabled in settings.");
        }
        String host = gs.getSmtpHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("SMTP host is not configured.");
        }
        int port = gs.getSmtpPort();
        String from = gs.getSmtpFrom();
        if (from == null || from.isBlank()) {
            from = gs.getSmtpUser();
        }
        if (from == null || from.isBlank()) {
            throw new IllegalStateException("SMTP From address is not configured.");
        }

        Properties props = new Properties();
        props.put("mail.smtp.host", host);
        props.put("mail.smtp.port", String.valueOf(port));
        String user = gs.getSmtpUser();
        String pass = gs.getSmtpPassword();
        boolean auth = user != null && !user.isBlank();
        props.put("mail.smtp.auth", auth ? "true" : "false");
        if (gs.isSmtpUseTls()) {
            props.put("mail.smtp.starttls.enable", "true");
        }

        Session session;
        if (auth) {
            final String u = user.trim();
            final String p = pass != null ? pass : "";
            session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(u, p);
                }
            });
        } else {
            session = Session.getInstance(props);
        }

        MimeMessage msg = new MimeMessage(session);
        msg.setFrom(new InternetAddress(from));
        msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.trim()));
        msg.setSubject("RAEZ Finance — password reset");
        String who = accountUsername != null && !accountUsername.isBlank() ? accountUsername : "your account";
        String body = "You requested a password reset for RAEZ Finance user: " + who + ".\n"
                + "(This message was sent to the address you entered; it may differ from the email on file.)\n\n"
                + "Use this one-time token on the login screen under Forgot password:\n\n"
                + token
                + "\n\nThis token expires in 24 hours. If you did not request this, ignore this email.";
        msg.setText(body);
        Transport.send(msg);
    }
}
