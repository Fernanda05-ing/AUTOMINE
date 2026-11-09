import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';

String _hoy() => DateTime.now().toIso8601String().substring(0, 10);

/// Supervisores de campo: reporte diario y registro de produccion del turno (HU009).
class ProduccionScreen extends StatefulWidget {
  const ProduccionScreen({super.key});

  @override
  State<ProduccionScreen> createState() => _ProduccionScreenState();
}

class _ProduccionScreenState extends State<ProduccionScreen> {
  String _fecha = _hoy();
  Key _key = UniqueKey();

  bool get _registra => Api.instance.usuario!.tiene(['SUPERVISOR']);

  Future<void> _elegirFecha() async {
    final d = await showDatePicker(
      context: context,
      initialDate: DateTime.parse(_fecha),
      firstDate: DateTime(2020),
      lastDate: DateTime.now(),
    );
    if (d != null) {
      setState(() {
        _fecha = d.toIso8601String().substring(0, 10);
        _key = UniqueKey();
      });
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        floatingActionButton: _registra
            ? FloatingActionButton.extended(
                icon: const Icon(Icons.add),
                label: const Text('Registrar turno'),
                onPressed: () async {
                  final ok = await Navigator.of(context)
                      .push<bool>(MaterialPageRoute(builder: (_) => const RegistroProduccionScreen()));
                  if (ok == true) setState(() => _key = UniqueKey());
                },
              )
            : null,
        body: Cargador<Map>(
          key: _key,
          cargar: () async => await Api.instance.get('/Production/DailyReport', {'fecha': _fecha}) as Map,
          builder: (context, r, _) => ListView(padding: const EdgeInsets.fromLTRB(12, 12, 12, 90), children: [
            ListTile(
              leading: const Icon(Icons.calendar_today),
              title: Text('Reporte del ${fecha(_fecha)}'),
              trailing: TextButton(onPressed: _elegirFecha, child: const Text('Cambiar')),
            ),
            Row(children: [
              _Indicador('Toneladas', numero(r['totalToneladas'])),
              _Indicador('Meta', numero(r['metaDia'])),
              _Indicador('Cumplimiento', '${numero(r['cumplimiento'])} %'),
            ]),
            const SizedBox(height: 8),
            if ((r['lineas'] as List).isEmpty)
              const Padding(padding: EdgeInsets.all(24), child: Text('No hay producción registrada en esta fecha.', textAlign: TextAlign.center))
            else
              for (final l in r['lineas'])
                Card(
                  child: ListTile(
                    title: Text('${l['frente']} · ${etiqueta(l['turno'])}'),
                    subtitle: Text('${l['personal']} trabajadores · ${numero(l['horas'])} h${l['observaciones'] != null ? '\n${l['observaciones']}' : ''}'),
                    trailing: Text('${numero(l['toneladas'])} t', style: const TextStyle(fontWeight: FontWeight.w700)),
                  ),
                ),
          ]),
        ),
      );
}

class _Indicador extends StatelessWidget {
  final String titulo;
  final String valor;
  const _Indicador(this.titulo, this.valor);

  @override
  Widget build(BuildContext context) => Expanded(
        child: Card(
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(titulo, style: const TextStyle(fontSize: 12, color: Colors.black54)),
              Text(valor, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            ]),
          ),
        ),
      );
}

class RegistroProduccionScreen extends StatefulWidget {
  const RegistroProduccionScreen({super.key});

  @override
  State<RegistroProduccionScreen> createState() => _RegistroProduccionScreenState();
}

class _RegistroProduccionScreenState extends State<RegistroProduccionScreen> {
  final _form = GlobalKey<FormState>();
  final _toneladas = TextEditingController();
  final _horas = TextEditingController(text: '8');
  final _obs = TextEditingController();
  String _fecha = _hoy();
  String _turno = 'MANANA';
  int? _frente;
  List _frentes = [];
  List _empleados = [];
  final Set<int> _personal = {};
  bool _cargando = true;
  bool _guardando = false;

  @override
  void initState() {
    super.initState();
    Future.wait([Api.instance.get('/Fronts'), Api.instance.get('/Employees/Options')]).then((r) {
      setState(() {
        _frentes = (r[0] as List).where((f) => f['activo'] == true).toList();
        _empleados = r[1] as List;
        _frente = _frentes.isEmpty ? null : _frentes.first['id'];
        _cargando = false;
      });
    }).catchError((e) {
      if (mounted) {
        avisar(context, '$e', error: true);
        setState(() => _cargando = false);
      }
    });
  }

  Future<void> _guardar() async {
    if (!_form.currentState!.validate() || _frente == null) return;
    setState(() => _guardando = true);
    try {
      await Api.instance.post('/Production', {
        'fecha': _fecha,
        'turno': _turno,
        'frenteId': _frente,
        'toneladas': double.parse(_toneladas.text.replaceAll(',', '.')),
        'horasOperacion': double.parse(_horas.text.replaceAll(',', '.')),
        'observaciones': _obs.text,
        'asignaciones': _personal.map((id) => {'empleadoId': id, 'labor': 'Operación'}).toList(),
      });
      if (!mounted) return;
      avisar(context, 'Producción registrada');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(title: const Text('Registrar producción')),
        body: _cargando
            ? const Center(child: CircularProgressIndicator())
            : Form(
                key: _form,
                child: ListView(padding: const EdgeInsets.all(16), children: [
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    leading: const Icon(Icons.event),
                    title: Text('Fecha: ${fecha(_fecha)}'),
                    trailing: TextButton(
                      child: const Text('Cambiar'),
                      onPressed: () async {
                        final d = await showDatePicker(
                            context: context, initialDate: DateTime.parse(_fecha), firstDate: DateTime(2020), lastDate: DateTime.now());
                        if (d != null) setState(() => _fecha = d.toIso8601String().substring(0, 10));
                      },
                    ),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _turno,
                    decoration: const InputDecoration(labelText: 'Turno'),
                    items: const [
                      DropdownMenuItem(value: 'MANANA', child: Text('Manana')),
                      DropdownMenuItem(value: 'TARDE', child: Text('Tarde')),
                      DropdownMenuItem(value: 'NOCHE', child: Text('Noche')),
                    ],
                    onChanged: (v) => setState(() => _turno = v!),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int>(
                    initialValue: _frente,
                    decoration: const InputDecoration(labelText: 'Frente de trabajo'),
                    items: [for (final f in _frentes) DropdownMenuItem(value: f['id'] as int, child: Text('${f['nombre']} (${f['mineral']})'))],
                    onChanged: (v) => setState(() => _frente = v),
                    validator: (v) => v == null ? 'Seleccione el frente' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _toneladas,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Toneladas extraídas', suffixText: 't'),
                    validator: (v) => double.tryParse((v ?? '').replaceAll(',', '.')) == null ? 'Ingrese un número' : null,
                  ),
                  const SizedBox(height: 12),
                  TextFormField(
                    controller: _horas,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Horas de operación', suffixText: 'h'),
                    validator: (v) {
                      final n = double.tryParse((v ?? '').replaceAll(',', '.'));
                      return n == null || n < 0 || n > 12 ? 'Entre 0 y 12 horas' : null;
                    },
                  ),
                  const SizedBox(height: 12),
                  TextFormField(controller: _obs, decoration: const InputDecoration(labelText: 'Observaciones'), maxLines: 2),
                  const SizedBox(height: 16),
                  Text('Personal del turno (${_personal.length})', style: Theme.of(context).textTheme.titleMedium),
                  for (final e in _empleados)
                    CheckboxListTile(
                      dense: true,
                      contentPadding: EdgeInsets.zero,
                      value: _personal.contains(e['id']),
                      title: Text(e['nombre']),
                      subtitle: Text(e['cargo']),
                      onChanged: (v) => setState(() => v == true ? _personal.add(e['id']) : _personal.remove(e['id'])),
                    ),
                  const SizedBox(height: 16),
                  FilledButton(
                    onPressed: _guardando ? null : _guardar,
                    style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                    child: Text(_guardando ? 'Guardando...' : 'Guardar'),
                  ),
                ]),
              ),
      );
}
