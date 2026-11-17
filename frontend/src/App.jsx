import { useState } from 'react';
import { consultarContribuyente, consultarLicencia, consultarVehiculo } from './api.js';
import TablaClaveValor from './components/TablaClaveValor.jsx';
import Licencia from './components/Licencia.jsx';

const INICIAL = { cargando: false, error: null, datos: null };

function Mensaje({ estado, textoCarga }) {
  if (estado.cargando) {
    return (
      <p className="cargando" role="status">
        <span className="spinner" aria-hidden="true" /> {textoCarga}
      </p>
    );
  }
  if (estado.error) {
    return (
      <p className="error" role="alert">
        {estado.error}
      </p>
    );
  }
  return null;
}

export default function App() {
  const [email, setEmail] = useState('');
  const [ruc, setRuc] = useState('');
  const [placa, setPlaca] = useState('');

  const [contribuyente, setContribuyente] = useState(INICIAL);
  const [vehiculo, setVehiculo] = useState(INICIAL);
  const [licencia, setLicencia] = useState(INICIAL);

  async function cargarLicencia(cedula) {
    setLicencia({ cargando: true, error: null, datos: null });
    try {
      const datos = await consultarLicencia(cedula);
      setLicencia({ cargando: false, error: null, datos });
    } catch (e) {
      setLicencia({ cargando: false, error: e.message, datos: null });
    }
  }

  async function onConsultarContribuyente(e) {
    e.preventDefault();
    const rucLimpio = ruc.trim();
    setContribuyente({ cargando: true, error: null, datos: null });
    setVehiculo(INICIAL);
    setLicencia(INICIAL);
    try {
      const datos = await consultarContribuyente(email.trim(), rucLimpio);
      setContribuyente({ cargando: false, error: null, datos });
      // RUC validado: la licencia se consulta automáticamente, en paralelo al paso de la placa.
      cargarLicencia(datos.cedula ?? rucLimpio.slice(0, 10));
    } catch (err) {
      setContribuyente({ cargando: false, error: err.message, datos: null });
    }
  }

  async function onConsultarVehiculo(e) {
    e.preventDefault();
    setVehiculo({ cargando: true, error: null, datos: null });
    try {
      const datos = await consultarVehiculo(placa.trim());
      setVehiculo({ cargando: false, error: null, datos });
    } catch (err) {
      setVehiculo({ cargando: false, error: err.message, datos: null });
    }
  }

  const validado = Boolean(contribuyente.datos);

  return (
    <main className="contenedor">
      <header className="encabezado">
        <h1>Consulta Vehicular</h1>
        <p>Contribuyente (SRI), vehículo (SRI) y puntos de licencia (ANT) en un solo lugar.</p>
      </header>

      <section className="tarjeta">
        <h2>
          <span className="paso">1</span> Contribuyente
        </h2>
        <form onSubmit={onConsultarContribuyente} className="formulario">
          <label>
            Correo electrónico
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="nombre@dominio.com"
              autoComplete="email"
              required
            />
          </label>
          <label>
            RUC (persona natural)
            <input
              type="text"
              inputMode="numeric"
              value={ruc}
              onChange={(e) => setRuc(e.target.value.replace(/\D/g, '').slice(0, 13))}
              placeholder="1710034065001"
              pattern="\d{13}"
              title="El RUC debe tener 13 dígitos"
              required
            />
          </label>
          <button type="submit" disabled={contribuyente.cargando}>
            {contribuyente.cargando ? 'Consultando…' : 'Verificar en el SRI'}
          </button>
        </form>
        <Mensaje estado={contribuyente} textoCarga="Consultando al SRI…" />
        {contribuyente.datos && <TablaClaveValor datos={contribuyente.datos.contribuyente} />}
      </section>

      <section className={`tarjeta ${validado ? '' : 'deshabilitada'}`} aria-disabled={!validado}>
        <h2>
          <span className="paso">2</span> Vehículo
        </h2>
        {!validado && <p className="vacio">Primero verifique el RUC del contribuyente.</p>}
        {validado && (
          <>
            <form onSubmit={onConsultarVehiculo} className="formulario en-linea">
              <label>
                Placa
                <input
                  type="text"
                  value={placa}
                  onChange={(e) => setPlaca(e.target.value.toUpperCase())}
                  placeholder="PBA1234"
                  maxLength={12}
                  required
                />
              </label>
              <button type="submit" disabled={vehiculo.cargando}>
                {vehiculo.cargando ? 'Consultando…' : 'Consultar vehículo'}
              </button>
            </form>
            <Mensaje estado={vehiculo} textoCarga="Consultando matriculación en el SRI…" />
            {vehiculo.datos && <TablaClaveValor datos={vehiculo.datos} />}
          </>
        )}
      </section>

      <section className={`tarjeta ${validado ? '' : 'deshabilitada'}`} aria-disabled={!validado}>
        <h2>
          <span className="paso">3</span> Licencia (ANT)
        </h2>
        {!validado && <p className="vacio">Se consultará automáticamente al verificar el RUC.</p>}
        <Mensaje
          estado={licencia}
          textoCarga="Consultando puntos en la ANT… (puede tardar si la ANT está lenta)"
        />
        {licencia.error && validado && (
          <button type="button" className="secundario" onClick={() => cargarLicencia(contribuyente.datos.cedula)}>
            Reintentar
          </button>
        )}
        {licencia.datos && <Licencia resultado={licencia.datos} />}
      </section>

      <footer className="pie">
        ISWZ2202 · Diseño y Arquitectura de Software · El correo no se almacena.
      </footer>
    </main>
  );
}
