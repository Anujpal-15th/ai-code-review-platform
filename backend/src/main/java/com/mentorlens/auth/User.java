package com.mentorlens.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id @GeneratedValue UUID id;
    @Column(unique = true, nullable = false) String email;
    @Column(nullable = false) String passwordHash;
    // both nullable: rows from before the pilot have neither
    @Enumerated(EnumType.STRING) PilotGroup pilotGroup; // set only by scripts/set-pilot-group.sql
    Instant consentedAt;

    protected User() {}

    public User(String email, String passwordHash) {
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UUID getId() { return id; }
    public PilotGroup getPilotGroup() { return pilotGroup; }
    public void setPilotGroup(PilotGroup pilotGroup) { this.pilotGroup = pilotGroup; }
    public Instant getConsentedAt() { return consentedAt; }
    public void setConsentedAt(Instant consentedAt) { this.consentedAt = consentedAt; }
}
