import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';

/// Servicios SeeProfile / EditProfile: datos del usuario y cambio de contrasena.
class PerfilScreen extends StatefulWidget {
  const PerfilScreen({super.key});

  @override
  State<PerfilScreen> createState() => _PerfilScreenState();
}

class _PerfilScreenState extends State<PerfilScreen> {
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  bool _guardando = false;

  Future<void> _cambiar() async {
    final u = Api.instance.usuario!;
    if (_nueva.text.length < 8) {
      avisar(context, 'La nueva contraseña debe tener al menos 8 caracteres con letras y números', error: true);
      return;
    }
    setState(() => _guardando = true);
    try {
      await Api.instance.put('/EditUser', {
        'usuarioId': u.id,
        'nombre': u.nombre,
        'email': u.email,
        'password': _nueva.text,
        'passwordActual': _actual.text,
      });
      _actual.clear();
      _nueva.clear();
      if (mounted) avisar(context, 'Contraseña actualizada');
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final u = Api.instance.usuario!;
    return ListView(padding: const EdgeInsets.all(16), children: [
      const Center(child: Logo(height: 80)),
      const SizedBox(height: 16),
      Card(
        child: Column(children: [
          ListTile(leading: const Icon(Icons.person), title: Text(u.nombre), subtitle: const Text('Nombre')),
          ListTile(leading: const Icon(Icons.mail), title: Text(u.email), subtitle: const Text('Correo')),
          ListTile(leading: const Icon(Icons.badge), title: Text(u.rolNombre), subtitle: const Text('Rol')),
        ]),
      ),
      const SizedBox(height: 16),
      Text('Cambiar contraseña', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 8),
      TextField(controller: _actual, obscureText: true, decoration: const InputDecoration(labelText: 'Contraseña actual')),
      const SizedBox(height: 12),
      TextField(controller: _nueva, obscureText: true, decoration: const InputDecoration(labelText: 'Nueva contraseña')),
      const SizedBox(height: 12),
      FilledButton(onPressed: _guardando ? null : _cambiar, child: const Text('Guardar')),
      const SizedBox(height: 24),
      Text('Servidor: $apiUrl', style: const TextStyle(fontSize: 11, color: Colors.black45), textAlign: TextAlign.center),
    ]);
  }
}
