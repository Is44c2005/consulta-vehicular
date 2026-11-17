// Tabla tolerante: muestra solo los campos simples (texto, número, booleano) de un objeto,
// ignorando nulos, vacíos, objetos anidados y listas. Así no se rompe si el SRI agrega campos.

function etiqueta(clave) {
  const texto = clave
    .replace(/([a-z])([A-Z])/g, '$1 $2')
    .replace(/[_-]+/g, ' ')
    .trim();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

function formatear(valor) {
  if (typeof valor === 'boolean') return valor ? 'Sí' : 'No';
  return String(valor);
}

export function camposSimples(objeto) {
  if (!objeto || typeof objeto !== 'object') return [];
  return Object.entries(objeto).filter(
    ([, v]) =>
      v !== null &&
      v !== undefined &&
      ['string', 'number', 'boolean'].includes(typeof v) &&
      String(v).trim() !== '',
  );
}

export default function TablaClaveValor({ datos }) {
  const filas = camposSimples(datos);
  if (filas.length === 0) {
    return <p className="vacio">No hay datos para mostrar.</p>;
  }
  return (
    <table className="tabla kv">
      <tbody>
        {filas.map(([clave, valor]) => (
          <tr key={clave}>
            <th scope="row">{etiqueta(clave)}</th>
            <td>{formatear(valor)}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
