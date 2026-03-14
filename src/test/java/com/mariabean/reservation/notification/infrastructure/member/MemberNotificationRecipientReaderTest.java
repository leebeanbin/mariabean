package com.mariabean.reservation.notification.infrastructure.member;

import com.mariabean.reservation.member.domain.Member;
import com.mariabean.reservation.member.domain.MemberRepository;
import com.mariabean.reservation.member.domain.Role;
import com.mariabean.reservation.notification.application.NotificationRecipient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class MemberNotificationRecipientReaderTest {

    @Mock
    private MemberRepository memberRepository;

    @InjectMocks
    private MemberNotificationRecipientReader reader;

    @Test
    @DisplayName("memberId로 수신자 정보를 조회한다")
    void findByMemberId_success() {
        Member member = Member.builder()
                .id(7L)
                .email("test@test.com")
                .name("tester")
                .role(Role.USER)
                .provider("kakao")
                .providerId("kakao_777")
                .phoneNumber("01099998888")
                .build();
        given(memberRepository.findById(7L)).willReturn(Optional.of(member));

        Optional<NotificationRecipient> result = reader.findByMemberId(7L);

        assertThat(result).isPresent();
        assertThat(result.get().memberId()).isEqualTo(7L);
        assertThat(result.get().phoneNumber()).isEqualTo("01099998888");
        assertThat(result.get().kakaoProviderId()).isEqualTo("kakao_777");
    }
}
