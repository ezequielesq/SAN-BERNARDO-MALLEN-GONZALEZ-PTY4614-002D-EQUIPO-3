export interface Evento {
  id: number;
  titulo: string;
  /** Fecha del evento, formato yyyy-MM-dd. */
  fecha: string;
  /** Hora del evento, formato HH:mm:ss; null si no tiene hora. */
  hora: string | null;
  descripcion: string | null;
  creadoPorNombre: string;
}

export interface EventoRequest {
  titulo: string;
  fecha: string;
  hora: string | null;
  descripcion: string | null;
}
