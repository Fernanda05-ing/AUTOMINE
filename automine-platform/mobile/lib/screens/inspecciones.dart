import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:http_parser/http_parser.dart';
import 'package:image_picker/image_picker.dart';

import '../api.dart';
import '../ui.dart';

const _tipos = {
  'MAQUINARIA_PESADA': 'Maquinaria pesada',
  'VEHICULO': 'Vehículo',
  'HERRAMIENTA': 'Herramienta / neumática',
  'EQUIPO_ALTURAS': 'Equipo de alturas',
};

/// Inspecciones preoperacionales desde el celular: lista de chequeo y fotos de las fallas.
class InspeccionesScreen extends StatefulWidget {
  const InspeccionesScreen({super.key});

  @override
  State<InspeccionesScreen> createState() => _InspeccionesScreenState();
}

class _InspeccionesScreenState extends State<InspeccionesScreen> {
  Key _k = UniqueKey();

  Future<void> _nueva() async {
    final ok = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const NuevaInspeccionScreen()));
    if (ok == true) setState(() => _k = UniqueKey());
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: Cargador<List<dynamic>>(
          key: _k,
          cargar: () async => (await Api.instance.get('/Inspections')) as List,
          builder: (context, lista, recargar) {
            if (lista.isEmpty) return const Vacio('No hay inspecciones en los últimos 30 días.\nPulse "Inspeccionar" para empezar.');
            return ListView.builder(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 88),
              itemCount: lista.length,
              itemBuilder: (_, i) {
                final x = lista[i] as Map<String, dynamic>;
                final r = x['resultado'] as String;
                final color = r == 'APTO' ? Colors.green.shade700 : r == 'NO_APTO' ? Colors.red.shade700 : Colors.orange.shade800;
                return Card(
                  child: ListTile(
                    leading: Icon(r == 'NO_APTO' ? Icons.block : Icons.fact_check_outlined, color: color),
                    title: Text(x['equipo']),
                    subtitle: Text('${fecha(x['fechaHora'])} ${(x['fechaHora'] as String).substring(11, 16)} · ${x['inspector']}'
                        '${(x['fallas'] as int) > 0 ? ' · ${x['fallas']} fallas' : ''}'),
                    trailing: Text(etiqueta(r), style: TextStyle(color: color, fontWeight: FontWeight.w700, fontSize: 12)),
                  ),
                );
              },
            );
          },
        ),
        floatingActionButton: FloatingActionButton.extended(
          heroTag: 'nueva-inspeccion',
          onPressed: _nueva,
          backgroundColor: colorPrimario,
          foregroundColor: Colors.white,
          icon: const Icon(Icons.fact_check),
          label: const Text('Inspeccionar'),
        ),
      );
}

class NuevaInspeccionScreen extends StatefulWidget {
  const NuevaInspeccionScreen({super.key});

  @override
  State<NuevaInspeccionScreen> createState() => _NuevaInspeccionScreenState();
}

class _NuevaInspeccionScreenState extends State<NuevaInspeccionScreen> {
  Map<String, dynamic> _listas = {};
  List<dynamic> _maquinas = [];
  String _tipo = 'MAQUINARIA_PESADA';
  int? _maquinaId;
  final _equipo = TextEditingController();
  final _horometro = TextEditingController();
  final _obs = TextEditingController();
  final Map<String, String> _estado = {};
  final Map<String, TextEditingController> _detalle = {};
  final List<String> _fotos = [];
  bool _cargando = true;
  bool _enviando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      _listas = (await Api.instance.get('/Inspections/Checklists')) as Map<String, dynamic>;
      try {
        _maquinas = (await Api.instance.get('/Machinery')) as List;
      } catch (_) {}
    } catch (e) {
      _error = '$e';
    }
    if (mounted) setState(() => _cargando = false);
  }

  @override
  void dispose() {
    _equipo.dispose();
    _horometro.dispose();
    _obs.dispose();
    for (final c in _detalle.values) {
      c.dispose();
    }
    super.dispose();
  }

  List<Map<String, dynamic>> get _puntos => ((_listas[_tipo] as List?) ?? []).cast<Map<String, dynamic>>();

  Future<void> _foto() async {
    final f = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 70, maxWidth: 1600);
    if (f != null) setState(() => _fotos.add(f.path));
  }

  Future<void> _enviar() async {
    final faltan = _puntos.where((p) => _estado[p['item']] == null).length;
    if (faltan > 0) {
      avisar(context, 'Faltan $faltan puntos por revisar', error: true);
      return;
    }
    if (_estado.values.contains('FALLA') && _fotos.isEmpty) {
      avisar(context, 'Tome al menos una foto de las fallas', error: true);
      return;
    }
    if (_maquinaId == null && _equipo.text.trim().isEmpty) {
      avisar(context, 'Seleccione o escriba el equipo', error: true);
      return;
    }
    setState(() => _enviando = true);
    try {
      final items = _puntos
          .map((p) => {'item': p['item'], 'estado': _estado[p['item']], 'observacion': _detalle[p['item']]?.text.trim() ?? ''})
          .toList();
      final req = http.MultipartRequest('POST', Uri.parse('$apiUrl/AutomineApi/Inspection'));
      req.headers.addAll(await Api.instance.cabeceras());
      req.fields.addAll({
        'tipo': _tipo,
        if (_maquinaId != null) 'maquinariaId': '$_maquinaId' else 'equipo': _equipo.text.trim(),
        if (_horometro.text.trim().isNotEmpty) 'horometro': _horometro.text.trim().replaceAll(',', '.'),
        if (_obs.text.trim().isNotEmpty) 'observaciones': _obs.text.trim(),
        'items': jsonEncode(items),
      });
      for (final f in _fotos) {
        req.files.add(await http.MultipartFile.fromPath('fotos', f, contentType: MediaType('image', 'jpeg')));
      }
      final res = await http.Response.fromStream(await req.send().timeout(const Duration(seconds: 90)));
      final body = utf8.decode(res.bodyBytes);
      if (res.statusCode >= 400) {
        String msg = 'Error ${res.statusCode}';
        try {
          msg = jsonDecode(body)['message'] ?? msg;
        } catch (_) {}
        throw ApiException(res.statusCode, msg);
      }
      final r = jsonDecode(body)['resultado'];
      if (!mounted) return;
      avisar(context, r == 'NO_APTO' ? 'Equipo NO APTO: quedó en mantenimiento y se avisó a almacén' : 'Inspección registrada',
          error: r == 'NO_APTO');
      Navigator.pop(context, true);
    } on SocketException {
      if (mounted) avisar(context, 'Sin conexión: intente de nuevo cuando tenga señal', error: true);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Inspección preoperacional')),
      body: _cargando
          ? const Center(child: CircularProgressIndicator())
          : _error != null
              ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!)))
              : ListView(padding: const EdgeInsets.all(16), children: [
                  DropdownButtonFormField<String>(
                    initialValue: _tipo,
                    decoration: const InputDecoration(labelText: 'Tipo de equipo'),
                    items: _tipos.entries.map((e) => DropdownMenuItem(value: e.key, child: Text(e.value))).toList(),
                    onChanged: (v) => setState(() {
                      _tipo = v ?? _tipo;
                      _estado.clear();
                    }),
                  ),
                  const SizedBox(height: 12),
                  DropdownButtonFormField<int?>(
                    initialValue: _maquinaId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Equipo'),
                    items: [
                      const DropdownMenuItem<int?>(value: null, child: Text('Otro (escribir)')),
                      ..._maquinas.map((m) => DropdownMenuItem<int?>(value: m['id'], child: Text('${m['codigo']} ${m['nombre']}'))),
                    ],
                    onChanged: (v) => setState(() => _maquinaId = v),
                  ),
                  if (_maquinaId == null) ...[
                    const SizedBox(height: 12),
                    TextField(controller: _equipo, decoration: const InputDecoration(labelText: 'Equipo', hintText: 'Ej.: Camioneta ABC-123')),
                  ],
                  const SizedBox(height: 12),
                  TextField(
                    controller: _horometro,
                    keyboardType: const TextInputType.numberWithOptions(decimal: true),
                    decoration: const InputDecoration(labelText: 'Horómetro / kilometraje (opcional)'),
                  ),
                  const SizedBox(height: 16),
                  const Text('Lista de chequeo (* crítico)', style: TextStyle(fontWeight: FontWeight.w700)),
                  ..._puntos.map((p) {
                    final item = p['item'] as String;
                    final e = _estado[item];
                    return Card(
                      margin: const EdgeInsets.symmetric(vertical: 4),
                      child: Padding(
                        padding: const EdgeInsets.all(8),
                        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                          Text('$item${p['critico'] == true ? ' *' : ''}', style: const TextStyle(fontWeight: FontWeight.w600)),
                          const SizedBox(height: 6),
                          SegmentedButton<String>(
                            showSelectedIcon: false,
                            emptySelectionAllowed: true,
                            segments: const [
                              ButtonSegment(value: 'OK', label: Text('Bien')),
                              ButtonSegment(value: 'FALLA', label: Text('Falla')),
                              ButtonSegment(value: 'NA', label: Text('N/A')),
                            ],
                            selected: e == null ? <String>{} : {e},
                            onSelectionChanged: (s) => setState(() {
                              if (s.isEmpty) {
                                _estado.remove(item);
                              } else {
                                _estado[item] = s.first;
                              }
                            }),
                          ),
                          if (e == 'FALLA')
                            TextField(
                              controller: _detalle.putIfAbsent(item, TextEditingController.new),
                              decoration: const InputDecoration(labelText: 'Describa la falla *', isDense: true),
                            ),
                        ]),
                      ),
                    );
                  }),
                  const SizedBox(height: 12),
                  Wrap(spacing: 8, runSpacing: 8, children: [
                    ..._fotos.map((f) => Stack(children: [
                          ClipRRect(
                            borderRadius: BorderRadius.circular(8),
                            child: Image.file(File(f), width: 90, height: 90, fit: BoxFit.cover),
                          ),
                          Positioned(
                            right: 0,
                            top: 0,
                            child: InkWell(
                              onTap: () => setState(() => _fotos.remove(f)),
                              child: const CircleAvatar(radius: 12, child: Icon(Icons.close, size: 14)),
                            ),
                          ),
                        ])),
                    if (_fotos.length < 10)
                      OutlinedButton.icon(onPressed: _foto, icon: const Icon(Icons.photo_camera), label: const Text('Foto')),
                  ]),
                  const SizedBox(height: 12),
                  TextField(controller: _obs, maxLines: 2, decoration: const InputDecoration(labelText: 'Observaciones (opcional)')),
                  const SizedBox(height: 20),
                  FilledButton.icon(
                    onPressed: _enviando ? null : _enviar,
                    icon: const Icon(Icons.send),
                    label: Text('Registrar (${_estado.length}/${_puntos.length} revisados)'),
                    style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
                  ),
                ]),
    );
  }
}
