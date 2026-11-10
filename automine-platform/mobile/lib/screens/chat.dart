import 'dart:async';

import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api.dart';
import '../ui.dart';

class Contacto {
  final int id;
  final String nombre;
  final String rolNombre;
  final String? cargo;
  final String? telefono;

  Contacto.fromJson(Map<String, dynamic> j)
      : id = j['id'],
        nombre = j['nombre'],
        rolNombre = j['rolNombre'] ?? '',
        cargo = j['cargo'],
        telefono = j['telefono'];

  String get detalle => cargo ?? rolNombre;
}

class Mensaje {
  final int id;
  final String texto;
  final bool leido;
  final DateTime fecha;
  final bool mio;

  Mensaje.fromJson(Map<String, dynamic> j)
      : id = j['id'],
        texto = j['texto'],
        leido = j['leido'] == true,
        fecha = DateTime.parse(j['fecha']),
        mio = j['mio'] == true;
}

String iniciales(String nombre) {
  final p = nombre.trim().split(RegExp(r'\s+')).where((w) => w.isNotEmpty).toList();
  if (p.isEmpty) return '?';
  return (p.first[0] + (p.length > 1 ? p[1][0] : '')).toUpperCase();
}

/// Celular colombiano en 10 dígitos (sin el 57), o null si no es válido.
String? celular10(String? t) {
  if (t == null) return null;
  var d = t.replaceAll(RegExp(r'[^0-9]'), '');
  if (d.length == 12 && d.startsWith('57')) d = d.substring(2);
  return d.length == 10 && d.startsWith('3') ? d : null;
}

String formatoCelular(String? t) {
  final d = celular10(t);
  return d == null ? (t ?? '') : '${d.substring(0, 3)} ${d.substring(3, 6)} ${d.substring(6)}';
}

String hora(DateTime d) {
  final l = d.toLocal();
  final hoy = DateTime.now();
  final mismoDia = l.year == hoy.year && l.month == hoy.month && l.day == hoy.day;
  return mismoDia ? DateFormat('h:mm a', 'es_CO').format(l) : DateFormat('dd MMM', 'es_CO').format(l);
}

/// WhatsApp solo para contacto inmediato; lo demás va por el chat interno.
Future<void> abrirWhatsApp(BuildContext context, String? telefono, String mensaje) async {
  final d = celular10(telefono);
  if (d == null) return avisar(context, 'Este contacto no tiene celular registrado', error: true);
  final uri = Uri.parse('https://wa.me/57$d?text=${Uri.encodeComponent(mensaje)}');
  if (!await launchUrl(uri, mode: LaunchMode.externalApplication) && context.mounted) {
    avisar(context, 'No se pudo abrir WhatsApp', error: true);
  }
}

Future<void> llamar(BuildContext context, String? telefono) async {
  final d = celular10(telefono);
  if (d == null) return;
  if (!await launchUrl(Uri.parse('tel:$d')) && context.mounted) {
    avisar(context, 'No se pudo abrir el marcador', error: true);
  }
}

class _Avatar extends StatelessWidget {
  final String nombre;
  const _Avatar(this.nombre);

  @override
  Widget build(BuildContext context) => CircleAvatar(
        backgroundColor: colorDorado,
        foregroundColor: colorCarbon,
        child: Text(iniciales(nombre), style: const TextStyle(fontWeight: FontWeight.w700)),
      );
}

/// Lista de conversaciones del chat interno. Se actualiza cada 10 segundos.
class ChatScreen extends StatefulWidget {
  /// Avisa a la navegación cuántos mensajes hay sin leer (para el contador).
  final void Function(int noLeidos)? onNoLeidos;
  const ChatScreen({super.key, this.onNoLeidos});

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  List<Map<String, dynamic>>? _conversaciones;
  String? _error;
  Timer? _timer;

  @override
  void initState() {
    super.initState();
    _cargar();
    _timer = Timer.periodic(const Duration(seconds: 10), (_) => _cargar());
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  Future<void> _cargar() async {
    try {
      final r = (await Api.instance.get('/Chat/Conversations') as List).cast<Map<String, dynamic>>();
      if (!mounted) return;
      setState(() {
        _conversaciones = r;
        _error = null;
      });
      widget.onNoLeidos?.call(r.fold<int>(0, (s, c) => s + ((c['noLeidos'] ?? 0) as int)));
    } catch (e) {
      if (mounted && _conversaciones == null) setState(() => _error = '$e');
    }
  }

  Future<void> _abrir(Contacto c) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => ConversacionScreen(contacto: c)));
    _cargar();
  }

  Future<void> _nueva() async {
    final c = await showModalBottomSheet<Contacto>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      builder: (_) => const _ElegirContacto(),
    );
    if (c != null) _abrir(c);
  }

  @override
  Widget build(BuildContext context) {
    Widget cuerpo;
    if (_conversaciones == null && _error == null) {
      cuerpo = const Center(child: CircularProgressIndicator());
    } else if (_conversaciones == null) {
      cuerpo = RefreshIndicator(onRefresh: _cargar, child: Vacio(_error!));
    } else if (_conversaciones!.isEmpty) {
      cuerpo = RefreshIndicator(
        onRefresh: _cargar,
        child: const Vacio('Aún no tiene conversaciones.\nPulse "Nueva" para escribirle a alguien.'),
      );
    } else {
      cuerpo = RefreshIndicator(
        onRefresh: _cargar,
        child: ListView.separated(
          padding: const EdgeInsets.only(bottom: 88),
          itemCount: _conversaciones!.length,
          separatorBuilder: (_, _) => const Divider(height: 1, indent: 72),
          itemBuilder: (_, i) {
            final c = _conversaciones![i];
            final contacto = Contacto.fromJson(c['contacto']);
            final noLeidos = (c['noLeidos'] ?? 0) as int;
            return ListTile(
              leading: _Avatar(contacto.nombre),
              title: Text(contacto.nombre, style: TextStyle(fontWeight: noLeidos > 0 ? FontWeight.w700 : FontWeight.w600)),
              subtitle: Text('${c['ultimoEsMio'] == true ? 'Tú: ' : ''}${c['ultimoMensaje']}',
                  maxLines: 1, overflow: TextOverflow.ellipsis),
              trailing: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(hora(DateTime.parse(c['fecha'])), style: Theme.of(context).textTheme.bodySmall),
                  if (noLeidos > 0) ...[
                    const SizedBox(height: 4),
                    Badge(label: Text('$noLeidos'), backgroundColor: colorPrimario),
                  ],
                ],
              ),
              onTap: () => _abrir(contacto),
            );
          },
        ),
      );
    }
    return Scaffold(
      body: cuerpo,
      floatingActionButton: FloatingActionButton.extended(
        heroTag: 'nuevo-chat',
        onPressed: _nueva,
        backgroundColor: colorPrimario,
        foregroundColor: Colors.white,
        icon: const Icon(Icons.add_comment_outlined),
        label: const Text('Nueva'),
      ),
    );
  }
}

class _ElegirContacto extends StatefulWidget {
  const _ElegirContacto();

  @override
  State<_ElegirContacto> createState() => _ElegirContactoState();
}

class _ElegirContactoState extends State<_ElegirContacto> {
  List<Contacto>? _contactos;
  String? _error;
  String _filtro = '';

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final r = await Api.instance.get('/Chat/Contacts') as List;
      if (mounted) setState(() => _contactos = r.map((e) => Contacto.fromJson(e)).toList());
    } catch (e) {
      if (mounted) setState(() => _error = '$e');
    }
  }

  @override
  Widget build(BuildContext context) {
    final q = _filtro.trim().toLowerCase();
    final lista = (_contactos ?? []).where((c) => '${c.nombre} ${c.detalle}'.toLowerCase().contains(q)).toList();
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
      child: SizedBox(
        height: MediaQuery.of(context).size.height * 0.75,
        child: Column(children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text('Nueva conversación', style: Theme.of(context).textTheme.titleMedium),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              decoration: const InputDecoration(prefixIcon: Icon(Icons.search), hintText: 'Buscar por nombre o cargo', isDense: true),
              onChanged: (v) => setState(() => _filtro = v),
            ),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: _error != null
                ? Center(child: Text(_error!))
                : _contactos == null
                    ? const Center(child: CircularProgressIndicator())
                    : lista.isEmpty
                        ? const Center(child: Text('No hay contactos'))
                        : ListView.builder(
                            itemCount: lista.length,
                            itemBuilder: (_, i) {
                              final c = lista[i];
                              return ListTile(
                                leading: _Avatar(c.nombre),
                                title: Text(c.nombre),
                                subtitle: Text([c.detalle, if (celular10(c.telefono) != null) formatoCelular(c.telefono)].join(' · ')),
                                onTap: () => Navigator.of(context).pop(c),
                              );
                            },
                          ),
          ),
        ]),
      ),
    );
  }
}

/// Conversación con un contacto. Trae los mensajes nuevos cada 4 segundos.
class ConversacionScreen extends StatefulWidget {
  final Contacto contacto;
  const ConversacionScreen({super.key, required this.contacto});

  @override
  State<ConversacionScreen> createState() => _ConversacionScreenState();
}

class _ConversacionScreenState extends State<ConversacionScreen> {
  final _texto = TextEditingController();
  final _scroll = ScrollController();
  final List<Mensaje> _mensajes = [];
  bool _cargando = true;
  bool _enviando = false;
  Timer? _timer;

  int get _ultimo => _mensajes.isEmpty ? 0 : _mensajes.last.id;

  @override
  void initState() {
    super.initState();
    _cargarTodo();
    _timer = Timer.periodic(const Duration(seconds: 4), (_) => _nuevos());
  }

  @override
  void dispose() {
    _timer?.cancel();
    _texto.dispose();
    _scroll.dispose();
    super.dispose();
  }

  Future<void> _cargarTodo() async {
    try {
      final r = await Api.instance.get('/Chat/Messages/${widget.contacto.id}') as List;
      if (!mounted) return;
      setState(() {
        _mensajes
          ..clear()
          ..addAll(r.map((e) => Mensaje.fromJson(e)));
        _cargando = false;
      });
      _bajar();
    } catch (e) {
      if (!mounted) return;
      setState(() => _cargando = false);
      avisar(context, '$e', error: true);
    }
  }

  Future<void> _nuevos() async {
    if (_cargando) return;
    try {
      final r = await Api.instance.get('/Chat/Messages/${widget.contacto.id}', {'despues': '$_ultimo'}) as List;
      if (!mounted || r.isEmpty) return;
      final ids = _mensajes.map((m) => m.id).toSet();
      setState(() => _mensajes.addAll(r.map((e) => Mensaje.fromJson(e)).where((m) => !ids.contains(m.id))));
      _bajar();
    } catch (_) {}
  }

  Future<void> _enviar() async {
    final t = _texto.text.trim();
    if (t.isEmpty || _enviando) return;
    setState(() => _enviando = true);
    try {
      final r = await Api.instance.post('/Chat/Messages', {'destinatarioId': widget.contacto.id, 'texto': t});
      if (!mounted) return;
      _texto.clear();
      final m = Mensaje.fromJson(r);
      setState(() {
        if (!_mensajes.any((x) => x.id == m.id)) _mensajes.add(m);
      });
      _bajar();
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _enviando = false);
    }
  }

  void _bajar() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent,
            duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final c = widget.contacto;
    final tieneCelular = celular10(c.telefono) != null;
    return Scaffold(
      backgroundColor: const Color(0xFFF7F5F2),
      appBar: AppBar(
        titleSpacing: 0,
        title: Row(children: [
          _Avatar(c.nombre),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(c.nombre, style: const TextStyle(fontSize: 16), overflow: TextOverflow.ellipsis),
              Text(
                [c.detalle, if (tieneCelular) formatoCelular(c.telefono)].join(' · '),
                style: const TextStyle(fontSize: 12, color: Colors.white70),
                overflow: TextOverflow.ellipsis,
              ),
            ]),
          ),
        ]),
        actions: [
          if (tieneCelular)
            IconButton(tooltip: 'Llamar', icon: const Icon(Icons.call_outlined), onPressed: () => llamar(context, c.telefono)),
          if (tieneCelular)
            TextButton.icon(
              style: TextButton.styleFrom(foregroundColor: const Color(0xFF4ADE80)),
              onPressed: () => abrirWhatsApp(context, c.telefono, 'Hola ${c.nombre.split(' ').first}, te escribo de AUTOMINE.'),
              icon: const Icon(Icons.chat_outlined, size: 18),
              label: const Text('Urgente'),
            ),
        ],
      ),
      body: Column(children: [
        Expanded(
          child: _cargando
              ? const Center(child: CircularProgressIndicator())
              : _mensajes.isEmpty
                  ? const Center(child: Text('Escriba el primer mensaje.'))
                  : ListView.builder(
                      controller: _scroll,
                      padding: const EdgeInsets.all(12),
                      itemCount: _mensajes.length,
                      itemBuilder: (_, i) => _Burbuja(_mensajes[i]),
                    ),
        ),
        Material(
          color: Colors.white,
          child: SafeArea(
            top: false,
            child: Padding(
              padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
              child: Row(crossAxisAlignment: CrossAxisAlignment.end, children: [
                Expanded(
                  child: TextField(
                    controller: _texto,
                    minLines: 1,
                    maxLines: 5,
                    maxLength: 2000,
                    textCapitalization: TextCapitalization.sentences,
                    decoration: InputDecoration(
                      hintText: 'Escribir a ${c.nombre.split(' ').first}...',
                      counterText: '',
                      isDense: true,
                      border: OutlineInputBorder(borderRadius: BorderRadius.circular(24)),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton.filled(
                  style: IconButton.styleFrom(backgroundColor: colorPrimario),
                  tooltip: 'Enviar',
                  onPressed: _enviando ? null : _enviar,
                  icon: _enviando
                      ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                      : const Icon(Icons.send),
                ),
              ]),
            ),
          ),
        ),
      ]),
    );
  }
}

class _Burbuja extends StatelessWidget {
  final Mensaje m;
  const _Burbuja(this.m);

  @override
  Widget build(BuildContext context) {
    return Align(
      alignment: m.mio ? Alignment.centerRight : Alignment.centerLeft,
      child: Container(
        constraints: BoxConstraints(maxWidth: MediaQuery.of(context).size.width * 0.78),
        margin: const EdgeInsets.symmetric(vertical: 4),
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 6),
        decoration: BoxDecoration(
          color: m.mio ? const Color(0xFFFDEFC7) : Colors.white,
          border: Border.all(color: m.mio ? const Color(0xFFF5D98A) : Colors.grey.shade300),
          borderRadius: BorderRadius.only(
            topLeft: const Radius.circular(14),
            topRight: const Radius.circular(14),
            bottomLeft: Radius.circular(m.mio ? 14 : 4),
            bottomRight: Radius.circular(m.mio ? 4 : 14),
          ),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.end, children: [
          Align(alignment: Alignment.centerLeft, child: Text(m.texto)),
          const SizedBox(height: 2),
          Text(
            '${hora(m.fecha)}${m.mio ? (m.leido ? ' · Leído' : ' · Enviado') : ''}',
            style: TextStyle(fontSize: 11, color: Colors.grey.shade600),
          ),
        ]),
      ),
    );
  }
}
