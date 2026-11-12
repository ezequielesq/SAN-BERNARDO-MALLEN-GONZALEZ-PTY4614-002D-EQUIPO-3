package com.smartrdp.backend.movimiento;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface LoteRepository extends JpaRepository<Lote, Long> {

    /** Bloquea (FOR UPDATE) todos los lotes del producto, siempre en orden de id, para serializar las salidas. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.producto.id = :productoId ORDER BY l.id")
    List<Lote> bloquearDelProducto(@Param("productoId") Long productoId);

    /**
     * Bloquea (FOR UPDATE) en UNA sola consulta todos los lotes de varios productos, en orden global de id.
     * Toda operación que mueve stock bloquea así sus lotes antes de leer saldos: con un orden único no hay
     * interbloqueos entre operaciones concurrentes.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.producto.id IN :productoIds ORDER BY l.id")
    List<Lote> bloquearDeProductos(@Param("productoIds") Collection<Long> productoIds);

    /** Igual, para un conjunto de lotes (correcciones, devoluciones). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM Lote l WHERE l.id IN :ids ORDER BY l.id")
    List<Lote> bloquearPorIds(@Param("ids") Collection<Long> ids);
}
