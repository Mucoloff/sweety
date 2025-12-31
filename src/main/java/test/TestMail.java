package test;

import dev.sweety.core.time.TimeUtils;

public class TestMail {

    public static void main(String[] args) {
        String email = "mucofietigibbys@gmail.com";
        MailSender.INSTANCE.set(email, "byox vfql hinr sxyr");

        String username = "lucky", license = "lucky", resource = "negro", edition = "Developer";

        long expireAt = System.currentTimeMillis() + 5000;

        String welcome = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your account has been created successfully.</p>
                    <p>Please confirm your email address by clicking the link below:</p>
                    <p><a href='[confirmation_link]' style="color: #1a73e8;">Confirm Email</a></p>
                    <p>Thank you for choosing <span style="color: #1a73e8;">Aurora</span>.</p>
                    <p>Best regards,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username);

        long expiryTime = expireAt - System.currentTimeMillis();
        String newLicense = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your license for the resource '<strong>%s</strong>' has been created.</p>
                    <p><strong>License Details:</strong></p>
                    <p>License Key: <strong>%s</strong></p>
                    <p>Resource: <strong>%s</strong></p>
                    <p>Edition: <strong>%s</strong></p>
                    <p>Expiration Date: <strong>%s</strong></p>
                    <p>Time Remaining: <strong>%s</strong></p>
                    <p>Thank you for choosing our service.</p>
                    <p>Best regards,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username, resource, license, resource, edition, TimeUtils.date(expireAt, "dd-MM-yyyy HH:mm"), TimeUtils.formatDuration(expiryTime));

        String expiring = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your license for the resource <strong>%s</strong> will expire in <strong>%s</strong>.</p>
                    <p>Thank you for choosing our service.</p>
                    <p>Best regards,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username, resource, TimeUtils.formatDuration(expiryTime));

        String expired = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your license for the resource <strong>%s</strong> has expired. You have <strong>%s</strong> to renew it.</p>
                    <p>Thank you for choosing our service.</p>
                    <p>Best regards,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username, resource, TimeUtils.formatDuration(3 * 24 * 60 * 60 * 1000));

        email = "lupica.francy06@gmail.com";

        MailSender.INSTANCE.send("Aurora | Welcome " + username, welcome, email);
        MailSender.INSTANCE.send("Aurora | License creation", newLicense, email);
        MailSender.INSTANCE.send("Aurora | " + resource, expiring, email);
        MailSender.INSTANCE.send("Aurora | " + resource, expired, email);

    }

     /*
        String accountReactivation = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your account has been successfully reactivated.</p>
                    <p>Welcome back! We are glad to have you with us again.</p>
                    <p>Thank you,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username);

        String paymentConfirmation = """
                <html>
                <body style="font-family: Arial, sans-serif; color: #333;">
                    <p>Dear <strong>%s</strong>,</p>
                    <p>We have received your payment for the resource <strong>%s</strong>.</p>
                    <p>Thank you for your purchase!</p>
                    <p>Best regards,<br><span style="color: #1a73e8;">Aurora Team</span></p>
                </body>
                </html>
                """.formatted(username, resource);
         */
}