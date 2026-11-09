import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

const colorPrimario = Color(0xFFB45309);
const colorDorado = Color(0xFFF2A900);
const colorCarbon = Color(0xFF2B2826);

final _money = NumberFormat.currency(locale: 'es_CO', symbol: '\$ ', decimalDigits: 0);
final _num = NumberFormat.decimalPattern('es_CO');

String dinero(num? v) => v == null ? '' : _money.format(v);
String numero(num? v) => v == null ? '' : _num.format(v);

String fecha(String? iso) {
  if (iso == null || iso.length < 10) return '';
  final p = iso.substring(0, 10).split('-');
  return '${p[2]}/${p[1]}/${p[0]}';
}

const meses = ['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre', 'Octubre', 'Noviembre', 'Diciembre'];

const _tildes = {
  'manana': 'mañana', 'nomina': 'nómina', 'cedula': 'cédula', 'produccion': 'producción',
  'anulada': 'anulada', 'medico': 'médico', 'critico': 'crítico', 'fisico': 'físico',
};

/// Convierte un código interno (MANANA, PAGADA...) en texto legible con tildes.
String etiqueta(String? v) {
  if (v == null || v.isEmpty) return '';
  final t = v
      .replaceAll('_', ' ')
      .toLowerCase()
      .split(' ')
      .map((w) => _tildes[w] ?? w)
      .join(' ');
  return t[0].toUpperCase() + t.substring(1);
}

ThemeData tema() => ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: colorPrimario, primary: colorPrimario, secondary: colorDorado),
      appBarTheme: const AppBarTheme(backgroundColor: colorCarbon, foregroundColor: Colors.white),
      inputDecorationTheme: const InputDecorationTheme(border: OutlineInputBorder()),
    );

/// Logo institucional (assets/logo.png) con respaldo en texto.
class Logo extends StatelessWidget {
  final double height;
  const Logo({super.key, this.height = 88});

  @override
  Widget build(BuildContext context) => Image.asset(
        'assets/logo.png',
        height: height,
        errorBuilder: (_, _, _) => Column(children: [
          Icon(Icons.landscape, size: height * 0.5, color: colorDorado),
          const Text('AUTOMINE', style: TextStyle(fontSize: 28, fontWeight: FontWeight.w800, color: colorCarbon)),
          const Text('GESTIÓN MINERA INTEGRAL', style: TextStyle(fontSize: 11, letterSpacing: 2)),
        ]),
      );
}

class Estado extends StatelessWidget {
  final String? valor;
  const Estado(this.valor, {super.key});

  @override
  Widget build(BuildContext context) {
    final v = valor ?? '';
    final ok = ['APROBADA', 'PAGADA', 'VIGENTE', 'ACTIVO', 'APROBADO', 'PAGADO'].contains(v);
    final mal = ['ANULADA', 'ANULADO', 'INACTIVO'].contains(v);
    final color = ok ? Colors.green.shade700 : mal ? Colors.red.shade700 : Colors.orange.shade800;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
      child: Text(etiqueta(v), style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
    );
  }
}

void avisar(BuildContext context, String texto, {bool error = false}) {
  ScaffoldMessenger.of(context).showSnackBar(SnackBar(
    content: Text(texto),
    backgroundColor: error ? Colors.red.shade700 : null,
  ));
}

/// Carga asincrona con indicador, error y "jale para actualizar".
class Cargador<T> extends StatefulWidget {
  final Future<T> Function() cargar;
  final Widget Function(BuildContext, T, Future<void> Function()) builder;
  const Cargador({super.key, required this.cargar, required this.builder});

  @override
  State<Cargador<T>> createState() => _CargadorState<T>();
}

class _CargadorState<T> extends State<Cargador<T>> {
  late Future<T> _futuro;

  @override
  void initState() {
    super.initState();
    _futuro = widget.cargar();
  }

  Future<void> _recargar() async {
    setState(() => _futuro = widget.cargar());
    await _futuro.catchError((_) => null as T);
  }

  @override
  Widget build(BuildContext context) => FutureBuilder<T>(
        future: _futuro,
        builder: (context, snap) {
          if (snap.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snap.hasError) {
            return RefreshIndicator(
              onRefresh: _recargar,
              child: ListView(children: [
                const SizedBox(height: 120),
                Icon(Icons.cloud_off, size: 48, color: Colors.grey.shade500),
                Padding(
                  padding: const EdgeInsets.all(24),
                  child: Text('${snap.error}', textAlign: TextAlign.center),
                ),
                Center(child: TextButton(onPressed: _recargar, child: const Text('Reintentar'))),
              ]),
            );
          }
          return RefreshIndicator(onRefresh: _recargar, child: widget.builder(context, snap.data as T, _recargar));
        },
      );
}

class Vacio extends StatelessWidget {
  final String texto;
  const Vacio(this.texto, {super.key});

  @override
  Widget build(BuildContext context) => ListView(children: [
        const SizedBox(height: 120),
        Icon(Icons.inbox_outlined, size: 48, color: Colors.grey.shade500),
        Padding(padding: const EdgeInsets.all(16), child: Text(texto, textAlign: TextAlign.center)),
      ]);
}
