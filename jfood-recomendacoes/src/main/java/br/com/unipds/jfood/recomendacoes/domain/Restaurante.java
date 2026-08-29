package br.com.unipds.jfood.recomendacoes.domain;

import java.util.List;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

/**
 * O id vem do MongoDB. O grafo REFERENCIA os outros bancos; nao duplica o
 * dominio -- so guarda o nome, para o Neo4J Browser ficar legivel.
 *
 * Se o preco, a taxa ou o horario tambem morassem aqui, existiriam TRES copias
 * do catalogo (Mongo, cache do Redis e grafo) e tres oportunidades de divergir.
 */
@Node
public record Restaurante(

        @Id String id,
        String nome,

        @Relationship(type = "DO_TIPO", direction = Relationship.Direction.OUTGOING)
        Categoria categoria
) {}
