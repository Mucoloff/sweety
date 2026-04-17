package dev.sweety.time;


import javax.mail.*;
import javax.mail.internet.*;
import java.util.Arrays;
import java.util.Properties;

public class MailSender {

    private final Properties properties;
    private String username, password;

    public MailSender(Properties properties) {
        this.properties = properties;
    }

    public MailSender() {
        this(new Properties());
    }

    public MailSender setup(String auth, String starttls, String host, String port) {
        this.properties.put("mail.smtp.auth", auth);
        this.properties.put("mail.smtp.starttls.enable", starttls);
        this.properties.put("mail.smtp.host", host);
        this.properties.put("mail.smtp.port", port);
        return this;
    }

    public MailSender setGmail() {
        return setup("true", "true", "smtp.gmail.com", "587");
    }

    public MailSender setPassword(String password) {
        this.password = password;
        return this;
    }

    public MailSender setUsername(String username) {
        this.username = username;
        return this;
    }

    public void send(String object, String content, String... address) {

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