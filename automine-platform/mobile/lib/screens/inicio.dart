import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';
import 'login.dart';
import 'pagos.dart';
import 'perfil.dart';
import 'produccion.dart';

class _Seccion {
  final String titulo;
  final IconData icono;
  final Widget pantalla;
  _Seccion(this.titulo, this.icono, this.pantalla);
}

/// Navegacion principal. Las secciones dependen del rol (empleado, supervisor, gerente...).
class InicioScreen extends StatefulWidget {
  const InicioScreen({super.key});

  @override
  State<InicioScreen> createState() => _InicioScreenState();
}

class _InicioScreenState extends State<InicioScreen> {
  int _actual = 0;

  List<_Seccion> _secciones(Usuario u) => [
        if (u.empleadoId != null) _Seccion('Mis pagos', Icons.payments_outlined, PagosScreen(empleadoId: u.empleadoId!)),
        if (u.empleadoId != null)
          _Seccion('Certificados', Icons.workspace_premium_outlined, CertificadosScreen(empleadoId: u.empleadoId!)),
        if (u.empleadoId != null) _Seccion('Turnos', Icons.schedule, TurnosScreen(empleadoId: u.empleadoId!)),
        if (u.tiene(['SUPERVISOR', 'GERENTE'])) _Seccion('Producción', Icons.landscape_outlined, const ProduccionScreen()),
        _Seccion('Perfil', Icons.person_outline, const PerfilScreen()),
      ];

  @override
  Widget build(BuildContext context) {
    final u = Api.instance.usuario;
    if (u == null) return const LoginScreen();
    final secciones = _secciones(u);
    final i = _actual.clamp(0, secciones.length - 1);
    return Scaffold(
      appBar: AppBar(
        title: Text(secciones[i].titulo),
        actions: [
          IconButton(
            tooltip: 'Cerrar sesión',
            icon: const Icon(Icons.logout),
            onPressed: () async {
              await Api.instance.logout();
              if (!context.mounted) return;
              Navigator.of(context)
                  .pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
            },
          ),
        ],
      ),
      body: IndexedStack(index: i, children: secciones.map((s) => s.pantalla).toList()),
      bottomNavigationBar: NavigationBar(
        selectedIndex: i,
        indicatorColor: colorDorado.withValues(alpha: 0.35),
        onDestinationSelected: (n) => setState(() => _actual = n),
        destinations: secciones.map((s) => NavigationDestination(icon: Icon(s.icono), label: s.titulo)).toList(),
      ),
    );
  }
}
