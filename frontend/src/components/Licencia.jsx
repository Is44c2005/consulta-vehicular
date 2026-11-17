function badge(resultado) {
  if (resultado.fuente === 'ANT') return { texto: 'Dato en vivo (ANT)', clase: 'badge vivo' };
  if (resultado.desactualizado) return { texto: 'Caché desactualizada', clase: 'badge viejo' };
  return { texto: 'Caché reciente', clase: 'badge reciente' };
}

function formatearFecha(iso) {
  if (!iso) return '';
  const fecha = new Date(iso);
  if (Number.isNaN(fecha.getTime())) return iso;
  return fecha.toLocaleString('es-EC', { dateStyle: 'medium', timeStyle: 'short' });
}

function formatearPuntos(puntos) {
  if (puntos === null || puntos === undefined) return '—';
  return Number.isInteger(puntos) ? String(puntos) : puntos.toLocaleString('es-EC');
}

export default function Licencia({ resultado }) {
  const { datos, consultadoEn, aviso } = resultado;
  const b = badge(resultado);
  const citaciones = datos?.citaciones ?? [];
  const columnas = [...new Set(citaciones.flatMap((c) => Object.keys(c)))];

  return (
    <div className="licencia">
      <div className="licencia-cabecera">
        <div className="puntos">
          <span className="puntos-valor">{formatearPuntos(datos?.puntos)}</span>
          <span className="puntos-etiqueta">puntos</span>
        </div>
        <div className="licencia-meta">
          <span className={b.clase}>{b.texto}</span>
          <span className="fecha">Consultado: {formatearFecha(consultadoEn)}</span>
          <span className="fecha">Cédula: {datos?.cedula}</span>
        </div>
      </div>

      {aviso && (
        <p className={resultado.desactualizado ? 'aviso advertencia' : 'aviso'} role="status">
          {aviso}
        </p>
      )}

      <h3>Citaciones</h3>
      {citaciones.length === 0 ? (
        <p className="vacio">No registra citaciones.</p>
      ) : (
        <div className="tabla-scroll">
          <table className="tabla">
            <thead>
              <tr>
                {columnas.map((c) => (
                  <th key={c}>{c}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {citaciones.map((fila, i) => (
                <tr key={i}>
                  {columnas.map((c) => (
                    <td key={c}>{fila[c] ?? ''}</td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
