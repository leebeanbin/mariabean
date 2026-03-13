package com.mariabean.reservation.global.persistence;

import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDateTime;

/**
 * MongoDB 공통 Auditing 필드.
 * Spring Data MongoDB의 @CreatedDate, @LastModifiedDate 사용.
 * 상속하여 사용. Soft Delete 포함.
 */
@Getter
public abstract class BaseMongoTimeEntity {

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    private LocalDateTime deletedAt;

    public void softDelete() {
        this.deletedAt = LocalDateTime.now();
    }

    public boolean isDeleted() {
        return this.deletedAt != null;
    }
}
