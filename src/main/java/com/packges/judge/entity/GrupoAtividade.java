package com.packges.judge.entity;

import com.packges.judge.enums.StatusGrupo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "grupo_atividade")
@Getter
@Setter
@NoArgsConstructor
public class GrupoAtividade {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criador_id", nullable = false)
    private Usuario criador;

    @Column(nullable = false)
    private String nome;

    @Column(columnDefinition = "text")
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusGrupo status;

    private Instant dataInicio;

    private Instant dataFim;

    /** Token do link de convite: único e gerado uma única vez na criação do grupo. */
    @Column(nullable = false, unique = true, updatable = false)
    private String codigoConvite;
}
