package com.packges.judge.entity;

import com.packges.judge.enums.PapelGrupo;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "membro_grupo",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grupo_id", "usuario_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MembroGrupo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoAtividade grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PapelGrupo papel;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant entrouEm;
}
