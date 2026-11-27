package com.smartrdp.backend.movimiento.dto;

import java.util.List;

/** El lote y su historia completa, del primer movimiento al último. */
public record LoteDetalleResponse(LoteResponse lote, List<MovimientoLoteResponse> trazabilidad) {}
