package br.com.unipds.jfood.catalogo.domain;

import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;

/** Entradas, Pratos, Bebidas, Sobremesas — a divisao que a tela mostra. */
public record SecaoCardapio(
        @Field("nome") String nome,
        @Field("ordem") int ordem,
        @Field("itens") List<ItemResumo> itens
) {}
