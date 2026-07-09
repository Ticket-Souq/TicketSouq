package org.ticketsouq.notificationservice.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "user_email_projection")
public class UserEmailProjection {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(nullable = false, unique = true)
    private String email;
}
