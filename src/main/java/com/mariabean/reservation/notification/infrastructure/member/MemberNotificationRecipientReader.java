package com.mariabean.reservation.notification.infrastructure.member;

import com.mariabean.reservation.member.domain.MemberRepository;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import com.mariabean.reservation.notification.application.NotificationRecipientReader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class MemberNotificationRecipientReader implements NotificationRecipientReader {

    private final MemberRepository memberRepository;

    @Override
    public Optional<NotificationRecipient> findByMemberId(Long memberId) {
        return memberRepository.findById(memberId)
                .map(member -> new NotificationRecipient(
                        member.getId(),
                        member.getName(),
                        member.getEmail(),
                        member.getPhoneNumber(),
                        member.getProviderId()));
    }
}
