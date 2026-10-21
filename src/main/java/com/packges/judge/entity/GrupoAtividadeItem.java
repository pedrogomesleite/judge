package com.packges.judge.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.UUID;

@Entity
@Table(name = "grupo_atividade_item",
        uniqueConstraints = @UniqueConstraint(columnNames = {"grupo_id", "atividade_id"}))
@Getter
@Setter
@NoArgsConstructor
public class GrupoAtividadeItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "grupo_id", nullable = false)
    private GrupoAtividade grupo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "atividade_id", nullable = false)
    private Atividade atividade;

    @Column(nullable = false)
    private Integer ordem;

    /** Pontos que um envio aceito vale nesta atividade (1 a 100). */
    @ColumnDefault("1")
    @Column(nullable = false)
    private Integer peso = 1;
}
