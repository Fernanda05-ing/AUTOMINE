import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';
import 'inicio.dart';

class LoginScreen extends StatefulWidget {
  const LoginScreen({super.key});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  final _form = GlobalKey<FormState>();
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _ocultar = true;
  bool _cargando = false;
  String? _error;

  Future<void> _ingresar() async {
    if (!_form.currentState!.validate()) return;
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      await Api.instance.login(_email.text.trim(), _password.text);
      if (!mounted) return;
      final u = Api.instance.usuario!;
      Navigator.of(context).pushReplacement(MaterialPageRoute(
          builder: (_) => u.debeCambiarPassword ? const CambiarPasswordScreen() : const InicioScreen()));
    } catch (e) {
      setState(() => _error = e.toString());
    } finally {
      if (mounted) setState(() => _cargando = false);
    }
  }

  Future<void> _recuperar() async {
    final ctrl = TextEditingController(text: _email.text);
    final email = await showDialog<String>(
      context: context,
      builder: (c) => AlertDialog(
        title: const Text('Recuperar contraseña'),
        content: TextField(
            controller: ctrl,
            keyboardType: TextInputType.emailAddress,
            decoration: const InputDecoration(labelText: 'Correo')),
        actions: [
          TextButton(onPressed: () => Navigator.pop(c), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(c, ctrl.text.trim()), child: const Text('Enviar enlace')),
        ],
      ),
    );
    if (email == null || email.isEmpty || !mounted) return;
    try {
      final r = await Api.instance.post('/ForgotPassword', {'email': email});
      if (mounted) avisar(context, r['message']);
    } catch (e) {
      if (mounted) avisar(context, e.toString(), error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: Form(
                key: _form,
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  const Logo(height: 96),
                  const SizedBox(height: 28),
                  Text('Iniciar sesión', style: Theme.of(context).textTheme.headlineSmall, textAlign: TextAlign.center),
                  const SizedBox(height: 20),
                  if (_error != null)
                    Container(
                      padding: const EdgeInsets.all(12),
                      margin: const EdgeInsets.only(bottom: 12),
                      decoration: BoxDecoration(color: Colors.red.shade50, borderRadius: BorderRadius.circular(8)),
                      child: Text(_error!, style: TextStyle(color: Colors.red.shade800)),
                    ),
                  TextFormField(
                    controller: _email,
                    keyboardType: TextInputType.emailAddress,
                    autofillHints: const [AutofillHints.email],
                    decoration: const InputDecoration(labelText: 'Correo', prefixIcon: Icon(Icons.mail_outline)),
                    validator: (v) => v == null || !v.contains('@') ? 'Ingrese un correo válido' : null,
                  ),
                  const SizedBox(height: 14),
                  TextFormField(
                    controller: _password,
                    obscureText: _ocultar,
                    autofillHints: const [AutofillHints.password],
                    decoration: InputDecoration(
                      labelText: 'Contraseña',
                      prefixIcon: const Icon(Icons.lock_outline),
                      suffixIcon: IconButton(
                        icon: Icon(_ocultar ? Icons.visibility : Icons.visibility_off),
                        onPressed: () => setState(() => _ocultar = !_ocultar),
                      ),
                    ),
                    validator: (v) => v == null || v.isEmpty ? 'Ingrese la contraseña' : null,
                    onFieldSubmitted: (_) => _ingresar(),
                  ),
                  const SizedBox(height: 20),
                  FilledButton(
                    onPressed: _cargando ? null : _ingresar,
                    style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                    child: _cargando
                        ? const SizedBox(height: 20, width: 20, child: CircularProgressIndicator(strokeWidth: 2))
                        : const Text('Ingresar'),
                  ),
                  TextButton(onPressed: _recuperar, child: const Text('¿Olvidó su contraseña?')),
                ]),
              ),
            ),
          ),
        ),
      );
}


/// Obligatoria cuando el usuario entra con una contraseña temporal.
class CambiarPasswordScreen extends StatefulWidget {
  const CambiarPasswordScreen({super.key});

  @override
  State<CambiarPasswordScreen> createState() => _CambiarPasswordScreenState();
}

class _CambiarPasswordScreenState extends State<CambiarPasswordScreen> {
  final _form = GlobalKey<FormState>();
  final _actual = TextEditingController();
  final _nueva = TextEditingController();
  final _confirmar = TextEditingController();
  bool _guardando = false;

  Future<void> _guardar() async {
    if (!_form.currentState!.validate()) return;
    setState(() => _guardando = true);
    try {
      final u = await Api.instance.post('/ChangePassword', {'passwordActual': _actual.text, 'password': _nueva.text});
      Api.instance.usuario = Usuario.fromJson(u);
      if (!mounted) return;
      Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const InicioScreen()));
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Cree su contraseña')),
        body: Form(
          key: _form,
          child: ListView(padding: const EdgeInsets.all(20), children: [
            const Text('Está usando una contraseña temporal. Por seguridad, cree una contraseña personal para continuar.'),
            const SizedBox(height: 20),
            TextFormField(
              controller: _actual,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Contraseña temporal'),
              validator: (v) => v == null || v.isEmpty ? 'Requerida' : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _nueva,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Nueva contraseña', helperText: '8 o más caracteres, con letras y números'),
              validator: (v) => v == null || v.length < 8 || !RegExp(r'[A-Za-z]').hasMatch(v) || !RegExp(r'\d').hasMatch(v)
                  ? 'Mínimo 8 caracteres con letras y números'
                  : null,
            ),
            const SizedBox(height: 12),
            TextFormField(
              controller: _confirmar,
              obscureText: true,
              decoration: const InputDecoration(labelText: 'Confirmar nueva contraseña'),
              validator: (v) => v != _nueva.text ? 'Las contraseñas no coinciden' : null,
            ),
            const SizedBox(height: 20),
            FilledButton(
              onPressed: _guardando ? null : _guardar,
              style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
              child: Text(_guardando ? 'Guardando...' : 'Guardar y continuar'),
            ),
          ]),
        ),
      );
}
