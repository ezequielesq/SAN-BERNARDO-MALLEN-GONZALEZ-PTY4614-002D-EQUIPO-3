import { HttpErrorResponse } from '@angular/common/http';

export interface ErrorTraducido {
  status: number | null;
  mensaje: string;
  campo: string | null;
  camposValidacion: Record<string, string>;
}

export const MENSAJE_GENERICO =
  'Ocurrió un error inesperado. Intenta de nuevo; si continúa, avisa al administrador.';

export function traducirError(err: unknown): ErrorTraducido {
  if (!(err instanceof HttpErrorResponse)) {
    return { status: null, mensaje: MENSAJE_GENERICO, campo: null, camposValidacion: {} };
  }
  const cuerpo: unknown = err.error;
  const base = { status: err.status, campo: null, camposValidacion: {} };
  switch (err.status) {
    case 0:
      return {
        ...base,
        mensaje: 'No se pudo conectar con el servidor. Revisa tu conexión e intenta de nuevo.',
      };
    case 400:
      return {
        ...base,
        mensaje: 'Revisa los campos marcados.',
        camposValidacion: leerCampos(cuerpo),
      };
    case 401:
      return { ...base, mensaje: 'Tu sesión expiró. Vuelve a iniciar sesión.' };
    case 403:
      return { ...base, mensaje: 'No tienes permiso para esta acción.' };
    case 404:
      return { ...base, mensaje: 'El registro que buscas ya no existe.' };
    case 422:
      return {
        ...base,
        mensaje: leerTexto(cuerpo, 'message') ?? MENSAJE_GENERICO,
        campo: leerTexto(cuerpo, 'field'),
      };
    default:
      return { ...base, mensaje: MENSAJE_GENERICO };
  }
}

function leerTexto(cuerpo: unknown, clave: string): string | null {
  if (typeof cuerpo !== 'object' || cuerpo === null) return null;
  const valor = (cuerpo as Record<string, unknown>)[clave];
  return typeof valor === 'string' && valor.trim() !== '' ? valor : null;
}

function leerCampos(cuerpo: unknown): Record<string, string> {
  if (typeof cuerpo !== 'object' || cuerpo === null) return {};
  const campos = (cuerpo as Record<string, unknown>)['fields'];
  if (typeof campos !== 'object' || campos === null) return {};
  const resultado: Record<string, string> = {};
  for (const [clave, valor] of Object.entries(campos)) {
    if (typeof valor === 'string') resultado[clave] = valor;
  }
  return resultado;
}
