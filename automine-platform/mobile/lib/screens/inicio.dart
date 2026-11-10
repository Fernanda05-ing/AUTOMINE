import 'dart:async';

import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';
import 'chat.dart';
import 'vacaciones.dart';
import 'asistencia.dart';
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
  int _noLeidos = 0;
  Timer? _timer;
  late final Widget _chat = ChatScreen(onNoLeidos: _setNoLeidos);

  @override
  void initState() {
    super.initState();
    _contarNoLeidos();
    _timer = Timer.periodic(const Duration(seconds: 20), (_) => _contarNoLeidos());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _setNoLeidos(int n) {
    if (mounted && n != _noLeidos) setState(() => _noLeidos = n);
  }

  Future<void> _contarNoLeidos() async {
    if (Api.instance.usuario == null) return;
    try {
      final r = await Api.instance.get('/Chat/Unread');
      _setNoLeidos((r['noLeidos'] ?? 0) as int);
    } catch (_) {}
  }

  List<_Seccion> _secciones(Usuario u) => [
        if (u.tiene(['SGSST'])) _Seccion('Asistencia', Icons.how_to_reg_outlined, const AsistenciaScreen()),
        if (u.empleadoId != null) _Seccion('Mis pagos', Icons.payments_outlined, PagosScreen(empleadoId: u.empleadoId!)),
        if (u.empleadoId != null)
          _Seccion('Certificados', Icons.workspace_premium_outlined, CertificadosScreen(empleadoId: u.empleadoId!)),
        if (u.empleadoId != null) _Seccion('Turnos', Icons.schedule, TurnosScreen(empleadoId: u.empleadoId!)),
        if (u.empleadoId != null) _Seccion('Novedades', Icons.event_note_outlined, const NovedadesScreen()),
        if (u.tiene(['SUPERVISOR', 'GERENTE'])) _Seccion('Producción', Icons.landscape_outlined, const ProduccionScreen()),
        _Seccion('Mensajes', Icons.forum_outlined, _chat),
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
            tooltip: 'Mi perfil',
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                builder: (_) => Scaffold(appBar: AppBar(title: const Text('Mi perfil')), body: const PerfilScreen()))),
          ),
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
        labelBehavior: secciones.length > 5
            ? NavigationDestinationLabelBehavior.onlyShowSelected
            : NavigationDestinationLabelBehavior.alwaysShow,
        destinations: secciones
            .map((s) => NavigationDestination(
                  icon: s.pantalla == _chat && _noLeidos > 0
                      ? Badge(label: Text('$_noLeidos'), backgroundColor: colorPrimario, child: Icon(s.icono))
                      : Icon(s.icono),
                  label: s.titulo,
                ))
            .toList(),
      ),
    );
  }
}
