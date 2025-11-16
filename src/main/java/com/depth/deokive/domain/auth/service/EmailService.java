package com.depth.deokive.domain.email.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {
    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;

    @Async
    public void sendEmail(String email) {
        MimeMessage mimeMessage = javaMailSender.createMimeMessage();

        try {
            // p0: Message, p1: multipart 사용 여부, p2: encoding
            MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, false, "UTF-8");

            mimeMessageHelper.setTo(String.valueOf(email));
            mimeMessageHelper.setSubject("Deokive 이메일 인증"); // 메일 제목
            mimeMessageHelper.setText(setContext(), true); // 메일 본문 내용, HTML 여부
            javaMailSender.send(mimeMessage);

            log.info("🟢 Success to Send Email");
        } catch (MessagingException e) {
            log.info("🔴 Failed to Send Email");
            throw new RuntimeException(e);
        }
    }

    // Thymeleaf 를 통한 HTML 적용
    public String setContext() {
        Context context = new Context();
        context.setVariable("date", LocalDateTime.now());
        return templateEngine.process("emailVerification", context);
    }
}
