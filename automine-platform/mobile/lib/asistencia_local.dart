import 'dart:convert';
import 'dart:io';
import 'dart:math';

import 'package:path_provider/path_provider.dart';

import 'api.dart';
import 'facial.dart';

/// Trabajador con rostro registrado (copia local para reconocer sin conexion).
class Persona {
  final int empleadoId;
  final String cedula;
  final String nombre;
  final String cargo;
  final List<double> plantilla;
  final String version;

  Persona(this.empleadoId, this.cedula, this.nombre, this.cargo, this.plantilla, this.version);

  factory Persona.fromJson(Map<String, dynamic> j) => Persona(
        j['empleadoId'],
        j['cedula'] ?? '',
        j['nombre'] ?? '',
        j['cargo'] ?? '',
        (j['plantilla'] is String ? jsonDecode(j['plantilla']) : j['plantilla'] as List).cast<num>().map((e) => e.toDouble()).toList(),
        j['versionModelo'] ?? j['version'] ?? versionPlantilla,
      );

  Map<String, dynamic> toJson() =>
      {'empleadoId': empleadoId, 'cedula': cedula, 'nombre': nombre, 'cargo': cargo, 'plantilla': plantilla, 'version': version};
}

class Coincidencia {
  final Persona persona;
  final double similitud;
  Coincidencia(this.persona, this.similitud);
}

/// Marcacion guardada en el celular hasta poder enviarla.
class MarcaPendiente {
  final String id;
  final int empleadoId;
  final String nombre;
  final String tipo;
  final DateTime fechaHora;
  final double? similitud;
  final String? observacion;
  final String? foto;

  MarcaPendiente(this.id, this.empleadoId, this.nombre, this.tipo, this.fechaHora, this.similitud, this.observacion, this.foto);

  factory MarcaPendiente.fromJson(Map<String, dynamic> j) => MarcaPendiente(j['id'], j['empleadoId'], j['nombre'], j['tipo'],
      DateTime.parse(j['fechaHora']), (j['similitud'] as num?)?.toDouble(), j['observacion'], j['foto']);

  Map<String, dynamic> toJson() => {
        'id': id,
        'empleadoId': empleadoId,
        'nombre': nombre,
        'tipo': tipo,
        'fechaHora': fechaHora.toIso8601String(),
        'similitud': similitud,
        'observacion': observacion,
        'foto': foto,
      };
}

/// Almacen local de la SISO: plantillas, fotos de referencia y cola de marcaciones.
class AsistenciaLocal {
  AsistenciaLocal._();
  static final AsistenciaLocal instance = AsistenciaLocal._();

  List<Persona> personas = [];
  List<MarcaPendiente> pendientes = [];
  DateTime? actualizado;
  double umbral = 0.80;
  bool _cargado = false;

  Future<Directory> _dir() async {
    final d = Directory('${(await getApplicationDocumentsDirectory()).path}/asistencia');
    if (!await d.exists()) await d.create(recursive: true);
    return d;
  }

  Future<File> _archivo(String nombre) async => File('${(await _dir()).path}/$nombre');

  Future<void> cargar() async {
    if (_cargado) return;
    try {
      final f = await _archivo('rostros.json');
      if (await f.exists()) {
        final j = jsonDecode(await f.readAsString());
        personas = (j['personas'] as List).map((e) => Persona.fromJson(e)).toList();
        actualizado = j['actualizado'] == null ? null : DateTime.parse(j['actualizado']);
        umbral = (j['umbral'] as num?)?.toDouble() ?? umbral;
      }
      final p = await _archivo('pendientes.json');
      if (await p.exists()) {
        pendientes = (jsonDecode(await p.readAsString()) as List).map((e) => MarcaPendiente.fromJson(e)).toList();
      }
    } catch (_) {
      // Archivo dañado: se reconstruye en la siguiente sincronizacion
    }
    _cargado = true;
  }

  Future<void> _guardarRostros() async {
    final f = await _archivo('rostros.json');
    await f.writeAsString(jsonEncode({
      'personas': personas.map((p) => p.toJson()).toList(),
      'actualizado': actualizado?.toIso8601String(),
      'umbral': umbral,
    }));
  }

  Future<void> _guardarPendientes() async {
    final f = await _archivo('pendientes.json');
    await f.writeAsString(jsonEncode(pendientes.map((p) => p.toJson()).toList()));
  }

  Future<String> rutaFotoReferencia(int empleadoId) async => '${(await _dir()).path}/ref_$empleadoId.jpg';

  /// Descarga las plantillas y fotos de referencia (requiere conexion).
  Future<void> actualizarRostros() async {
    await cargar();
    final lista = (await Api.instance.get('/Faces')) as List;
    try {
      final cfg = await Api.instance.get('/Faces/Consent');
      umbral = (cfg['umbral'] as num).toDouble();
    } catch (_) {}
    personas = lista.map((e) => Persona.fromJson(e as Map<String, dynamic>)).toList();
    actualizado = DateTime.now();
    await _guardarRostros();
    for (final p in personas) {
      final ruta = await rutaFotoReferencia(p.empleadoId);
      if (!await File(ruta).exists()) {
        try {
          await Api.instance.guardarArchivo('/Employee/${p.empleadoId}/Face/Photo', ruta);
        } catch (_) {}
      }
    }
  }

  /// Personas ordenadas de mayor a menor similitud con la plantilla capturada.
  List<Coincidencia> reconocer(List<double> plantilla) {
    final r = personas
        .where((p) => p.version == versionPlantilla)
        .map((p) => Coincidencia(p, Facial.similitud(plantilla, p.plantilla)))
        .toList()
      ..sort((a, b) => b.similitud.compareTo(a.similitud));
    return r;
  }

  /// Guarda la marcacion y trata de enviarla de inmediato.
  Future<bool> marcar({
    required int empleadoId,
    required String nombre,
    required String tipo,
    double? similitud,
    String? observacion,
    String? foto,
  }) async {
    await cargar();
    String? copia;
    if (foto != null) {
      copia = '${(await _dir()).path}/marca_${DateTime.now().millisecondsSinceEpoch}.jpg';
      await File(foto).copy(copia);
    }
    final m = MarcaPendiente(_id(), empleadoId, nombre, tipo, DateTime.now(), similitud, observacion, copia);
    pendientes.add(m);
    await _guardarPendientes();
    await sincronizar();
    return !pendientes.any((p) => p.id == m.id);
  }

  /// Envia las marcaciones pendientes. Devuelve cuantas quedaron sin enviar.
  Future<int> sincronizar() async {
    await cargar();
    for (final m in List.of(pendientes)) {
      try {
        final campos = {
          'empleadoId': '${m.empleadoId}',
          'tipo': m.tipo,
          'fechaHora': m.fechaHora.toIso8601String().substring(0, 19),
          'dispositivoId': m.id,
          if (m.similitud != null) 'similitud': m.similitud!.toStringAsFixed(4),
          if (m.observacion != null && m.observacion!.isNotEmpty) 'observacion': m.observacion!,
        };
        if (m.foto != null && await File(m.foto!).exists()) {
          await Api.instance.enviarArchivo('POST', '/Attendance/Mark', campos: campos, archivo: m.foto!, campoArchivo: 'foto');
        } else {
          await Api.instance.enviarFormulario('/Attendance/Mark', campos);
        }
        pendientes.removeWhere((p) => p.id == m.id);
        await _guardarPendientes();
        if (m.foto != null) {
          try {
            await File(m.foto!).delete();
          } catch (_) {}
        }
      } on ApiException catch (e) {
        if (e.status == 0 || e.status >= 500) break; // sin conexion: se intenta despues
        // Rechazada por el servidor (datos invalidos): se descarta para no bloquear la cola
        pendientes.removeWhere((p) => p.id == m.id);
        await _guardarPendientes();
        rechazadas.add('${m.nombre} (${m.tipo.toLowerCase()}): ${e.message}');
      }
    }
    return pendientes.length;
  }

  /// Mensajes de marcaciones que el servidor rechazo (para mostrarlos a la SISO).
  final List<String> rechazadas = [];

  static String _id() {
    final r = Random.secure();
    return List.generate(16, (_) => r.nextInt(256).toRadixString(16).padLeft(2, '0')).join();
  }
}
