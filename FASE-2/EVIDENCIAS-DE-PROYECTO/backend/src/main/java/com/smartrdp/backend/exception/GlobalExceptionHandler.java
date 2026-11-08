package com.smartrdp.backend.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    record ErrorResponse(int status, String error, String message, String field, LocalDateTime timestamp) {}

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ErrorResponse handleBadCredentials(BadCredentialsException ex) {
        return new ErrorResponse(401, "Unauthorized", "Credenciales inválidas", null, LocalDateTime.now());
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleNotFound(ResourceNotFoundException ex) {
        return new ErrorResponse(404, "Not Found", ex.getMessage(), null, LocalDateTime.now());
    }

    @ExceptionHandler(BusinessException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleBusiness(BusinessException ex) {
        return new ErrorResponse(422, "Business Error", ex.getMessage(), ex.getCampo(), LocalDateTime.now());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErrorResponse handleArchivoGrande(MaxUploadSizeExceededException ex) {
        return new ErrorResponse(422, "Business Error", "El archivo supera el tamaño máximo de 2 MB.", null, LocalDateTime.now());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        return Map.of(
            "status", 400,
            "error", "Validation Failed",
            "fields", fieldErrors,
            "timestamp", LocalDateTime.now().toString()
        );
    }

    /** Parámetro faltante o de tipo equivocado, o cuerpo JSON ilegible: error del cliente, no del servidor. */
    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleSolicitudInvalida(Exception ex) {
        log.debug("Solicitud inválida: {}", ex.getMessage());
        return new ErrorResponse(400, "Bad Request", "Solicitud inválida: revisa los datos enviados.", null,
                LocalDateTime.now());
    }

    /**
     * Errores de la base. Los RAISE EXCEPTION de los triggers (SQLSTATE P0001) traen un mensaje ya legible y
     * son reglas de negocio: se responden 422. Las claves únicas del libro que protegen contra la doble
     * aprobación o doble corrección también. Lo demás es un error interno.
     */
    @ExceptionHandler({DataAccessException.class, TransactionSystemException.class})
    public ResponseEntity<ErrorResponse> handleBaseDeDatos(Exception ex) {
        // Interbloqueo (40P01), fallo de serialización (40001) o espera de bloqueo agotada (55P03): la
        // operación chocó con otra concurrente y no se aplicó nada; el usuario puede reintentar.
        SQLException choque = causaSql(ex, "40P01", "40001", "55P03");
        if (choque != null) {
            log.warn("Operación abortada por concurrencia (SQLSTATE {}): {}", choque.getSQLState(),
                    mensajeLegible(choque.getMessage()));
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(409, "Conflict",
                    "La operación chocó con otra en curso. Intenta de nuevo.", null, LocalDateTime.now()));
        }
        SQLException sql = causaSql(ex);
        if (sql != null) {
            String mensaje = null;
            if ("P0001".equals(sql.getSQLState())) {
                mensaje = mensajeLegible(sql.getMessage());
                // Un trigger del esquema rechazó la escritura: si es de inmutabilidad, es un error de programación.
                log.warn("Trigger de la base rechazó la operación (P0001): {}", mensaje);
            } else if ("23505".equals(sql.getSQLState())) {
                String detalle = String.valueOf(sql.getMessage());
                if (detalle.contains("uq_operaciones_solicitud")) mensaje = "La solicitud ya fue procesada.";
                if (detalle.contains("uq_operaciones_corregida")) mensaje = "Esta operación ya fue corregida.";
            }
            if (mensaje != null) {
                return ResponseEntity.unprocessableEntity()
                        .body(new ErrorResponse(422, "Business Error", mensaje, null, LocalDateTime.now()));
            }
        }
        log.error("Error de base de datos no controlado", ex);
        return ResponseEntity.internalServerError().body(new ErrorResponse(
                500, "Internal Server Error", "Error interno del servidor", null, LocalDateTime.now()));
    }

    private static SQLException causaSql(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) return sql;
        }
        return null;
    }

    /** Primera SQLException de la cadena de causas (incluidas las encadenadas con getNextException) con uno de esos estados. */
    private static SQLException causaSql(Throwable ex, String... estados) {
        java.util.Set<String> buscados = java.util.Set.of(estados);
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                for (SQLException s = sql; s != null; s = s.getNextException()) {
                    if (s.getSQLState() != null && buscados.contains(s.getSQLState())) return s;
                }
            }
        }
        return null;
    }

    /** PSQLException trae "ERROR: mensaje" seguido de "Where: ..."; el usuario solo ve el mensaje. */
    private static String mensajeLegible(String mensaje) {
        String primera = mensaje == null ? "" : mensaje.lines().findFirst().orElse(mensaje);
        return primera.startsWith("ERROR: ") ? primera.substring("ERROR: ".length()) : primera;
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return new ErrorResponse(500, "Internal Server Error", "Error interno del servidor", null, LocalDateTime.now());
    }
}
