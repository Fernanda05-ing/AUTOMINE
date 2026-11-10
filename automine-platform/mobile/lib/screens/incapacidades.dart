import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api.dart';
import '../ui.dart';

const _tipos = {
  'ENFERMEDAD_GENERAL': 'Enfermedad general',
  'ACCIDENTE_TRABAJO': 'Accidente de trabajo',
  'ENFERMEDAD_LABORAL': 'Enfermedad laboral',
  'LICENCIA_MATERNIDAD': 'Licencia de maternidad',
  'LICENCIA_PATERNIDAD': 'Licencia de paternidad',
};

String _iso(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Elige el certificado: foto con la cámara, imagen de la galería o PDF. Devuelve la ruta del archivo.
Future<String?> elegirSoporte(BuildContext context) async {
  final opcion = await showModalBottomSheet<String>(
    context: context,
    builder: (_) => SafeArea(
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        const ListTile(title: Text('Adjuntar certificado', style: TextStyle(fontWeight: FontWeight.w700))),
        ListTile(
          leading: const Icon(Icons.photo_camera_outlined),
          title: const Text('Tomar foto del certificado'),
          onTap: () => Navigator.pop(context, 'camara'),
        ),
        ListTile(
          leading: const Icon(Icons.photo_library_outlined),
          title: const Text('Elegir foto de la galería'),
          onTap: () => Navigator.pop(context, 'galeria'),
        ),
        ListTile(
          leading: const Icon(Icons.picture_as_pdf_outlined),
          title: const Text('Elegir PDF'),
          onTap: () => Navigator.pop(context, 'pdf'),
        ),
      ]),
    ),
  );
  if (opcion == null) return null;
  if (opcion == 'pdf') {
    final r = await FilePicker.pickFiles(type: FileType.custom, allowedExtensions: ['pdf', 'jpg', 'jpeg', 'png']);
    return r.isEmpty ? null : r.first.path;
  }
  final foto = await ImagePicker().pickImage(
    source: opcion == 'camara' ? ImageSource.camera : ImageSource.gallery,
    imageQuality: 80,
    maxWidth: 2200,
  );
  return foto?.path;
}

Color _colorEstado(String e) => switch (e) {
      'APROBADA' => Colors.green.shade700,
      'RECHAZADA' || 'ANULADA' => Colors.red.shade700,
      _ => Colors.orange.shade800,
    };

/// Mis incapacidades: el trabajador las registra con el certificado y ve si fueron aprobadas.
class IncapacidadesScreen extends StatefulWidget {
  const IncapacidadesScreen({super.key});

  @override
  State<IncapacidadesScreen> createState() => _IncapacidadesScreenState();
}

class _IncapacidadesScreenState extends State<IncapacidadesScreen> {
  final _clave = GlobalKey<_ListaState>();

  Future<void> _nueva() async {
    final ok = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const NuevaIncapacidadScreen()));
    if (ok == true) _clave.currentState?.recargar();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        body: _Lista(key: _clave),
        floatingActionButton: FloatingActionButton.extended(
          heroTag: 'nueva-incapacidad',
          onPressed: _nueva,
          backgroundColor: colorPrimario,
          foregroundColor: Colors.white,
          icon: const Icon(Icons.add),
          label: const Text('Registrar'),
        ),
      );
}

class _Lista extends StatefulWidget {
  const _Lista({super.key});

  @override
  State<_Lista> createState() => _ListaState();
}

class _ListaState extends State<_Lista> {
  Key _k = UniqueKey();

  void recargar() => setState(() => _k = UniqueKey());

  Future<void> _corregir(Map<String, dynamic> i) async {
    final ruta = await elegirSoporte(context);
    if (ruta == null || !mounted) return;
    try {
      await Api.instance.enviarArchivo('PUT', '/Disability/${i['id']}/Support',
          campos: {}, archivo: ruta, campoArchivo: 'soporte');
      if (!mounted) return;
      avisar(context, i['estado'] == 'RECHAZADA' ? 'Certificado enviado: vuelve a revisión' : 'Certificado actualizado');
      recargar();
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    }
  }

  @override
  Widget build(BuildContext context) => Cargador<List<dynamic>>(
        key: _k,
        cargar: () async => (await Api.instance.get('/Disabilities')) as List<dynamic>,
        builder: (context, lista, recargarLista) {
          final propias = lista.where((i) => i['empleadoId'] == Api.instance.usuario?.empleadoId).toList();
          if (propias.isEmpty) {
            return const Vacio('No tiene incapacidades registradas.\n\n'
                'Si su médico de la EPS o ARL le dio incapacidad, pulse "Registrar" y adjunte '
                'el certificado (foto o PDF).');
          }
          return ListView.builder(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 88),
            itemCount: propias.length,
            itemBuilder: (_, n) {
              final i = propias[n] as Map<String, dynamic>;
              final estado = i['estado'] as String;
              final puedeCorregir = estado == 'PENDIENTE' || estado == 'RECHAZADA';
              return Card(
                child: Padding(
                  padding: const EdgeInsets.all(12),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      Expanded(
                        child: Text(_tipos[i['tipo']] ?? etiqueta(i['tipo']),
                            style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
                      ),
                      Container(
                        padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                        decoration: BoxDecoration(
                            color: _colorEstado(estado).withValues(alpha: 0.12), borderRadius: BorderRadius.circular(20)),
                        child: Text(etiqueta(estado),
                            style: TextStyle(color: _colorEstado(estado), fontSize: 12, fontWeight: FontWeight.w600)),
                      ),
                    ]),
                    const SizedBox(height: 6),
                    Text('${fecha(i['fechaInicio'])} al ${fecha(i['fechaFin'])} · ${i['dias']} ${i['dias'] == 1 ? 'día' : 'días'}'),
                    if (i['entidad'] != null) Text(i['entidad'], style: const TextStyle(color: Colors.black54)),
                    if (estado == 'RECHAZADA' && i['motivoRechazo'] != null)
                      Container(
                        margin: const EdgeInsets.only(top: 8),
                        padding: const EdgeInsets.all(8),
                        decoration: BoxDecoration(color: Colors.red.shade50, borderRadius: BorderRadius.circular(8)),
                        child: Text('Motivo: ${i['motivoRechazo']}', style: TextStyle(color: Colors.red.shade800)),
                      ),
                    if (estado == 'PENDIENTE')
                      const Padding(
                        padding: EdgeInsets.only(top: 6),
                        child: Text('En revisión por SG-SST / Talento Humano',
                            style: TextStyle(fontSize: 12, color: Colors.black54)),
                      ),
                    const SizedBox(height: 4),
                    Wrap(spacing: 8, children: [
                      if (i['soporteNombre'] != null)
                        TextButton.icon(
                          onPressed: () => Api.instance
                              .abrirArchivo('/Disability/${i['id']}/Support', i['soporteNombre'])
                              .catchError((e) {
                            if (context.mounted) avisar(context, '$e', error: true);
                          }),
                          icon: const Icon(Icons.description_outlined, size: 18),
                          label: const Text('Ver certificado'),
                        ),
                      if (puedeCorregir)
                        TextButton.icon(
                          onPressed: () => _corregir(i),
                          icon: const Icon(Icons.upload_file, size: 18),
                          label: Text(estado == 'RECHAZADA' ? 'Subir de nuevo' : 'Cambiar certificado'),
                        ),
                    ]),
                  ]),
                ),
              );
            },
          );
        },
      );
}

/// Formulario para registrar una incapacidad propia con su certificado.
class NuevaIncapacidadScreen extends StatefulWidget {
  const NuevaIncapacidadScreen({super.key});

  @override
  State<NuevaIncapacidadScreen> createState() => _NuevaIncapacidadScreenState();
}

class _NuevaIncapacidadScreenState extends State<NuevaIncapacidadScreen> {
  String _tipo = 'ENFERMEDAD_GENERAL';
  DateTime _desde = DateTime.now();
  DateTime _hasta = DateTime.now();
  bool _prorroga = false;
  final _entidad = TextEditingController();
  final _numero = TextEditingController();
  final _obs = TextEditingController();
  String? _archivo;
  bool _enviando = false;

  int get _dias => DateTime(_hasta.year, _hasta.month, _hasta.day)
          .difference(DateTime(_desde.year, _desde.month, _desde.day))
          .inDays +
      1;
  bool get _esArl => _tipo == 'ACCIDENTE_TRABAJO' || _tipo == 'ENFERMEDAD_LABORAL';

  @override
  void dispose() {
    _entidad.dispose();
    _numero.dispose();
    _obs.dispose();
    super.dispose();
  }

  Future<void> _fecha(bool inicio) async {
    final hoy = DateTime.now();
    final d = await showDatePicker(
      context: context,
      initialDate: inicio ? _desde : _hasta,
      firstDate: inicio ? hoy.subtract(const Duration(days: 365)) : _desde,
      lastDate: hoy.add(const Duration(days: 240)),
      helpText: inicio ? 'Fecha de inicio' : 'Fecha de fin',
      cancelText: 'Cancelar',
      confirmText: 'Aceptar',
    );
    if (d == null) return;
    setState(() {
      if (inicio) {
        _desde = d;
        if (_hasta.isBefore(d)) _hasta = d;
      } else {
        _hasta = d;
      }
    });
  }

  Future<void> _adjuntar() async {
    final r = await elegirSoporte(context);
    if (r != null) setState(() => _archivo = r);
  }

  Future<void> _enviar() async {
    if (_archivo == null) {
      avisar(context, 'Adjunte el certificado de incapacidad (foto o PDF)', error: true);
      return;
    }
    setState(() => _enviando = true);
    try {
      await Api.instance.enviarArchivo('POST', '/Disability',
          campos: {
            'tipo': _tipo,
            'fechaInicio': _iso(_desde),
            'fechaFin': _iso(_hasta),
            'prorroga': '$_prorroga',
            if (_entidad.text.trim().isNotEmpty) 'entidad': _entidad.text.trim(),
            if (_numero.text.trim().isNotEmpty) 'numeroCertificado': _numero.text.trim(),
            if (_obs.text.trim().isNotEmpty) 'observaciones': _obs.text.trim(),
          },
          archivo: _archivo!,
          campoArchivo: 'soporte');
      if (!mounted) return;
      avisar(context, 'Incapacidad enviada a revisión');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final nombreArchivo = _archivo?.split(RegExp(r'[\\/]')).last;
    return Scaffold(
      appBar: AppBar(title: const Text('Registrar incapacidad')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        Container(
          padding: const EdgeInsets.all(12),
          decoration: BoxDecoration(color: const Color(0xFFFFF7E6), borderRadius: BorderRadius.circular(10)),
          child: const Text(
              'La incapacidad la expide el médico de su EPS o ARL. Adjunte el certificado completo y legible; '
              'SG-SST o Talento Humano la revisarán y se tendrá en cuenta en su nómina.',
              style: TextStyle(fontSize: 13)),
        ),
        const SizedBox(height: 16),
        DropdownButtonFormField<String>(
          initialValue: _tipo,
          decoration: const InputDecoration(labelText: 'Tipo'),
          items: _tipos.entries.map((e) => DropdownMenuItem(value: e.key, child: Text(e.value))).toList(),
          onChanged: (v) => setState(() => _tipo = v ?? _tipo),
        ),
        const SizedBox(height: 12),
        Row(children: [
          Expanded(
            child: InkWell(
              onTap: () => _fecha(true),
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Desde', suffixIcon: Icon(Icons.event)),
                child: Text(fecha(_iso(_desde))),
              ),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: InkWell(
              onTap: () => _fecha(false),
              child: InputDecorator(
                decoration: const InputDecoration(labelText: 'Hasta', suffixIcon: Icon(Icons.event)),
                child: Text(fecha(_iso(_hasta))),
              ),
            ),
          ),
        ]),
        Padding(
          padding: const EdgeInsets.only(top: 6, left: 4),
          child: Text('$_dias ${_dias == 1 ? 'día' : 'días'} de incapacidad',
              style: const TextStyle(fontWeight: FontWeight.w600, color: colorPrimario)),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _entidad,
          decoration: InputDecoration(labelText: _esArl ? 'ARL' : 'EPS', hintText: _esArl ? 'Ej.: ARL Sura' : 'Ej.: EPS Sanitas'),
        ),
        const SizedBox(height: 12),
        TextField(controller: _numero, decoration: const InputDecoration(labelText: 'N.º de certificado (opcional)')),
        SwitchListTile(
          contentPadding: EdgeInsets.zero,
          value: _prorroga,
          onChanged: (v) => setState(() => _prorroga = v),
          title: const Text('Es prórroga de una incapacidad anterior'),
        ),
        TextField(
          controller: _obs,
          maxLines: 2,
          decoration: const InputDecoration(labelText: 'Observaciones (opcional)'),
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: _adjuntar,
          icon: Icon(_archivo == null ? Icons.attach_file : Icons.check_circle, color: _archivo == null ? null : Colors.green),
          label: Text(_archivo == null ? 'Adjuntar certificado (foto o PDF)' : nombreArchivo!, overflow: TextOverflow.ellipsis),
          style: OutlinedButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
        ),
        const SizedBox(height: 20),
        FilledButton.icon(
          onPressed: _enviando ? null : _enviar,
          icon: _enviando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Icon(Icons.send),
          label: const Text('Enviar a revisión'),
          style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
        ),
      ]),
    );
  }
}
