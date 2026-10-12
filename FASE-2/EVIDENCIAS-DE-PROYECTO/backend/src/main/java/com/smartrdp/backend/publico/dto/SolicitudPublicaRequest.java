package com.smartrdp.backend.publico.dto;

import com.smartrdp.backend.solicitud.dto.SolicitudRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SolicitudPublicaRequest(
        @NotNull Long empleadoId,
        @NotBlank String password,
        @NotEmpty List<SolicitudRequest.@Valid Item> items
) {}
