// Carga datos de DEMOSTRACION en una instancia de Smart RDP usando su API (no toca la base directamente).
//
// Uso (desde cualquier carpeta, con Node 18 o superior):
//   node poblar-demo.mjs                       # contra https://bodega.ezsuarez.org
//   node poblar-demo.mjs --url http://localhost:8080
//   node poblar-demo.mjs --forzar              # corre aunque ya haya productos
//
// Credenciales: nada va escrito en este archivo ni en el repositorio.
//   - Administrador: variables SMARTRDP_ADMIN_EMAIL y SMARTRDP_ADMIN_PASSWORD, o el script las pide.
//   - Cuentas de ejemplo (bodeguero y empleados): usan DEMO_PASSWORD si la defines; si no, se genera
//     una contrasena aleatoria y se muestra UNA vez al final, solo en esta consola.
//
// Crea: 1 bodeguero y 3 empleados, 4 proveedores, 12 productos, entradas de compra con lotes de distintos
// vencimientos y proveedor (para ver FEFO, alertas y la comparación de precios; la leche y el aceite se compran
// a dos proveedores con costos distintos), una salida de consumo y 4 solicitudes (aprobada, aprobada parcial,
// rechazada, pendiente). Si los proveedores ya existen (mismo RUT), se reutilizan. Las fechas se calculan desde hoy.

import readline from "node:readline";
import { randomBytes } from "node:crypto";

const args = process.argv.slice(2);
const forzar = args.includes("--forzar");
const iUrl = args.indexOf("--url");
const URL_BASE = (iUrl >= 0 ? args[iUrl + 1] : process.env.SMARTRDP_URL || "https://bodega.ezsuarez.org").replace(/\/$/, "");
const B = URL_BASE + "/api";

function preguntar(texto, oculto = false) {
  return new Promise((resolve) => {
    const rl = readline.createInterface({ input: process.stdin, output: process.stdout, terminal: true });
    if (oculto) {
      // No se muestra lo que se escribe.
      rl._writeToOutput = (s) => { if (s.startsWith(texto)) process.stdout.write(s); };
    }
    rl.question(texto, (v) => { rl.close(); if (oculto) process.stdout.write("\n"); resolve(v.trim()); });
  });
}

async function call(method, path, body, token) {
  const r = await fetch(B + path, {
    method,
    headers: { "Content-Type": "application/json", ...(token ? { Authorization: "Bearer " + token } : {}) },
    body: body ? JSON.stringify(body) : undefined,
  });
  const t = await r.text();
  let j; try { j = t ? JSON.parse(t) : null; } catch { j = t; }
  if (!r.ok) throw new Error(`${method} ${path} -> ${r.status} ${String(t).slice(0, 200)}`);
  return j;
}

const hoy = new Date();
const dias = (n) => { const d = new Date(Date.UTC(hoy.getFullYear(), hoy.getMonth(), hoy.getDate())); d.setUTCDate(d.getUTCDate() + n); return d.toISOString().slice(0, 10); };

(async () => {
  console.log("Destino:", URL_BASE);
  const adminEmail = process.env.SMARTRDP_ADMIN_EMAIL || (await preguntar("Correo del administrador: "));
  const adminPass = process.env.SMARTRDP_ADMIN_PASSWORD || (await preguntar("Contrasena del administrador: ", true));
  const demoPass = process.env.DEMO_PASSWORD || randomBytes(12).toString("base64url");

  const adm = (await call("POST", "/auth/login", { email: adminEmail, password: adminPass })).token;

  const existentes = await call("GET", "/productos", null, adm);
  const hay = Array.isArray(existentes) ? existentes.length : (existentes?.content?.length ?? 0);
  if (hay > 0 && !forzar) {
    console.error(`La base ya tiene ${hay} producto(s). No se cargo nada para no duplicar. Usa --forzar si de verdad quieres seguir.`);
    process.exit(2);
  }

  const mk = (nombre, email, rol) => call("POST", "/usuarios", { nombre, email, password: demoPass, rol }, adm);
  await mk("Marcela Rojas", "bodega@demo.local", "BODEGUERO");
  await mk("Camila Soto", "camila@demo.local", "EMPLEADO");
  await mk("Diego Pérez", "diego@demo.local", "EMPLEADO");
  await mk("Javiera Muñoz", "javiera@demo.local", "EMPLEADO");
  const bod = (await call("POST", "/auth/login", { email: "bodega@demo.local", password: demoPass })).token;

  const cats = await call("GET", "/categorias", null, bod);
  const unis = await call("GET", "/unidades-medida", null, bod);
  const cat = (n) => cats.find((c) => c.nombre === n).id;
  const uni = (n) => unis.find((u) => u.nombre === n).id;
  const tipos = await call("GET", "/movimientos/tipos", null, bod);

  const prods = [
    ["Aceite de oliva extra virgen 1 L", "Abarrotes", "Litro", false, 6, 3, 6900, "1001"],
    ["Harina sin polvos 25 kg", "Abarrotes", "Kg", false, 4, 2, 21500, "1002"],
    ["Arroz grado 1 1 kg", "Abarrotes", "Kg", false, 20, 10, 1450, "1003"],
    ["Leche entera 1 L", "Lácteos", "Litro", true, 24, 12, 980, "2001"],
    ["Crema de leche 1 L", "Lácteos", "Litro", true, 10, 5, 3200, "2002"],
    ["Queso mantecoso laminado 1 kg", "Lácteos", "Kg", true, 5, 2, 8900, "2003"],
    ["Tomate", "Frutas y verduras", "Kg", true, 15, 8, 1900, "3001"],
    ["Palta Hass", "Frutas y verduras", "Kg", true, 10, 5, 5400, "3002"],
    ["Limón", "Frutas y verduras", "Kg", true, 8, 4, 1700, "3003"],
    ["Agua mineral sin gas 500 ml", "Aguas y bebidas", "Botella", true, 48, 24, 520, "4001"],
    ["Bebida cola 350 ml", "Aguas y bebidas", "Botella", true, 48, 24, 640, "4002"],
    ["Detergente lavaloza 5 L", "Aseo", "Litro", false, 3, 1, 7800, "5001"],
  ];
  const ids = {};
  for (const [nombre, c, u, per, min, crit, costo, codigo] of prods) {
    const p = await call("POST", "/productos", { codigo, nombre, categoriaId: cat(c), unidadMedidaId: uni(u), esPerecible: per, stockMinimo: min, stockCritico: crit, costoUnitario: costo }, bod);
    ids[codigo] = p.id;
  }
  // Proveedores (RUT ficticios pero válidos, con dígito verificador correcto). Si ya existen por RUT, se reutilizan.
  const proveedoresDemo = [
    ["76123456-0", "Distribuidora Norte (demo)"],
    ["77234567-4", "Frutas y Verduras Andes (demo)"],
    ["78345678-8", "Abarrotes del Sur (demo)"],
    ["79456789-1", "Carnes Selectas (demo)"],
  ];
  const normRut = (r) => String(r).replace(/[.\s]/g, "").toUpperCase();
  const yaCargados = await call("GET", "/proveedores?soloActivos=false", null, bod);
  const prov = [];
  for (const [rut, nombre] of proveedoresDemo) {
    let p = yaCargados.find((x) => normRut(x.rut) === normRut(rut));
    if (!p) p = await call("POST", "/proveedores", { rut, nombre }, bod);
    else if (!p.activo) await call("PUT", `/proveedores/${p.id}/reactivar`, null, bod);
    prov.push(p.id);
  }
  const [norte, andes, sur, carnes] = prov;

  const tipoEntrada = tipos.find((t) => t.codigo === "COMPRA");
  const tipoSalida = tipos.find((t) => t.codigo === "CONSUMO_INTERNO");
  const E = tipoEntrada.codigo ?? tipoEntrada.nombre;
  const S = tipoSalida.codigo ?? tipoSalida.nombre;

  // Entradas: lotes con distintos vencimientos para mostrar FEFO y alertas. Cada línea de compra lleva su proveedor.
  // El aceite y la leche se compran a dos proveedores con costos distintos, para la comparación de precios.
  await call("POST", "/movimientos/entradas", { tipo: E, nota: "Compra semanal de abarrotes", lineas: [
    { productoId: ids["1001"], cantidad: 12, numeroLote: "AC-2610", fechaVencimiento: dias(240), costoUnitario: 6900, proveedorId: norte },
    { productoId: ids["1002"], cantidad: 8, numeroLote: "HA-2611", fechaVencimiento: dias(150), costoUnitario: 21500, proveedorId: norte },
    { productoId: ids["1003"], cantidad: 40, numeroLote: "AR-2609", fechaVencimiento: dias(300), costoUnitario: 1450, proveedorId: sur },
    { productoId: ids["5001"], cantidad: 4, numeroLote: "DT-2610", costoUnitario: 7800, proveedorId: sur },
  ] }, bod);
  await call("POST", "/movimientos/entradas", { tipo: E, nota: "Aceite a otro proveedor (más barato)", lineas: [
    { productoId: ids["1001"], cantidad: 6, numeroLote: "AC-2611", fechaVencimiento: dias(260), costoUnitario: 6500, proveedorId: sur },
  ] }, bod);
  await call("POST", "/movimientos/entradas", { tipo: E, nota: "Compra de lácteos (lote antiguo)", lineas: [
    { productoId: ids["2001"], cantidad: 20, numeroLote: "LE-1101", fechaVencimiento: dias(6), costoUnitario: 980, proveedorId: norte },
    { productoId: ids["2002"], cantidad: 12, numeroLote: "CR-1102", fechaVencimiento: dias(12), costoUnitario: 3200, proveedorId: carnes },
    { productoId: ids["2003"], cantidad: 6, numeroLote: "QM-1103", fechaVencimiento: dias(20), costoUnitario: 8900, proveedorId: carnes },
  ] }, bod);
  await call("POST", "/movimientos/entradas", { tipo: E, nota: "Reposición de lácteos (lote nuevo)", lineas: [
    { productoId: ids["2001"], cantidad: 30, numeroLote: "LE-1115", fechaVencimiento: dias(25), costoUnitario: 1040, proveedorId: sur },
  ] }, bod);
  await call("POST", "/movimientos/entradas", { tipo: E, nota: "Feria y bebidas", lineas: [
    { productoId: ids["3001"], cantidad: 25, numeroLote: "TO-1117", fechaVencimiento: dias(5), costoUnitario: 1900, proveedorId: andes },
    { productoId: ids["3002"], cantidad: 9, numeroLote: "PA-1117", fechaVencimiento: dias(4), costoUnitario: 5400, proveedorId: andes },
    { productoId: ids["3003"], cantidad: 14, numeroLote: "LI-1117", fechaVencimiento: dias(9), costoUnitario: 1700, proveedorId: andes },
    { productoId: ids["4001"], cantidad: 96, numeroLote: "AG-2610", fechaVencimiento: dias(400), costoUnitario: 520, proveedorId: norte },
    { productoId: ids["4002"], cantidad: 30, numeroLote: "CO-2611", fechaVencimiento: dias(180), costoUnitario: 640, proveedorId: norte },
  ] }, bod);
  // Salida manual (consumo): reparte por FEFO.
  await call("POST", "/movimientos/salidas", { tipo: S, nota: "Consumo de cocina", lineas: [
    { productoId: ids["2001"], cantidad: 8 },
    { productoId: ids["3001"], cantidad: 10 },
    { productoId: ids["1003"], cantidad: 28 },
    { productoId: ids["4002"], cantidad: 8 },
  ] }, bod);

  // Pedidos desde la vista pública del empleado.
  const emps = await call("GET", "/publico/empleados");
  const emp = (n) => emps.find((e) => e.nombre.startsWith(n));
  const ped = (e, items) => call("POST", "/publico/solicitudes", { empleadoId: emp(e).id, password: demoPass, items: items.map(([c, q]) => ({ productoId: ids[c], cantidad: q })) });
  const s1 = await ped("Camila", [["2001", 10], ["3002", 4], ["1001", 2]]);
  const s2 = await ped("Diego", [["4001", 24], ["3003", 5]]);
  const s3 = await ped("Javiera", [["1002", 2], ["5001", 1]]);
  await ped("Camila", [["3001", 6]]); // queda pendiente
  await call("PUT", `/solicitudes/${s1.id}/aprobar`, { items: [{ productoId: ids["2001"], cantidad: 10 }, { productoId: ids["3002"], cantidad: 3 }] }, bod);
  await call("PUT", `/solicitudes/${s2.id}/aprobar`, null, bod);
  await call("PUT", `/solicitudes/${s3.id}/rechazar`, null, bod);

  console.log("\nListo. Datos de demostracion cargados.");
  console.log("Cuentas de ejemplo (todas con la misma contrasena):");
  console.log("  bodeguero : bodega@demo.local");
  console.log("  empleados : camila@demo.local, diego@demo.local, javiera@demo.local");
  if (!process.env.DEMO_PASSWORD) {
    console.log("Contrasena generada (se muestra solo esta vez, guardala):", demoPass);
  }
})().catch((e) => { console.error("FALLO:", e.message); process.exit(1); });
