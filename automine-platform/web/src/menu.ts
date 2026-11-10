import {
  Banknote,
  BarChart3,
  Boxes,
  Calculator,
  FileBadge,
  HardHat,
  LayoutDashboard,
  MessagesSquare,
  type LucideIcon,
  Mountain,
  Settings,
  UserRound,
  Users,
  Wallet,
} from 'lucide-react';

// Menu lateral por rol (RF003). ADMIN ve todo.
export interface MenuItem {
  path: string;
  label: string;
  roles: string[];
  section: string;
  icon: LucideIcon;
}

export const MENU: MenuItem[] = [
  { path: '/', label: 'Inicio', roles: ['*'], section: 'General', icon: LayoutDashboard },
  { path: '/mensajes', label: 'Mensajes', roles: ['*'], section: 'General', icon: MessagesSquare },
  { path: '/mi-portal', label: 'Mi portal', roles: ['EMPLEADO', 'SUPERVISOR', 'SGSST', 'RRHH', 'CONTADOR', 'ALMACENISTA', 'GERENTE'], section: 'General', icon: UserRound },
  { path: '/empleados', label: 'Empleados', roles: ['RRHH', 'GERENTE'], section: 'Talento humano', icon: Users },
  { path: '/certificados', label: 'Certificados', roles: ['RRHH', 'GERENTE'], section: 'Talento humano', icon: FileBadge },
  { path: '/nomina', label: 'Nómina', roles: ['CONTADOR', 'RRHH', 'GERENTE'], section: 'Nómina y pagos', icon: Wallet },
  { path: '/consignaciones', label: 'Consignaciones', roles: ['CONTADOR', 'GERENTE'], section: 'Nómina y pagos', icon: Banknote },
  { path: '/produccion', label: 'Producción', roles: ['SUPERVISOR', 'GERENTE'], section: 'Operación', icon: Mountain },
  { path: '/inventario', label: 'Inventario', roles: ['ALMACENISTA', 'GERENTE', 'SGSST', 'SUPERVISOR', 'CONTADOR'], section: 'Operación', icon: Boxes },
  { path: '/sgsst', label: 'SG-SST', roles: ['SGSST', 'GERENTE', 'SUPERVISOR', 'RRHH'], section: 'Operación', icon: HardHat },
  { path: '/contabilidad', label: 'Contabilidad', roles: ['CONTADOR', 'GERENTE'], section: 'Finanzas', icon: Calculator },
  { path: '/reportes', label: 'Reportes', roles: ['GERENTE', 'CONTADOR', 'RRHH', 'SUPERVISOR', 'SGSST', 'ALMACENISTA'], section: 'Finanzas', icon: BarChart3 },
  { path: '/administracion', label: 'Administración', roles: [], section: 'Sistema', icon: Settings },
];

export function allowed(item: MenuItem, rol: string): boolean {
  return rol === 'ADMIN' || item.roles.includes('*') || item.roles.includes(rol);
}
