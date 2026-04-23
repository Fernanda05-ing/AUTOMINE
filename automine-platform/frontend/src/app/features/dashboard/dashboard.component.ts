import { Component } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatGridListModule } from '@angular/material/grid-list';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [MatCardModule, MatGridListModule],
  template: `
    <h1 class="page-title">Dashboard Gerencial</h1>
    <p style="opacity:.75;">KPIs operativos, financieros y de seguridad en tiempo real</p>

    <div style="display:grid;grid-template-columns:repeat(auto-fit,minmax(220px,1fr));gap:16px;margin-top:16px;">
      <mat-card><p>Produccion Hoy</p><div class="kpi-value">1,248 t</div></mat-card>
      <mat-card><p>Nomina Mensual</p><div class="kpi-value">$ 821M</div></mat-card>
      <mat-card><p>Accidentes Mes</p><div class="kpi-value">2</div></mat-card>
      <mat-card><p>Flujo Neto</p><div class="kpi-value">$ 312M</div></mat-card>
    </div>

    <mat-card style="margin-top:16px;padding:20px;">
      <h3 style="margin:0 0 10px 0;">IA Predictiva (placeholder productivo)</h3>
      <p style="margin:0;opacity:.8;">Riesgo de sobrecostos operativos en turno noche: 67%. Recomendacion: mantenimiento preventivo de maquinaria M-14.</p>
    </mat-card>
  `
})
export class DashboardComponent {}
