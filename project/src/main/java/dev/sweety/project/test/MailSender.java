package dev.sweety.project.test;

import lombok.Setter;

import javax.mail.*;
import javax.mail.internet.*;
import java.util.Arrays;
import java.util.Properties;

public enum MailSender {
    INSTANCE;

    private final Properties properties = new Properties();
    boolean skip = true;
    @Setter
    private String username, password;

    MailSender() {
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.gmail.com");
        properties.put("mail.smtp.port", "587");

    }

    public void send(String object, String content, String... address) {

        if (skip) return;

        Session session = Session.getInstance(properties, new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(username));
            Address[] recipients = Arrays.stream(address).map(a -> {
                try {
                    return new InternetAddress(a);
                } catch (AddressException e) {
                    throw new RuntimeException(e);
                }
            }).toArray(Address[]::new);

            message.setRecipients(Message.RecipientType.TO, recipients);
            message.setSubject(object);

            MimeBodyPart htmlPart = new MimeBodyPart();
            htmlPart.setContent(content, "text/html; charset=utf-8");

            Multipart multipart = new MimeMultipart();
            multipart.addBodyPart(htmlPart);

            message.setContent(multipart);

            message.setHeader("X-Mailer", "JavaMailer");
            message.setHeader("Content-Transfer-Encoding", "8bit");

            Transport.send(message);

        } catch (MessagingException e) {
            e.printStackTrace(System.err);
            throw new RuntimeException(e);
        }
    }

    public void set(String... info) {
        this.username = info[0];
        this.password = info[1];
    }
}