// En desarrollo VITE_API_URL está vacío y las llamadas van a /api (proxy de Vite).
const BASE_URL = (import.meta.env.VITE_API_URL ?? '').replace(/\/$/, '');

async function request(path, options = {}) {
  let response;
  try {
    response = await fetch(`${BASE_URL}${path}`, {
      headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
      ...options,
    });
  } catch {
    throw new Error('No se pudo conectar con el servidor. Verifique su conexión e intente nuevamente.');
  }

  let body = null;
  try {
    body = await response.json();
  } catch {
    body = null;
  }

  if (!response.ok) {
    // Los errores llegan como ProblemDetail: se muestra el campo "detail".
    const mensaje = body?.detail || body?.title || `Error ${response.status} al realizar la consulta.`;
    const error = new Error(mensaje);
    error.status = response.status;
    throw error;
  }
  return body;
}

export function consultarContribuyente(email, ruc) {
  return request('/api/consultas/contribuyente', {
    method: 'POST',
    body: JSON.stringify({ email, ruc }),
  });
}

export function consultarVehiculo(placa) {
  return request(`/api/consultas/vehiculos/${encodeURIComponent(placa)}`);
}

export function consultarLicencia(cedula) {
  return request(`/api/consultas/licencias/${encodeURIComponent(cedula)}`);
}
