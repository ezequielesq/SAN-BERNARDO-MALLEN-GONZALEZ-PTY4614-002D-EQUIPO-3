package com.smartrdp.backend.movimiento.dto;

import jakarta.validation.constraints.Size;

public record CorreccionRequest(@Size(max = 300) String nota) {}
