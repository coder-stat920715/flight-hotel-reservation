package com.architecture.flighthotelquery.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Minimal read-only entity used as the aggregate type for the native budget repository. */
@Entity
@Table(name = "airports")
public class Airport {
    @Id
    @Column(name = "airport_id")
    private Long id;
    @Column(name = "airport_code")
    private String code;

    protected Airport() {
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
}
