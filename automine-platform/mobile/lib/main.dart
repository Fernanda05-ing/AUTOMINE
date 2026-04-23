import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';

void main() {
  runApp(const AutomineApp());
}

class AutomineApp extends StatelessWidget {
  const AutomineApp({super.key});

  @override
  Widget build(BuildContext context) {
    final scheme = ColorScheme.fromSeed(
      seedColor: const Color(0xFFC8A75D),
      brightness: Brightness.dark,
      primary: const Color(0xFFC8A75D),
      secondary: const Color(0xFF1F4B77),
      surface: const Color(0xFF11161C),
    );

    return MaterialApp(
      title: 'AUTOMINE Mobile',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: scheme,
        textTheme: GoogleFonts.manropeTextTheme(ThemeData.dark().textTheme),
        scaffoldBackgroundColor: const Color(0xFF0A0E12),
        useMaterial3: true,
      ),
      home: const LoginPage(),
    );
  }
}

class LoginPage extends StatelessWidget {
  const LoginPage({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              const SizedBox(height: 40),
              Text('AUTOMINE', style: Theme.of(context).textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w700)),
              const SizedBox(height: 8),
              Text('Portal de colaboradores mineros', style: Theme.of(context).textTheme.bodyMedium),
              const SizedBox(height: 28),
              const TextField(decoration: InputDecoration(labelText: 'Usuario')),
              const SizedBox(height: 14),
              const TextField(obscureText: true, decoration: InputDecoration(labelText: 'Contrasena')),
              const SizedBox(height: 22),
              SizedBox(
                width: double.infinity,
                child: FilledButton(
                  onPressed: () {
                    Navigator.of(context).pushReplacement(MaterialPageRoute(builder: (_) => const HomePage()));
                  },
                  child: const Text('Ingresar'),
                ),
              )
            ],
          ),
        ),
      ),
    );
  }
}

class HomePage extends StatelessWidget {
  const HomePage({super.key});

  @override
  Widget build(BuildContext context) {
    final items = [
      ('Desprendibles de pago', Icons.receipt_long),
      ('Consignaciones', Icons.account_balance),
      ('Certificados', Icons.verified),
      ('Reportar incidente', Icons.warning_amber),
      ('Novedades', Icons.campaign),
    ];

    return Scaffold(
      appBar: AppBar(title: const Text('AUTOMINE Empleados')),
      body: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: items.length,
        separatorBuilder: (_, __) => const SizedBox(height: 12),
        itemBuilder: (_, index) {
          final item = items[index];
          return Card(
            child: ListTile(
              leading: Icon(item.$2, color: const Color(0xFFC8A75D)),
              title: Text(item.$1),
              trailing: const Icon(Icons.chevron_right),
              onTap: () {},
            ),
          );
        },
      ),
    );
  }
}
