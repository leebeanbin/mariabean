package com.mariabean.reservation.notification.application;

import java.util.Optional;

public interface NotificationRecipientReader {
    Optional<NotificationRecipient> findByMemberId(Long memberId);
}
