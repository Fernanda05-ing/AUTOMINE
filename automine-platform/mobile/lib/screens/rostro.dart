import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';

import '../api.dart';
import '../facial.dart';
import '../ui.dart';
import 'asistencia.dart';

/// Registro del rostro de un trabajador: autorizacion (Ley 1581) y tres fotos de frente.
class RegistrarRostroScreen extends StatefulWidget {
  const RegistrarRostroScreen({super.key});

  @override
  State<RegistrarRostroScreen> createState() => _RegistrarRostroScreenState();
}

class _RegistrarRostroScreenState extends State<RegistrarRostroScreen> {
  static const _fotosNecesarias = 3;
  List<Map<String, dynamic>> _empleados = [];
  Map<String, dynamic>? _sel;
  String _autorizacion = '';
  bool _acepta = false;
  final List<String> _fotos = [];
  final List<List<double>> _plantillas = [];
  String _filtro = '';
  bool _procesando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final r = await Future.wait([Api.instance.get('/Employees/Options'), Api.instance.get('/Faces/Consent')]);
      _empleados = (r[0] as List).cast<Map<String, dynamic>>();
      _autorizacion = r[1]['texto'];
    } catch (e) {
      _error = 'Para registrar rostros se necesita conexión. $e';
    }
    if (mounted) setState(() {});
  }

  Future<void> _tomar() async {
    final foto = await tomarFoto();
    if (foto == null) return;
    setState(() => _procesando = true);
    try {
      final p = await Facial.instance.plantilla(foto);
      _fotos.add(foto);
      _plantillas.add(p);
    } on ErrorRostro catch (e) {
      if (mounted) avisar(context, e.mensaje, error: true);
    } finally {
      if (mounted) setState(() => _procesando = false);
    }
  }

  Future<void> _guardar() async {
    setState(() => _procesando = true);
    try {
      final plantilla = Facial.promedio(_plantillas);
      await Api.instance.enviarArchivo('POST', '/Employee/${_sel!['id']}/Face',
          campos: {'plantilla': jsonEncode(plantilla), 'version': versionPlantilla, 'autoriza': 'true'},
          archivo: _fotos.first,
          campoArchivo: 'foto');
      if (!mounted) return;
      avisar(context, 'Rostro de ${_sel!['nombre']} registrado');
      Navigator.pop(context, true);
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _procesando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final lista = _empleados.where((e) => '${e['nombre']} ${e['cedula']}'.toLowerCase().contains(_filtro.toLowerCase())).take(40).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Registrar rostro')),
      body: _error != null
          ? Center(child: Padding(padding: const EdgeInsets.all(24), child: Text(_error!, textAlign: TextAlign.center)))
          : ListView(padding: const EdgeInsets.all(16), children: [
              if (_sel == null) ...[
                const Text('1. Elija al trabajador', style: TextStyle(fontWeight: FontWeight.w700)),
                TextField(
                  decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar por nombre o cédula', isDense: true),
                  onChanged: (v) => setState(() => _filtro = v),
                ),
                ...lista.map((e) => ListTile(
                      title: Text(e['nombre']),
                      subtitle: Text('${e['cedula']} · ${e['cargo']}'),
                      onTap: () => setState(() => _sel = e),
                    )),
              ] else ...[
                Card(
                  child: ListTile(
                    leading: const Icon(Icons.person),
                    title: Text(_sel!['nombre']),
                    subtitle: Text('${_sel!['cedula']} · ${_sel!['cargo']}'),
                    trailing: TextButton(
                      onPressed: () => setState(() {
                        _sel = null;
                        _acepta = false;
                        _fotos.clear();
                        _plantillas.clear();
                      }),
                      child: const Text('Cambiar'),
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                const Text('2. Autorización del trabajador', style: TextStyle(fontWeight: FontWeight.w700)),
                Container(
                  margin: const EdgeInsets.symmetric(vertical: 8),
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(color: const Color(0xFFFFF7E6), borderRadius: BorderRadius.circular(10)),
                  child: Text(_autorizacion, style: const TextStyle(fontSize: 13)),
                ),
                CheckboxListTile(
                  contentPadding: EdgeInsets.zero,
                  value: _acepta,
                  onChanged: (v) => setState(() => _acepta = v ?? false),
                  title: const Text('El trabajador leyó y acepta esta autorización'),
                ),
                if (_acepta) ...[
                  const SizedBox(height: 8),
                  Text('3. Fotos de frente con buena luz ($_fotosNecesarias)', style: const TextStyle(fontWeight: FontWeight.w700)),
                  const Text('Sin gafas oscuras, gorra ni casco; mirando a la cámara.', style: TextStyle(fontSize: 12, color: Colors.black54)),
                  const SizedBox(height: 8),
                  Row(
                    children: List.generate(_fotosNecesarias, (i) => Expanded(
                          child: Padding(
                            padding: const EdgeInsets.all(4),
                            child: AspectRatio(
                              aspectRatio: 3 / 4,
                              child: Container(
                                decoration: BoxDecoration(color: Colors.grey.shade200, borderRadius: BorderRadius.circular(10)),
                                clipBehavior: Clip.antiAlias,
                                child: i < _fotos.length
                                    ? Image.file(File(_fotos[i]), fit: BoxFit.cover)
                                    : Center(child: Text('${i + 1}', style: const TextStyle(fontSize: 24, color: Colors.black26))),
                              ),
                            ),
                          ),
                        )),
                  ),
                  const SizedBox(height: 8),
                  if (_fotos.length < _fotosNecesarias)
                    FilledButton.icon(
                      onPressed: _procesando ? null : _tomar,
                      icon: const Icon(Icons.photo_camera),
                      label: Text('Tomar foto ${_fotos.length + 1} de $_fotosNecesarias'),
                    )
                  else
                    FilledButton.icon(
                      onPressed: _procesando ? null : _guardar,
                      icon: const Icon(Icons.save),
                      label: const Text('Guardar rostro'),
                      style: FilledButton.styleFrom(backgroundColor: colorPrimario),
                    ),
                  if (_procesando) const Padding(padding: EdgeInsets.all(12), child: Center(child: CircularProgressIndicator())),
                ],
              ],
            ]),
    );
  }
}
