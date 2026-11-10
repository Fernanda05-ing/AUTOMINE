import 'dart:math';

import 'package:google_mlkit_face_detection/google_mlkit_face_detection.dart';

/// Version del algoritmo de plantilla. Si se cambia por un modelo mas preciso
/// (por ejemplo una red neuronal), se sube la version y se vuelven a registrar los rostros.
const versionPlantilla = 'geo-v1';

/// Contornos usados para la huella geometrica del rostro (orden fijo).
const _contornos = [
  FaceContourType.face,
  FaceContourType.leftEyebrowTop,
  FaceContourType.rightEyebrowTop,
  FaceContourType.leftEye,
  FaceContourType.rightEye,
  FaceContourType.noseBridge,
  FaceContourType.noseBottom,
  FaceContourType.upperLipTop,
  FaceContourType.lowerLipBottom,
];

class ErrorRostro implements Exception {
  final String mensaje;
  ErrorRostro(this.mensaje);
  @override
  String toString() => mensaje;
}

/// Reconocimiento facial en el celular, sin conexion (ML Kit de Google).
///
/// La plantilla es una huella geometrica: los puntos del contorno de la cara, ojos,
/// cejas, nariz y boca, normalizados respecto a los ojos (posicion, giro y escala).
/// Sirve para identificar entre el personal de la empresa con buena luz; la SISO
/// confirma siempre visualmente y queda la foto como evidencia.
class Facial {
  Facial._();
  static final Facial instance = Facial._();

  final _detector = FaceDetector(
    options: FaceDetectorOptions(
      enableContours: true,
      enableClassification: true,
      performanceMode: FaceDetectorMode.accurate,
      minFaceSize: 0.15,
    ),
  );

  /// Calcula la plantilla del unico rostro de la foto, o lanza [ErrorRostro] con la razon.
  Future<List<double>> plantilla(String rutaFoto) async {
    final caras = await _detector.processImage(InputImage.fromFilePath(rutaFoto));
    if (caras.isEmpty) throw ErrorRostro('No se detectó ningún rostro. Acérquese y mejore la luz.');
    if (caras.length > 1) throw ErrorRostro('Hay varias personas en la foto. Tome solo al trabajador.');
    final c = caras.first;
    final giro = [c.headEulerAngleY ?? 0, c.headEulerAngleZ ?? 0, c.headEulerAngleX ?? 0].map((a) => a.abs()).reduce(max);
    if (giro > 15) throw ErrorRostro('El trabajador debe mirar de frente a la cámara.');
    final ojos = [(c.leftEyeOpenProbability ?? 1), (c.rightEyeOpenProbability ?? 1)];
    if (ojos.every((p) => p < 0.3)) throw ErrorRostro('Los ojos parecen cerrados. Tome la foto de nuevo.');

    final ojoIzq = _centro(c.contours[FaceContourType.leftEye]?.points);
    final ojoDer = _centro(c.contours[FaceContourType.rightEye]?.points);
    if (ojoIzq == null || ojoDer == null) throw ErrorRostro('No se distinguen los ojos. Mejore la luz.');
    final origen = Point((ojoIzq.x + ojoDer.x) / 2, (ojoIzq.y + ojoDer.y) / 2);
    final dist = ojoIzq.distanceTo(ojoDer);
    if (dist < 25) throw ErrorRostro('El rostro está muy lejos. Acérquese.');
    final angulo = atan2(ojoDer.y - ojoIzq.y, ojoDer.x - ojoIzq.x);
    final cosA = cos(-angulo), sinA = sin(-angulo);

    final v = <double>[];
    for (final tipo in _contornos) {
      final puntos = c.contours[tipo]?.points;
      if (puntos == null || puntos.isEmpty) throw ErrorRostro('El rostro no se ve completo. Tome la foto de nuevo.');
      for (final p in puntos) {
        final x = (p.x - origen.x) / dist, y = (p.y - origen.y) / dist;
        v.add(x * cosA - y * sinA);
        v.add(x * sinA + y * cosA);
      }
    }
    return v;
  }

  /// Promedio de varias plantillas del mismo trabajador (registro con varias fotos).
  static List<double> promedio(List<List<double>> plantillas) {
    final n = plantillas.first.length;
    if (plantillas.any((p) => p.length != n)) throw ErrorRostro('Las fotos no son comparables. Repita el registro.');
    return List.generate(n, (i) => plantillas.map((p) => p[i]).reduce((a, b) => a + b) / plantillas.length);
  }

  /// Similitud de 0 a 1 entre dos plantillas (1 = identicas).
  static double similitud(List<double> a, List<double> b) {
    if (a.length != b.length || a.isEmpty) return 0;
    var suma = 0.0;
    for (var i = 0; i < a.length; i++) {
      final d = a[i] - b[i];
      suma += d * d;
    }
    final rms = sqrt(suma / a.length);
    return exp(-pow(rms / 0.08, 2)).toDouble();
  }

  static Point<double>? _centro(List<Point<int>>? puntos) {
    if (puntos == null || puntos.isEmpty) return null;
    final x = puntos.map((p) => p.x).reduce((a, b) => a + b) / puntos.length;
    final y = puntos.map((p) => p.y).reduce((a, b) => a + b) / puntos.length;
    return Point(x, y);
  }
}
