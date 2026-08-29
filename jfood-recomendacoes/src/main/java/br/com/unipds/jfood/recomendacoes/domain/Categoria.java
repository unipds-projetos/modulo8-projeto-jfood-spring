package br.com.unipds.jfood.recomendacoes.domain;

import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node
public record Categoria(@Id String nome) {}
