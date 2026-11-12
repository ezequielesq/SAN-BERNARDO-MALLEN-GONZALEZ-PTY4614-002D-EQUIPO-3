package com.smartrdp.backend.producto.dto;

import java.util.Map;

/** Una fila con sus problemas por campo (campo → mensaje); sin errores, se puede crear. */
public record FilaValidada(FilaImportacion datos, Map<String, String> errores) {}
