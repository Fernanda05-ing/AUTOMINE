import 'dart:io';

import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';

import '../api.dart';
import '../asistencia_local.dart';
import '../facial.dart';
import '../ui.dart';
import 'rostro.dart';

/// Toma la foto del trabajador con la camara trasera (la SISO fotografia al trabajador).
Future<String?> tomarFoto() async {
  final f = await ImagePicker().pickImage(source: ImageSource.camera, imageQuality: 75, maxWidth: 1280);
  return f?.path;
}

/// Pantalla de la SISO: marcar entrada y salida con reconocimiento facial, incluso sin conexion.
class AsistenciaScreen extends StatefulWidget {
  const AsistenciaScreen({super.key});

  @override
  State<AsistenciaScreen> createState() => _AsistenciaScreenState();
}

class _AsistenciaScreenState extends State<AsistenciaScreen> {
  final _local = AsistenciaLocal.instance;
  bool _ocupado = false;
  List<dynamic> _hoy = [];
  String? _estado;

  @override
  void initState() {
    super.initState();
    _iniciar();
  }

  Future<void> _iniciar() async {
    await _local.cargar();
    if (mounted) setState(() {});
    await _actualizar(silencioso: true);
  }

  Future<void> _actualizar({bool silencioso = false}) async {
    setState(() => _estado = 'Sincronizando...');
    try {
      await _local.actualizarRostros();
      final quedan = await _local.sincronizar();
      final hoy = DateTime.now();
      final f = '${hoy.year}-${hoy.month.toString().padLeft(2, '0')}-${hoy.day.toString().padLeft(2, '0')}';
      _hoy = (await Api.instance.get('/Attendance', {'fecha': f})) as List;
      _estado = quedan == 0 ? null : '$quedan marcaciones sin enviar';
    } catch (e) {
      _estado = 'Sin conexión: se usan los rostros guardados en el celular';
      if (!silencioso && mounted) avisar(context, '$e', error: true);
    }
    _mostrarRechazadas();
    if (mounted) setState(() {});
  }

  void _mostrarRechazadas() {
    if (_local.rechazadas.isEmpty || !mounted) return;
    final msg = _local.rechazadas.join('\n');
    _local.rechazadas.clear();
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Marcaciones no aceptadas'),
        content: Text(msg),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Entendido'))],
      ),
    );
  }

  Future<void> _marcar(String tipo) async {
    if (_local.personas.isEmpty) {
      avisar(context, 'No hay rostros en el celular. Registre trabajadores o sincronice con conexión.', error: true);
      return;
    }
    final foto = await tomarFoto();
    if (foto == null || !mounted) return;
    setState(() => _ocupado = true);
    List<Coincidencia> candidatos = [];
    String? problema;
    try {
      candidatos = _local.reconocer(await Facial.instance.plantilla(foto));
    } on ErrorRostro catch (e) {
      problema = e.mensaje;
    } catch (e) {
      problema = 'No se pudo analizar la foto: $e';
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
    if (!mounted) return;
    final elegido = await Navigator.of(context).push<_Eleccion>(MaterialPageRoute(
      builder: (_) => _ConfirmarScreen(tipo: tipo, foto: foto, candidatos: candidatos, problema: problema, umbral: _local.umbral),
    ));
    if (elegido == null) return;
    final enviada = await _local.marcar(
      empleadoId: elegido.persona.empleadoId,
      nombre: elegido.persona.nombre,
      tipo: tipo,
      similitud: elegido.similitud,
      observacion: elegido.observacion,
      foto: foto,
    );
    if (!mounted) return;
    avisar(
        context,
        '${tipo == 'ENTRADA' ? 'Entrada' : 'Salida'} de ${elegido.persona.nombre}: '
        '${enviada ? 'registrada' : 'guardada en el celular (se enviará con conexión)'}');
    _mostrarRechazadas();
    if (enviada) {
      _actualizar(silencioso: true);
    } else {
      setState(() => _estado = '${_local.pendientes.length} marcaciones sin enviar');
    }
  }

  Future<void> _manual() async {
    final r = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const _ManualScreen()));
    if (r == true) _actualizar(silencioso: true);
  }

  Future<void> _registrarRostro() async {
    final r = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const RegistrarRostroScreen()));
    if (r == true) _actualizar(silencioso: true);
  }

  String _actualizadoTexto() {
    final a = _local.actualizado;
    if (a == null) return 'Sin sincronizar';
    return 'Actualizado ${fecha(a.toIso8601String())} ${a.hour.toString().padLeft(2, '0')}:${a.minute.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final pend = _local.pendientes.length;
    return Stack(children: [
      RefreshIndicator(
        onRefresh: _actualizar,
        child: ListView(padding: const EdgeInsets.all(16), children: [
          Card(
            child: ListTile(
              leading: Icon(_estado == null ? Icons.cloud_done_outlined : Icons.cloud_off_outlined,
                  color: _estado == null ? Colors.green : Colors.orange),
              title: Text('${_local.personas.length} rostros en el celular'),
              subtitle: Text(_estado ?? _actualizadoTexto()),
              trailing: IconButton(tooltip: 'Sincronizar', icon: const Icon(Icons.sync), onPressed: _actualizar),
            ),
          ),
          if (pend > 0)
            Card(
              color: Colors.orange.shade50,
              child: ListTile(
                leading: const Icon(Icons.schedule_send, color: Colors.orange),
                title: Text('$pend marcaciones guardadas sin conexión'),
                subtitle: const Text('Se envían al recuperar la señal'),
                trailing: TextButton(onPressed: _actualizar, child: const Text('Enviar')),
              ),
            ),
          const SizedBox(height: 12),
          Row(children: [
            Expanded(child: _BotonGrande('ENTRADA', Icons.login, Colors.green.shade700, () => _marcar('ENTRADA'))),
            const SizedBox(width: 12),
            Expanded(child: _BotonGrande('SALIDA', Icons.logout, Colors.blue.shade700, () => _marcar('SALIDA'))),
          ]),
          const SizedBox(height: 12),
          OutlinedButton.icon(
              onPressed: _registrarRostro,
              icon: const Icon(Icons.face_retouching_natural),
              label: const Text('Registrar rostro de un trabajador')),
          const SizedBox(height: 8),
          TextButton.icon(onPressed: _manual, icon: const Icon(Icons.edit_note), label: const Text('Marcación manual (sin foto)')),
          const SizedBox(height: 16),
          if (_hoy.isNotEmpty) const Text('Marcaciones de hoy', style: TextStyle(fontWeight: FontWeight.w700)),
          ..._hoy.reversed.map((m) => ListTile(
                dense: true,
                leading: Icon(m['tipo'] == 'ENTRADA' ? Icons.login : Icons.logout,
                    color: m['tipo'] == 'ENTRADA' ? Colors.green.shade700 : Colors.blue.shade700),
                title: Text(m['empleado']),
                subtitle: Text('${(m['fechaHora'] as String).substring(11, 16)} · ${etiqueta(m['metodo'])}'
                    '${m['anulada'] == true ? ' · anulada' : ''}'),
              )),
        ]),
      ),
      if (_ocupado)
        Container(
          color: Colors.black38,
          child: const Center(
            child: Card(
              child: Padding(
                padding: EdgeInsets.all(20),
                child: Row(mainAxisSize: MainAxisSize.min, children: [
                  CircularProgressIndicator(),
                  SizedBox(width: 16),
                  Text('Reconociendo rostro...'),
                ]),
              ),
            ),
          ),
        ),
    ]);
  }
}

class _BotonGrande extends StatelessWidget {
  final String texto;
  final IconData icono;
  final Color color;
  final VoidCallback onTap;
  const _BotonGrande(this.texto, this.icono, this.color, this.onTap);

  @override
  Widget build(BuildContext context) => SizedBox(
        height: 120,
        child: FilledButton(
          style: FilledButton.styleFrom(backgroundColor: color, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16))),
          onPressed: onTap,
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icono, size: 40),
            const SizedBox(height: 8),
            Text(texto, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          ]),
        ),
      );
}

class _Eleccion {
  final Persona persona;
  final double? similitud;
  final String? observacion;
  _Eleccion(this.persona, this.similitud, this.observacion);
}

/// Muestra la foto tomada junto a la registrada del trabajador reconocido para que la SISO confirme.
class _ConfirmarScreen extends StatefulWidget {
  final String tipo;
  final String foto;
  final List<Coincidencia> candidatos;
  final String? problema;
  final double umbral;
  const _ConfirmarScreen({required this.tipo, required this.foto, required this.candidatos, this.problema, required this.umbral});

  @override
  State<_ConfirmarScreen> createState() => _ConfirmarScreenState();
}

class _ConfirmarScreenState extends State<_ConfirmarScreen> {
  late Coincidencia? _sel = widget.candidatos.isEmpty ? null : widget.candidatos.first;
  final _obs = TextEditingController();
  String _filtro = '';

  @override
  void dispose() {
    _obs.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sel = _sel;
    final coincide = sel != null && sel.similitud >= widget.umbral;
    final todos = AsistenciaLocal.instance.personas
        .where((p) => '${p.nombre} ${p.cedula}'.toLowerCase().contains(_filtro.toLowerCase()))
        .toList();
    return Scaffold(
      appBar: AppBar(title: Text('Confirmar ${widget.tipo.toLowerCase()}')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        if (widget.problema != null)
          Container(
            padding: const EdgeInsets.all(12),
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(color: Colors.orange.shade50, borderRadius: BorderRadius.circular(10)),
            child: Text('${widget.problema}\nPuede volver y tomar la foto de nuevo, o elegir al trabajador de la lista '
                '(quedará para verificación).'),
          ),
        Row(children: [
          Expanded(child: _Foto(File(widget.foto), 'Foto tomada')),
          const SizedBox(width: 8),
          Expanded(
            child: sel == null
                ? const _SinFoto()
                : FutureBuilder<String>(
                    future: AsistenciaLocal.instance.rutaFotoReferencia(sel.persona.empleadoId),
                    builder: (_, s) =>
                        s.hasData && File(s.data!).existsSync() ? _Foto(File(s.data!), 'Foto registrada') : const _SinFoto(),
                  ),
          ),
        ]),
        const SizedBox(height: 12),
        if (sel != null)
          Card(
            color: coincide ? Colors.green.shade50 : Colors.orange.shade50,
            child: ListTile(
              title: Text(sel.persona.nombre, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
              subtitle: Text('${sel.persona.cedula} · ${sel.persona.cargo}'),
              trailing: Column(mainAxisAlignment: MainAxisAlignment.center, children: [
                Text('${(sel.similitud * 100).round()} %', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 18)),
                Text(coincide ? 'Coincide' : 'Revisar',
                    style: TextStyle(color: coincide ? Colors.green.shade800 : Colors.orange.shade900)),
              ]),
            ),
          ),
        if (sel != null && !coincide)
          Padding(
            padding: const EdgeInsets.only(top: 8),
            child: TextField(
              controller: _obs,
              decoration: const InputDecoration(labelText: 'Observación (opcional)', hintText: 'Ej.: verificado con la cédula'),
            ),
          ),
        const SizedBox(height: 12),
        FilledButton.icon(
          onPressed: sel == null
              ? null
              : () => Navigator.pop(context, _Eleccion(sel.persona, sel.similitud, _obs.text.trim().isEmpty ? null : _obs.text.trim())),
          icon: const Icon(Icons.check),
          label: Text(sel == null
              ? 'Elija al trabajador'
              : 'Confirmar ${widget.tipo.toLowerCase()} de ${sel.persona.nombre.split(' ').first}'),
          style: FilledButton.styleFrom(padding: const EdgeInsets.symmetric(vertical: 14)),
        ),
        const SizedBox(height: 20),
        const Text('¿No es la persona correcta? Elíjala:', style: TextStyle(fontWeight: FontWeight.w600)),
        TextField(
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar por nombre o cédula', isDense: true),
          onChanged: (v) => setState(() => _filtro = v),
        ),
        ...todos.take(30).map((p) {
          final c = widget.candidatos.where((x) => x.persona.empleadoId == p.empleadoId).firstOrNull;
          return ListTile(
            dense: true,
            selected: sel?.persona.empleadoId == p.empleadoId,
            title: Text(p.nombre),
            subtitle: Text(p.cedula),
            trailing: c == null ? null : Text('${(c.similitud * 100).round()} %'),
            onTap: () => setState(() => _sel = c ?? Coincidencia(p, 0)),
          );
        }),
      ]),
    );
  }
}

class _Foto extends StatelessWidget {
  final File archivo;
  final String titulo;
  const _Foto(this.archivo, this.titulo);

  @override
  Widget build(BuildContext context) => Column(children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: AspectRatio(aspectRatio: 3 / 4, child: Image.file(archivo, fit: BoxFit.cover)),
        ),
        const SizedBox(height: 4),
        Text(titulo, style: const TextStyle(fontSize: 12, color: Colors.black54)),
      ]);
}

class _SinFoto extends StatelessWidget {
  const _SinFoto();

  @override
  Widget build(BuildContext context) => AspectRatio(
        aspectRatio: 3 / 4,
        child: Container(
          decoration: BoxDecoration(color: Colors.grey.shade200, borderRadius: BorderRadius.circular(12)),
          child: const Icon(Icons.person_outline, size: 48, color: Colors.black26),
        ),
      );
}

/// Marcacion sin foto (requiere motivo): por ejemplo, trabajador sin rostro registrado.
class _ManualScreen extends StatefulWidget {
  const _ManualScreen();

  @override
  State<_ManualScreen> createState() => _ManualScreenState();
}

class _ManualScreenState extends State<_ManualScreen> {
  List<Persona> _personas = [];
  Persona? _sel;
  String _tipo = 'ENTRADA';
  final _obs = TextEditingController();
  String _filtro = '';
  bool _enviando = false;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final r = (await Api.instance.get('/Employees/Options')) as List;
      _personas = r.map((e) => Persona(e['id'], e['cedula'] ?? '', e['nombre'] ?? '', e['cargo'] ?? '', const [], '')).toList();
    } catch (_) {
      _personas = AsistenciaLocal.instance.personas;
    }
    if (mounted) setState(() {});
  }

  @override
  void dispose() {
    _obs.dispose();
    super.dispose();
  }

  Future<void> _enviar() async {
    if (_sel == null || _obs.text.trim().length < 3) {
      avisar(context, 'Elija al trabajador y escriba el motivo', error: true);
      return;
    }
    setState(() => _enviando = true);
    final ok = await AsistenciaLocal.instance
        .marcar(empleadoId: _sel!.empleadoId, nombre: _sel!.nombre, tipo: _tipo, observacion: _obs.text.trim());
    if (!mounted) return;
    avisar(context, ok ? 'Marcación manual registrada' : 'Guardada en el celular; se enviará con conexión');
    Navigator.pop(context, true);
  }

  @override
  Widget build(BuildContext context) {
    final lista =
        _personas.where((p) => '${p.nombre} ${p.cedula}'.toLowerCase().contains(_filtro.toLowerCase())).take(40).toList();
    return Scaffold(
      appBar: AppBar(title: const Text('Marcación manual')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        const Text('Úsela solo si no es posible tomar la foto. Queda marcada para revisión de Talento Humano.',
            style: TextStyle(color: Colors.black54)),
        const SizedBox(height: 12),
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'ENTRADA', label: Text('Entrada')),
            ButtonSegment(value: 'SALIDA', label: Text('Salida')),
          ],
          selected: {_tipo},
          onSelectionChanged: (s) => setState(() => _tipo = s.first),
        ),
        const SizedBox(height: 12),
        TextField(
            controller: _obs,
            decoration: const InputDecoration(labelText: 'Motivo *', hintText: 'Ej.: celular sin batería, la foto no funcionó')),
        const SizedBox(height: 12),
        TextField(
          decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar trabajador', isDense: true),
          onChanged: (v) => setState(() => _filtro = v),
        ),
        ...lista.map((p) => ListTile(
              dense: true,
              selected: _sel?.empleadoId == p.empleadoId,
              leading: Icon(_sel?.empleadoId == p.empleadoId ? Icons.radio_button_checked : Icons.radio_button_off),
              title: Text(p.nombre),
              subtitle: Text(p.cedula),
              onTap: () => setState(() => _sel = p),
            )),
        const SizedBox(height: 12),
        FilledButton(onPressed: _enviando ? null : _enviar, child: const Text('Registrar')),
      ]),
    );
  }
}
