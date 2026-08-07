package com.first.app.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "test_entities")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
class TestEntity extends BaseEntity {

    private String name;
}
