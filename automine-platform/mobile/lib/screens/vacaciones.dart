import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';
import 'incapacidades.dart';

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

String _dec(dynamic n) {
  final v = (n as num).toDouble();
  return v == v.roundToDouble() ? v.toStringAsFixed(0) : v.toStringAsFixed(2).replaceAll('.', ',');
}

/// Pestaña "Novedades": vacaciones e incapacidades del trabajador.
class NovedadesScreen extends StatelessWidget {
  const NovedadesScreen({super.key});

  @override
  Widget build(BuildContext context) => const DefaultTabController(
        length: 2,
        child: Column(children: [
          Material(
            color: Colors.white,
            child: TabBar(
              labelColor: colorPrimario,
              indicatorColor: colorPrimario,
              tabs: [Tab(text: 'Vacaciones'), Tab(text: 'Incapacidades')],
            ),
          ),
          Expanded(child: TabBarView(children: [VacacionesScreen(), IncapacidadesScreen()])),
        ]),
      );
}

/// Saldo de vacaciones, solicitudes y prestaciones sociales del trabajador.
class VacacionesScreen extends StatefulWidget {
  const VacacionesScreen({super.key});

  @override
  State<VacacionesScreen> createState() => _VacacionesScreenState();
}

class _VacacionesScreenState extends State<VacacionesScreen> {
  Key _k = UniqueKey();

  Future<Map<String, dynamic>> _cargar() async {
    final id = Api.instance.usuario!.empleadoId;
    final r = await Future.wait([
      Api.instance.get('/Vacations/Balance/$id'),
      Api.instance.get('/Vacations', {'empleadoId': '$id'}),
      Api.instance.get('/Benefits/Employee/$id'),
    ]);
    return {'saldo': r[0], 'lista': r[1], 'prestaciones': r[2]};
  }

  Future<void> _solicitar(Map<String, dynamic> saldo) async {
    final ok = await Navigator.of(context).push<bool>(MaterialPageRoute(
        builder: (_) => SolicitarVacacionesScreen(disponibles: (saldo['diasDisponibles'] as num).toDouble())));
    if (ok == true) setState(() => _k = UniqueKey());
  }

  Future<void> _cancelar(Map<String, dynamic> v) async {
    final si = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Cancelar solicitud'),
        content: Text('¿Cancelar las vacaciones desde el ${fecha(v['fechaInicio'])}?'),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('No')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Sí, cancelar')),
        ],
      ),
    );
    if (si != true) return;
    try {
      await Api.instance.put('/Vacation/${v['id']}/Cancel', {});
      if (!mounted) return;
      avisar(context, 'Solicitud cancelada');
      setState(() => _k = UniqueKey());
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Cargador<Map<String, dynamic>>(
        key: _k,
        cargar: _cargar,
        builder: (context, d, recargar) {
          final saldo = d['saldo'] as Map<String, dynamic>;
          final lista = (d['lista'] as List).cast<Map<String, dynamic>>();
          final prest = (d['prestaciones'] as List).cast<Map<String, dynamic>>();
          final aplica = saldo['aplica'] == true;
          return ListView(padding: const EdgeInsets.all(12), children: [
            Card(
              color: const Color(0xFFFFF7E6),
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  const Text('Días de vacaciones disponibles', style: TextStyle(color: Colors.black54)),
                  Text(_dec(saldo['diasDisponibles']),
                      style: const TextStyle(fontSize: 36, fontWeight: FontWeight.w800, color: colorPrimario)),
                  Text(
                      'Causados ${_dec(saldo['diasCausados'])} · disfrutados ${saldo['diasDisfrutados']} · '
                      'en solicitud ${saldo['diasSolicitados']}',
                      style: const TextStyle(fontSize: 12, color: Colors.black54)),
                  const SizedBox(height: 12),
                  if (aplica)
                    FilledButton.icon(
                      onPressed: () => _solicitar(saldo),
                      icon: const Icon(Icons.beach_access),
                      label: const Text('Solicitar vacaciones'),
                    )
                  else
                    const Text('Su tipo de contrato no genera vacaciones.'),
                ]),
              ),
            ),
            const SizedBox(height: 8),
            if (lista.isNotEmpty)
              const Padding(
                  padding: EdgeInsets.all(8), child: Text('Mis solicitudes', style: TextStyle(fontWeight: FontWeight.w700))),
            ...lista.map((v) => Card(
                  child: ListTile(
                    leading: const Icon(Icons.beach_access_outlined, color: colorPrimario),
                    title: Text('${fecha(v['fechaInicio'])} al ${fecha(v['fechaFin'])}'),
                    subtitle: Text('${v['diasHabiles']} días hábiles · regresa el ${fecha(v['fechaRegreso'])}'
                        '${v['motivoRechazo'] != null ? '\n${v['motivoRechazo']}' : ''}'),
                    isThreeLine: v['motivoRechazo'] != null,
                    trailing: v['estado'] == 'PENDIENTE'
                        ? TextButton(onPressed: () => _cancelar(v), child: const Text('Cancelar'))
                        : Estado(v['estado']),
                  ),
                )),
            if (prest.isNotEmpty) ...[
              const Padding(
                  padding: EdgeInsets.all(8),
                  child: Text('Mis prestaciones sociales', style: TextStyle(fontWeight: FontWeight.w700))),
              Card(
                child: Column(
                  children: prest
                      .map((p) => ListTile(
                            dense: true,
                            title: Text(p['tipo'] == 'PRIMA'
                                ? 'Prima ${p['periodo'] == 1 ? 'de junio' : 'de diciembre'} ${p['anio']}'
                                : p['tipo'] == 'CESANTIAS'
                                    ? 'Cesantías ${p['anio']}'
                                    : 'Intereses de cesantías ${p['anio']}'),
                            subtitle: Text('${p['dias']} días'),
                            trailing: Text(dinero(p['valor']), style: const TextStyle(fontWeight: FontWeight.w700)),
                          ))
                      .toList(),
                ),
              ),
            ],
          ]);
        },
      );
}

class SolicitarVacacionesScreen extends StatefulWidget {
  final double disponibles;
  const SolicitarVacacionesScreen({super.key, required this.disponibles});

  @override
  State<SolicitarVacacionesScreen> createState() => _SolicitarVacacionesScreenState();
}

class _SolicitarVacacionesScreenState extends State<SolicitarVacacionesScreen> {
  DateTime _inicio = DateTime.now().add(const Duration(days: 7));
  int _dias = 15;
  Map<String, dynamic>? _sim;
  String? _simError;
  bool _enviando = false;
  final _obs = TextEditingController();

  @override
  void initState() {
    super.initState();
    if (widget.disponibles < _dias) _dias = widget.disponibles.floor().clamp(1, 60);
    _simular();
  }

  @override
  void dispose() {
    _obs.dispose();
    super.dispose();
  }

  Future<void> _simular() async {
    try {
      final r = await Api.instance.get('/Vacations/Simulate', {'fechaInicio': _iso(_inicio), 'diasHabiles': '$_dias'});
      if (!mounted) return;
      setState(() {
        _sim = r as Map<String, dynamic>;
        _simError = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _sim = null;
        _simError = '$e';
      });
    }
  }

  void _cambiarDias(int delta) {
    setState(() => _dias = (_dias + delta).clamp(1, 60));
    _simular();
  }

  Future<void> _fecha() async {
    final hoy = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: _inicio,
      firstDate: hoy,
      lastDate: hoy.add(const Duration(days: 365)),
      helpText: 'Primer día de vacaciones',
      cancelText: 'Cancelar',
      confirmText: 'Aceptar',
    );
    if (d == null) return;
    setState(() => _inicio = d);
    _simular();
  }

  Future<void> _enviar() async {
    setState(() => _enviando = true);
    try {
      await Api.instance.post('/Vacation', {
        'fechaInicio': _iso(_inicio),
        'diasHabiles': _dias,
        if (_obs.text.trim().isNotEmpty) 'observaciones': _obs.text.trim(),
      });
      if (!mounted) return;
      avisar(context, 'Solicitud enviada a Talento Humano');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Solicitar vacaciones')),
        body: ListView(padding: const EdgeInsets.all(16), children: [
          Text('Tiene ${_dec(widget.disponibles)} días hábiles disponibles.', style: const TextStyle(color: Colors.black54)),
          const SizedBox(height: 16),
          InkWell(
            onTap: _fecha,
            child: InputDecorator(
              decoration: const InputDecoration(labelText: 'Primer día', suffixIcon: Icon(Icons.event)),
              child: Text(fecha(_iso(_inicio))),
            ),
          ),
          const SizedBox(height: 16),
          Row(children: [
            const Expanded(child: Text('Días hábiles', style: TextStyle(fontSize: 16))),
            IconButton.outlined(onPressed: _dias > 1 ? () => _cambiarDias(-1) : null, icon: const Icon(Icons.remove)),
            SizedBox(
                width: 48,
                child: Text('$_dias',
                    textAlign: TextAlign.center, style: const TextStyle(fontSize: 22, fontWeight: FontWeight.w700))),
            IconButton.outlined(onPressed: _dias < 60 ? () => _cambiarDias(1) : null, icon: const Icon(Icons.add)),
          ]),
          const SizedBox(height: 16),
          if (_sim != null)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.green.shade50, borderRadius: BorderRadius.circular(10)),
              child: Text(
                'Descansa del ${fecha(_sim!['fechaInicio'])} al ${fecha(_sim!['fechaFin'])} '
                '(${_sim!['diasCalendario']} días calendario) y regresa el ${fecha(_sim!['fechaRegreso'])}.\n'
                'Los domingos y festivos no se descuentan.',
              ),
            ),
          if (_simError != null)
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(color: Colors.orange.shade50, borderRadius: BorderRadius.circular(10)),
              child: Text(_simError!),
            ),
          const SizedBox(height: 16),
          TextField(controller: _obs, maxLines: 2, decoration: const InputDecoration(labelText: 'Observaciones (opcional)')),
          const SizedBox(height: 20),
          FilledButton.icon(
            onPressed: _enviando || _sim == null ? null : _enviar,
            icon: const Icon(Icons.send),
            label: const Text('Enviar solicitud'),
            style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
          ),
        ]),
      );
}
