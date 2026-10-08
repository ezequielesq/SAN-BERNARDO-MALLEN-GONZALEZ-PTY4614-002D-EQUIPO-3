package com.smartrdp.backend.catalogo.dto;

import jakarta.validation.constraints.NotNull;

public record RequierePtvRequest(@NotNull Boolean requierePtv) {}
