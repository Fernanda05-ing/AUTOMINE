import 'package:flutter/material.dart';
import 'package:intl/date_symbol_data_local.dart';

import 'api.dart';
import 'screens/inicio.dart';
import 'screens/login.dart';
import 'ui.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  await initializeDateFormatting('es_CO');
  runApp(const AutomineApp());
}

class AutomineApp extends StatefulWidget {
  const AutomineApp({super.key});

  @override
  State<AutomineApp> createState() => _AutomineAppState();
}

class _AutomineAppState extends State<AutomineApp> {
  final _nav = GlobalKey<NavigatorState>();
  bool? _conSesion;

  @override
  void initState() {
    super.initState();
    Api.instance.onSesionVencida = () {
      _nav.currentState?.pushAndRemoveUntil(MaterialPageRoute(builder: (_) => const LoginScreen()), (_) => false);
    };
    Api.instance.restaurarSesion().then((ok) => setState(() => _conSesion = ok));
  }

  @override
  Widget build(BuildContext context) => MaterialApp(
        title: 'AUTOMINE',
        navigatorKey: _nav,
        debugShowCheckedModeBanner: false,
        theme: tema(),
        home: _conSesion == null
            ? const Scaffold(body: Center(child: Logo()))
            : _conSesion!
                ? (Api.instance.usuario?.debeCambiarPassword == true
                    ? const CambiarPasswordScreen()
                    : const InicioScreen())
                : const LoginScreen(),
      );
}
