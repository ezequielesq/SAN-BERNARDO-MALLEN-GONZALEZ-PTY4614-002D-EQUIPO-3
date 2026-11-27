package com.smartrdp.backend.analitica.dto;

/** Un producto del ranking de más usados: cantidad y valor netos de devoluciones. */
public record ProductoTopDto(Long productoId, String nombre, String unidad, long cantidad, long valor) {}
