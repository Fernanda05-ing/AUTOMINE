import 'dart:convert';
import 'dart:io';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:http/http.dart' as http;
import 'package:open_filex/open_filex.dart';
import 'package:path_provider/path_provider.dart';

/// URL de la API. Se define al compilar:
///   flutter build apk --dart-define=API_URL=https://su-dominio.com
/// Por defecto apunta a la maquina local vista desde el emulador de Android.
const String apiUrl = String.fromEnvironment('API_URL', defaultValue: 'http://10.0.2.2:8081');

class ApiException implements Exception {
  final int status;
  final String message;
  ApiException(this.status, this.message);
  @override
  String toString() => message;
}

class Usuario {
  final int id;
  final String nombre;
  final String email;
  final String rol;
  final String rolNombre;
  final int? empleadoId;
  String? telefono;
  bool debeCambiarPassword;

  Usuario.fromJson(Map<String, dynamic> j)
      : id = j['id'],
        nombre = j['nombre'],
        email = j['email'],
        rol = j['rol'],
        rolNombre = j['rolNombre'],
        empleadoId = j['empleadoId'],
        telefono = j['telefono'],
        debeCambiarPassword = j['debeCambiarPassword'] == true;

  Map<String, dynamic> toJson() =>
      {'id': id, 'nombre': nombre, 'email': email, 'rol': rol, 'rolNombre': rolNombre, 'empleadoId': empleadoId, 'telefono': telefono, 'debeCambiarPassword': debeCambiarPassword};

  bool tiene(List<String> roles) => rol == 'ADMIN' || roles.contains(rol);
}

/// Cliente de la API AUTOMINE. El token JWT se guarda cifrado en el dispositivo.
class Api {
  Api._();
  static final Api instance = Api._();

  final _storage = const FlutterSecureStorage();
  String? _token;
  Usuario? usuario;
  void Function()? onSesionVencida;

  String get _base => '$apiUrl/AutomineApi';

  Future<bool> restaurarSesion() async {
    _token = await _storage.read(key: 'token');
    final u = await _storage.read(key: 'usuario');
    if (_token == null || u == null) return false;
    usuario = Usuario.fromJson(jsonDecode(u));
    try {
      usuario = Usuario.fromJson(await get('/Profile'));
      return true;
    } catch (_) {
      await _limpiar();
      return false;
    }
  }

  Future<Usuario> login(String email, String password) async {
    final r = await _send('POST', '/Login', body: {'email': email, 'password': password}, auth: false);
    _token = r['token'];
    usuario = Usuario.fromJson(r['usuario']);
    await _storage.write(key: 'token', value: _token);
    await _storage.write(key: 'usuario', value: jsonEncode(usuario!.toJson()));
    return usuario!;
  }

  Future<void> logout() async {
    try {
      await _send('POST', '/Logout', body: {});
    } catch (_) {}
    await _limpiar();
  }

  Future<void> _limpiar() async {
    _token = null;
    usuario = null;
    await _storage.deleteAll();
  }

  Future<dynamic> get(String path, [Map<String, String>? query]) => _send('GET', path, query: query);
  Future<dynamic> post(String path, Object body) => _send('POST', path, body: body);
  Future<dynamic> put(String path, Object body) => _send('PUT', path, body: body);

  Future<dynamic> _send(String method, String path,
      {Object? body, Map<String, String>? query, bool auth = true}) async {
    final uri = Uri.parse('$_base$path').replace(queryParameters: query);
    final headers = <String, String>{'Content-Type': 'application/json', 'User-Agent': 'AutomineMovil/1.0'};
    if (auth && _token != null) headers['Authorization'] = 'Bearer $_token';
    http.Response res;
    try {
      final req = http.Request(method, uri)..headers.addAll(headers);
      if (body != null) req.body = jsonEncode(body);
      res = await http.Response.fromStream(await req.send().timeout(const Duration(seconds: 25)));
    } on SocketException {
      throw ApiException(0, 'Sin conexión con el servidor');
    } catch (e) {
      throw ApiException(0, 'No fue posible conectar con el servidor');
    }
    if (res.statusCode == 401 && auth) {
      await _limpiar();
      onSesionVencida?.call();
      throw ApiException(401, 'La sesión vencio. Ingrese de nuevo');
    }
    final text = utf8.decode(res.bodyBytes);
    if (res.statusCode >= 400) {
      String msg = 'Error ${res.statusCode}';
      try {
        msg = jsonDecode(text)['message'] ?? msg;
      } catch (_) {}
      throw ApiException(res.statusCode, msg);
    }
    return text.isEmpty ? null : jsonDecode(text);
  }

  /// Descarga un PDF autenticado, lo guarda en el dispositivo y lo abre.
  Future<void> abrirPdf(String path, String nombre) async {
    final res = await http.get(Uri.parse('$_base$path'),
        headers: {if (_token != null) 'Authorization': 'Bearer $_token'}).timeout(const Duration(seconds: 40));
    if (res.statusCode >= 400) {
      String msg = 'No se pudo descargar el archivo';
      try {
        msg = jsonDecode(utf8.decode(res.bodyBytes))['message'] ?? msg;
      } catch (_) {}
      throw ApiException(res.statusCode, msg);
    }
    final dir = await getApplicationDocumentsDirectory();
    final file = File('${dir.path}/$nombre');
    await file.writeAsBytes(res.bodyBytes);
    await OpenFilex.open(file.path, type: 'application/pdf');
  }
}
