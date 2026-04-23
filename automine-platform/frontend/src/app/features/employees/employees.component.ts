import { Component } from '@angular/core';
import { MatTableModule } from '@angular/material/table';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-employees',
  standalone: true,
  imports: [MatTableModule, MatCardModule],
  template: `
    <h1 class="page-title">Gestion de Empleados</h1>
    <mat-card style="margin-top:16px;padding:16px;">
      <table mat-table [dataSource]="rows" style="width:100%;">
        <ng-container matColumnDef="code">
          <th mat-header-cell *matHeaderCellDef>Codigo</th>
          <td mat-cell *matCellDef="let r">{{ r.code }}</td>
        </ng-container>
        <ng-container matColumnDef="name">
          <th mat-header-cell *matHeaderCellDef>Nombre</th>
          <td mat-cell *matCellDef="let r">{{ r.name }}</td>
        </ng-container>
        <ng-container matColumnDef="position">
          <th mat-header-cell *matHeaderCellDef>Cargo</th>
          <td mat-cell *matCellDef="let r">{{ r.position }}</td>
        </ng-container>

        <tr mat-header-row *matHeaderRowDef="displayedColumns"></tr>
        <tr mat-row *matRowDef="let row; columns: displayedColumns"></tr>
      </table>
    </mat-card>
  `
})
export class EmployeesComponent {
  displayedColumns = ['code', 'name', 'position'];
  rows = [
    { code: 'EMP-001', name: 'Luis Moreno', position: 'Supervisor Mina' },
    { code: 'EMP-002', name: 'Ana Rojas', position: 'Analista Nomina' }
  ];
}
