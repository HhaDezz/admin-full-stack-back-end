package com.atelier.api.service;

import com.atelier.api.config.AppProperties;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

/**
 * Account emails. With spring.mail.host set they go out over SMTP in the background;
 * without it they are written to the log (links included), so every flow works in
 * development without a mail server. The last 20 messages are also kept in memory for tests.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final int KEEP = 20;

    public record Mail(String to, String subject, String text, LocalDateTime sentAt) {
    }

    private final ObjectProvider<JavaMailSender> sender;
    private final TaskExecutor executor;
    private final AppProperties props;
    private final Deque<Mail> recent = new ArrayDeque<>();
    private final boolean smtpConfigured;

    public MailService(ObjectProvider<JavaMailSender> sender, @Qualifier("applicationTaskExecutor") TaskExecutor executor, AppProperties props,
                       @Value("${spring.mail.host:}") String host) {
        this.sender = sender;
        this.executor = executor;
        this.props = props;
        // An empty spring.mail.host can still create a sender; treat it as "not configured".
        this.smtpConfigured = host != null && !host.isBlank();
    }

    /* ── Messages ── */

    public void verifyEmail(String to, String name, String link) {
        send(to, "Confirm your email address",
                "Hi " + name + ",",
                "Confirm your email address to finish setting up your " + props.brandName() + " account. The link works for 24 hours.",
                "Confirm email", link,
                "If you didn't create an account, you can ignore this email.");
    }

    public void resetPassword(String to, String name, String link) {
        send(to, "Reset your password",
                "Hi " + name + ",",
                "Someone asked to reset the password for your " + props.brandName() + " account. The link works for one hour and only once.",
                "Choose a new password", link,
                "If it wasn't you, ignore this email: your password stays the same.");
    }

    public void securityAlert(String to, String name, String title, String body, String path) {
        send(to, title, "Hi " + name + ",", body, "Review your security settings", props.webUrl() + path,
                "If this wasn't you, change your password straight away.");
    }

    public void notification(String to, String name, String title, String body, String path) {
        send(to, title, "Hi " + name + ",", body == null ? "" : body, path == null ? null : "Open " + props.brandName(),
                path == null ? null : props.webUrl() + path, "You can turn these emails off in Settings → Notifications.");
    }

    /** The last few messages, newest first (for tests and the development log). */
    public List<Mail> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }

    /* ── Sending ── */

    private void send(String to, String subject, String greeting, String body, String button, String link, String footer) {
        String text = greeting + "\n\n" + body + (link == null ? "" : "\n\n" + button + ": " + link) + "\n\n" + footer
                + "\n\n— " + props.brandName();
        synchronized (recent) {
            recent.addFirst(new Mail(to, subject, text, LocalDateTime.now()));
            while (recent.size() > KEEP) recent.removeLast();
        }
        JavaMailSender smtp = smtpConfigured ? sender.getIfAvailable() : null;
        if (smtp == null) {
            log.info("Email (no SMTP configured, set spring.mail.host to send):\n  To: {}\n  Subject: {}\n{}", to, subject,
                    text.indent(2));
            return;
        }
        String html = html(subject, greeting, body, button, link, footer);
        executor.execute(() -> {
            try {
                MimeMessage m = smtp.createMimeMessage();
                MimeMessageHelper h = new MimeMessageHelper(m, true, "UTF-8");
                h.setFrom(props.mail().from(), props.brandName());
                h.setTo(to);
                h.setSubject(subject);
                h.setText(text, html);
                smtp.send(m);
            } catch (Exception e) {
                log.warn("Could not send email '{}' to {}: {}", subject, to, e.getMessage());
            }
        });
    }

    private String html(String subject, String greeting, String body, String button, String link, String footer) {
        String e = props.brandName();
        String cta = link == null ? "" : """
                <p style="margin:28px 0"><a href="%s" style="background:#1f4d45;color:#fff;text-decoration:none;padding:12px 20px;border-radius:10px;font-weight:600;display:inline-block">%s</a></p>
                <p style="color:#6f7a75;font-size:13px">Or paste this link into your browser:<br><span style="word-break:break-all">%s</span></p>
                """.formatted(HtmlUtils.htmlEscape(link), HtmlUtils.htmlEscape(button), HtmlUtils.htmlEscape(link));
        return """
                <!doctype html><html><body style="margin:0;background:#f4f1ea;font-family:-apple-system,Segoe UI,Roboto,sans-serif;color:#1b2824">
                <div style="max-width:520px;margin:0 auto;padding:32px 20px">
                <div style="font:600 22px Georgia,serif;margin-bottom:20px">%s</div>
                <div style="background:#fffdf9;border:1px solid #e7e1d5;border-radius:16px;padding:28px">
                <h1 style="font:600 20px Georgia,serif;margin:0 0 16px">%s</h1>
                <p>%s</p><p style="line-height:1.55">%s</p>%s
                <p style="color:#6f7a75;font-size:13px;margin-top:24px">%s</p>
                </div></div></body></html>
                """.formatted(HtmlUtils.htmlEscape(e), HtmlUtils.htmlEscape(subject), HtmlUtils.htmlEscape(greeting),
                HtmlUtils.htmlEscape(body), cta, HtmlUtils.htmlEscape(footer));
    }
}
