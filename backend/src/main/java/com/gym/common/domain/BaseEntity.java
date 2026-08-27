package com.gym.common.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.OffsetDateTime;

/**
 * Cột dùng chung cho mọi bảng nghiệp vụ.
 *
 * <p>{@code createdAt} và {@code updatedAt} do CSDL quản lý (DEFAULT now() và
 * trigger {@code set_updated_at}), nên đánh dấu không cho Hibernate ghi —
 * chỉ đọc lại giá trị CSDL sinh ra.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", insertable = false, updatable = false)
    @Generated(event = EventType.INSERT)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    private OffsetDateTime updatedAt;

    /** Xóa mềm. {@code null} = bản ghi còn dùng. */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;
}
