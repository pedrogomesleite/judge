package com.packges.judge.entity;

import com.packges.judge.enums.StatusSolucao;
import com.packges.judge.enums.Veredito;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "solucao")
@Getter
@Setter
@NoArgsConstructor
public class Solucao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atividade_id", nullable = false)
    private Atividade atividade;

    /** Opcional: nulo quando a solução é enviada fora de um grupo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "grupo_id")
    private GrupoAtividade grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "linguagem_id", nullable = false)
    private Linguagem linguagem;

    @Column(nullable = false, columnDefinition = "text")
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusSolucao status;

    @Enumerated(EnumType.STRING)
    private Veredito veredito;

    private Integer tempoMs;

    private Integer memoriaKb;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant enviadoEm;

    /** Momento em que o broker confirmou a publicação. Nulo = ainda não publicada (ADR-007). */
    private Instant publicadaEm;
}
