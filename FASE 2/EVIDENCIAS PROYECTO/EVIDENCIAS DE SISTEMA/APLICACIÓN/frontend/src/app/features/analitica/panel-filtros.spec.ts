import { decidirMedida, diasDelRango, rangoDeAtajo, sugerirAgrupacion, validarRango } from './panel-filtros';

describe('atajos de fecha', () => {
  const hoy = new Date(2026, 10, 24); // 24-11-2026

  it('sugiere día para 7 días, semana para 30, mes para 90 y día para el mes actual', () => {
    expect(sugerirAgrupacion('7d')).toBe('DIA');
    expect(sugerirAgrupacion('30d')).toBe('SEMANA');
    expect(sugerirAgrupacion('90d')).toBe('MES');
    expect(sugerirAgrupacion('mes')).toBe('DIA');
  });

  it('calcula el rango de cada atajo contando el día de hoy', () => {
    expect(rangoDeAtajo('7d', hoy)).toEqual({ desde: '2026-11-18', hasta: '2026-11-24' });
    expect(rangoDeAtajo('30d', hoy)).toEqual({ desde: '2026-10-26', hasta: '2026-11-24' });
    expect(rangoDeAtajo('90d', hoy)).toEqual({ desde: '2026-08-27', hasta: '2026-11-24' });
    expect(rangoDeAtajo('mes', hoy)).toEqual({ desde: '2026-11-01', hasta: '2026-11-24' });
  });
});

describe('validarRango', () => {
  it('acepta un rango válido, incluso de un solo día', () => {
    expect(validarRango('2026-11-01', '2026-11-24')).toBeNull();
    expect(validarRango('2026-11-24', '2026-11-24')).toBeNull();
  });

  it('pide las dos fechas', () => {
    expect(validarRango('', '2026-11-24')).toContain('fechas');
    expect(validarRango('2026-11-01', '')).toContain('fechas');
  });

  it('rechaza «desde» posterior a «hasta»', () => {
    expect(validarRango('2026-11-25', '2026-11-24')).toContain('posterior');
  });

  it('acepta hasta 366 días contando ambos extremos y rechaza más', () => {
    expect(diasDelRango('2026-01-01', '2027-01-01')).toBe(366);
    expect(validarRango('2026-01-01', '2027-01-01')).toBeNull();
    expect(validarRango('2026-01-01', '2027-01-02')).toContain('366');
  });
});

describe('decidirMedida', () => {
  it('respeta la preferencia cuando se puede', () => {
    expect(decidirMedida('valor', null)).toEqual({ medida: 'valor', aviso: null });
    expect(decidirMedida('cantidad', 'kg')).toEqual({ medida: 'cantidad', aviso: null });
  });

  it('con unidades mezcladas pasa a valor y lo avisa', () => {
    const r = decidirMedida('cantidad', null);
    expect(r.medida).toBe('valor');
    expect(r.aviso).toContain('unidades distintas');
  });
});
