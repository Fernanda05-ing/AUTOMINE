import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';

import 'api.dart';

/// Notificaciones push (Firebase Cloud Messaging).
///
/// Queda desactivado si la app se compila sin los datos del proyecto de Firebase:
///   flutter build apk --dart-define=FCM_API_KEY=... --dart-define=FCM_APP_ID=...
///     --dart-define=FCM_SENDER_ID=... --dart-define=FCM_PROJECT_ID=...
/// (Firebase > Configuracion del proyecto > Tus apps > app Android). Asi no hace falta
/// incluir google-services.json en el codigo fuente.
class Push {
  Push._();

  static const _apiKey = String.fromEnvironment('FCM_API_KEY');
  static const _appId = String.fromEnvironment('FCM_APP_ID');
  static const _senderId = String.fromEnvironment('FCM_SENDER_ID');
  static const _projectId = String.fromEnvironment('FCM_PROJECT_ID');

  static bool get configurado => _apiKey.isNotEmpty && _appId.isNotEmpty && _senderId.isNotEmpty && _projectId.isNotEmpty;
  static bool _iniciado = false;
  static String? _token;

  static Future<bool> _iniciar() async {
    if (!configurado) return false;
    if (_iniciado) return true;
    try {
      await Firebase.initializeApp(
        options: const FirebaseOptions(apiKey: _apiKey, appId: _appId, messagingSenderId: _senderId, projectId: _projectId),
      );
      _iniciado = true;
      FirebaseMessaging.instance.onTokenRefresh.listen((t) {
        _token = t;
        _enviarToken(t);
      });
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Pide permiso y registra el celular para el usuario con sesion iniciada.
  static Future<void> registrar() async {
    if (!await _iniciar()) return;
    try {
      await FirebaseMessaging.instance.requestPermission();
      final t = await FirebaseMessaging.instance.getToken();
      if (t != null) {
        _token = t;
        await _enviarToken(t);
      }
    } catch (_) {}
  }

  static Future<void> _enviarToken(String t) async {
    try {
      await Api.instance.post('/Devices', {'token': t, 'plataforma': 'ANDROID'});
    } catch (_) {}
  }

  /// Al cerrar sesion el celular deja de recibir notificaciones de ese usuario.
  static Future<void> quitar() async {
    final t = _token;
    if (t == null) return;
    try {
      await Api.instance.post('/Devices/Remove', {'token': t});
    } catch (_) {}
  }
}
