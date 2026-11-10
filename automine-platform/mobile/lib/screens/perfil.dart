import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';
import 'chat.dart' show celular10;

/// Servicios SeeProfile / EditProfile: datos del usuario y cambio de contrasena.
class PerfilScreen extends StatefulWidget {
  const PerfilScreen({super.key});

  @override
  State<PerfilScreen> createState() => _PerfilScreenState();
}

class _PerfilScreenState extends State<PerfilScreen> {
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  final _celular = TextEditingController(text: Api.instance.usuario?.telefono ?? '');
  bool _guardando = false;
  bool _guardandoCelular = false;

  @override
  void dispose() {
    _actual.dispose();
    _nueva.dispose();
    _celular.dispose();
    super.dispose();
  }

  Future<void> _guardarCelular() async {
    final u = Api.instance.usuario!;
    final t = _celular.text.trim();
    if (t.isNotEmpty && celular10(t) == null) {
      avisar(context, 'Escriba un celular colombiano de 10 dígitos, por ejemplo 300 123 4567', error: true);
      return;
    }
    setState(() => _guardandoCelular = true);
    try {
      final r = await Api.instance.put('/EditUser', {'usuarioId': u.id, 'nombre': u.nombre, 'email': u.email, 'telefono': t});
      u.telefono = r is Map ? r['telefono'] : t;
      if (mounted) avisar(context, 'Celular actualizado');
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardandoCelular = false);
    }
  }

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
      Text('Celular de contacto (WhatsApp)', style: Theme.of(context).textTheme.titleMedium),
      const SizedBox(height: 4),
      const Text('Se muestra a su supervisor y al personal para contactarlo en casos urgentes.',
          style: TextStyle(fontSize: 12, color: Colors.black54)),
      const SizedBox(height: 8),
      Row(children: [
        Expanded(
          child: TextField(
            controller: _celular,
            keyboardType: TextInputType.phone,
            decoration: const InputDecoration(labelText: 'Celular', hintText: '300 123 4567', prefixIcon: Icon(Icons.phone_android)),
          ),
        ),
        const SizedBox(width: 8),
        FilledButton(onPressed: _guardandoCelular ? null : _guardarCelular, child: const Text('Guardar')),
      ]),
      const SizedBox(height: 24),
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
