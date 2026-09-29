# Inventario multi-sucursal — Fase 1

## Alcance
- Sucursales físicas por comercio.
- Migración automática del stock existente a la sucursal `Principal`.
- Stock físico por variante y sucursal.
- Stock total compatible con APIs y storefront existentes.
- Ajustes de stock por sucursal con idempotencia y auditoría.
- Vendedor puede consultar stock de todas las sucursales.
- Encargado puede consultar y ajustar stock.
- Propietario/Administrador pueden crear y editar sucursales.
- Ventas online consumen la sucursal `Principal` durante esta fase.

## Fuera de alcance
- Transferencias entre sucursales.
- POS / venta física.
- Reservas para clientes en otra sucursal.
- Retiro en sucursal y selección automática de fulfillment.

## Compatibilidad
Cada tenant recibe una sucursal `Principal`. El saldo actual de `inventory_balances` se copia a `branch_inventory_balances`, manteniendo `inventory_balances` como agregado compatible. Los flujos existentes que ajustan o consumen stock sincronizan la sucursal Principal.
