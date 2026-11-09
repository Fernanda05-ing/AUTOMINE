import 'package:flutter/material.dart';

import '../api.dart';
import '../ui.dart';

/// HU006: el trabajador consulta sus pagos, consignaciones y descarga el desprendible.
class PagosScreen extends StatelessWidget {
  final int empleadoId;
  const PagosScreen({super.key, required this.empleadoId});

  Future<(List, List)> _cargar() async {
    final r = await Future.wait([
      Api.instance.get('/Payroll/$empleadoId'),
      Api.instance.get('/Transfers/$empleadoId'),
    ]);
    return (r[0] as List, r[1] as List);
  }

  @override
  Widget build(BuildContext context) => Cargador<(List, List)>(
        cargar: _cargar,
        builder: (context, data, _) {
          final (nominas, consignaciones) = data;
          if (nominas.isEmpty) return const Vacio('Aún no tiene nóminas liquidadas.');
          return ListView(padding: const EdgeInsets.all(12), children: [
            for (final n in nominas) _NominaCard(n, consignaciones.where((c) => c['nominaId'] == n['id']).toList()),
          ]);
        },
      );
}

class _NominaCard extends StatelessWidget {
  final Map n;
  final List consignaciones;
  const _NominaCard(this.n, this.consignaciones);

  @override
  Widget build(BuildContext context) => Card(
        child: ExpansionTile(
          title: Text('${meses[n['mes'] - 1]} ${n['anio']}', style: const TextStyle(fontWeight: FontWeight.w600)),
          subtitle: Text('Neto ${dinero(n['netoPagar'])}'),
          trailing: Estado(n['estado']),
          childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
          children: [
            _fila('Salario devengado', n['salarioDevengado']),
            _fila('Horas extra', n['valorHorasExtra']),
            _fila('Bonificaciones', n['bonificaciones']),
            _fila('Auxilio de transporte', n['auxilioTransporte']),
            _fila('Total devengado', n['totalDevengado'], bold: true),
            _fila('Salud', -(n['salud'] as num)),
            _fila('Pensión', -(n['pension'] as num)),
            if ((n['fondoSolidaridad'] as num) > 0) _fila('Fondo de solidaridad', -(n['fondoSolidaridad'] as num)),
            if ((n['otrosDescuentos'] as num) > 0) _fila('Otros descuentos', -(n['otrosDescuentos'] as num)),
            _fila('Neto a pagar', n['netoPagar'], bold: true),
            const Divider(),
            if (consignaciones.isEmpty)
              const Align(alignment: Alignment.centerLeft, child: Text('Sin consignaciones registradas'))
            else
              for (final c in consignaciones)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: const Icon(Icons.account_balance),
                  title: Text('${c['banco']} ${c['cuenta']}'),
                  subtitle: Text('${fecha(c['fechaPago'])} · Ref ${c['referencia']}'),
                  trailing: Column(mainAxisAlignment: MainAxisAlignment.center, crossAxisAlignment: CrossAxisAlignment.end, children: [
                    Text(dinero(c['valor'])),
                    Estado(c['estado']),
                  ]),
                ),
            const SizedBox(height: 8),
            OutlinedButton.icon(
              icon: const Icon(Icons.picture_as_pdf_outlined),
              label: const Text('Descargar desprendible'),
              onPressed: () => Api.instance
                  .abrirPdf('/Payroll/Detail/${n['id']}/Payslip', 'desprendible-${n['anio']}-${n['mes']}.pdf')
                  .catchError((e) { if (context.mounted) avisar(context, '$e', error: true); }),
            ),
          ],
        ),
      );

  Widget _fila(String label, num v, {bool bold = false}) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(label, style: TextStyle(fontWeight: bold ? FontWeight.w700 : null))),
          Text(dinero(v), style: TextStyle(fontWeight: bold ? FontWeight.w700 : null)),
        ]),
      );
}

/// HU007: el trabajador descarga su certificado laboral.
class CertificadosScreen extends StatefulWidget {
  final int empleadoId;
  const CertificadosScreen({super.key, required this.empleadoId});

  @override
  State<CertificadosScreen> createState() => _CertificadosScreenState();
}

class _CertificadosScreenState extends State<CertificadosScreen> {
  Key _key = UniqueKey();
  bool _generando = false;

  Future<void> _nuevo() async {
    final dirigido = TextEditingController();
    bool salario = true;
    final ok = await showDialog<bool>(
      context: context,
      builder: (c) => StatefulBuilder(
        builder: (c, set) => AlertDialog(
          title: const Text('Nuevo certificado laboral'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: dirigido, decoration: const InputDecoration(labelText: 'Dirigido a (opcional)', hintText: 'A QUIEN INTERESE')),
            CheckboxListTile(
              contentPadding: EdgeInsets.zero,
              value: salario,
              onChanged: (v) => set(() => salario = v ?? true),
              title: const Text('Incluir salario'),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(c, false), child: const Text('Cancelar')),
            FilledButton(onPressed: () => Navigator.pop(c, true), child: const Text('Generar')),
          ],
        ),
      ),
    );
    if (ok != true) return;
    setState(() => _generando = true);
    try {
      final cert = await Api.instance.post('/Certificate',
          {'empleadoId': widget.empleadoId, 'dirigidoA': dirigido.text, 'incluyeSalario': salario});
      await Api.instance.abrirPdf('/Certificate/Detail/${cert['id']}/Pdf', 'certificado-${cert['codigoValidacion']}.pdf');
      setState(() => _key = UniqueKey());
    } catch (e) {
      if (mounted) avisar(context, '$e', error: true);
    } finally {
      if (mounted) setState(() => _generando = false);
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        floatingActionButton: FloatingActionButton.extended(
          onPressed: _generando ? null : _nuevo,
          icon: _generando
              ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
              : const Icon(Icons.add),
          label: const Text('Solicitar certificado'),
        ),
        body: Cargador<List>(
          key: _key,
          cargar: () async => await Api.instance.get('/Certificates') as List,
          builder: (context, lista, _) => lista.isEmpty
              ? const Vacio('Aún no ha solicitado certificados. Use el botón para generar uno.')
              : ListView(padding: const EdgeInsets.fromLTRB(12, 12, 12, 90), children: [
                  for (final c in lista)
                    Card(
                      child: ListTile(
                        leading: const Icon(Icons.workspace_premium, color: colorDorado),
                        title: Text(c['dirigidoA']),
                        subtitle: Text('${fecha(c['fechaEmision'])} · Código ${c['codigoValidacion']}'),
                        trailing: c['estado'] == 'VIGENTE'
                            ? IconButton(
                                icon: const Icon(Icons.picture_as_pdf_outlined),
                                onPressed: () => Api.instance
                                    .abrirPdf('/Certificate/Detail/${c['id']}/Pdf', 'certificado-${c['codigoValidacion']}.pdf')
                                    .catchError((e) { if (context.mounted) avisar(context, '$e', error: true); }),
                              )
                            : Estado(c['estado']),
                      ),
                    ),
                ]),
        ),
      );
}

/// Turnos asignados al trabajador.
class TurnosScreen extends StatelessWidget {
  final int empleadoId;
  const TurnosScreen({super.key, required this.empleadoId});

  @override
  Widget build(BuildContext context) => Cargador<List>(
        cargar: () async => await Api.instance.get('/Shifts/$empleadoId') as List,
        builder: (context, lista, _) => lista.isEmpty
            ? const Vacio('No tiene turnos asignados en los últimos 60 días.')
            : ListView.separated(
                padding: const EdgeInsets.all(12),
                itemCount: lista.length,
                separatorBuilder: (_, _) => const SizedBox(height: 4),
                itemBuilder: (_, i) {
                  final t = lista[i];
                  return Card(
                    child: ListTile(
                      leading: CircleAvatar(
                        backgroundColor: colorDorado.withValues(alpha: 0.25),
                        child: Icon(t['turno'] == 'NOCHE' ? Icons.nightlight_round : Icons.wb_sunny_outlined, color: colorCarbon),
                      ),
                      title: Text('${fecha(t['fecha'])} · ${etiqueta(t['turno'])}'),
                      subtitle: Text('${t['frente']} · ${t['labor']}'),
                      trailing: Text('${numero(t['toneladasTurno'])} t'),
                    ),
                  );
                },
              ),
      );
}
