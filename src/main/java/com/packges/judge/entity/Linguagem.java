package com.packges.judge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "linguagem")
@Getter
@Setter
@NoArgsConstructor
public class Linguagem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String nome;

    @Column(nullable = false)
    private String versao;

    /** Fila RabbitMQ para onde as soluções desta linguagem são publicadas (ex.: submissions.cpp). */
    @Column(nullable = false)
    private String fila;

    @Column(nullable = false)
    private boolean ativa;
}
