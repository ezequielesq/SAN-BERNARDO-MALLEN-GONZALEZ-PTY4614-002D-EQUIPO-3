package com.smartrdp.backend.producto.dto;

/**
 * Una fila de la importación masiva. Categoría y unidad van por id; los textos son lo que decía el Excel
 * y solo sirven para mostrarlo cuando el nombre no se pudo resolver.
 */
public record FilaImportacion(
        Integer fila,
        String codigoPtv,
        String codigo,
        String nombre,
        Long categoriaId,
        Long unidadMedidaId,
        String categoriaTexto,
        String unidadTexto,
        boolean esPerecible,
        Integer stockMinimo,
        Integer stockCritico,
        Integer costoUnitario
) {}
