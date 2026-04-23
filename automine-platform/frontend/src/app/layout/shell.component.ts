import { Component, inject } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../core/services/auth.service';

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [RouterOutlet, RouterLink, MatSidenavModule, MatListModule, MatButtonModule],
  template: `
    <mat-sidenav-container style="height:100vh;">
      <mat-sidenav mode="side" opened style="width:280px;background:#0c1117;border-right:1px solid rgba(200,167,93,.25);padding:18px;">
        <h2 style="font-family:'Sora',sans-serif;margin-top:10px;color:#c8a75d;">AUTOMINE</h2>
        <mat-nav-list>
          <a mat-list-item routerLink="/dashboard">Dashboard</a>
          <a mat-list-item routerLink="/employees">Empleados</a>
        </mat-nav-list>
        <button mat-stroked-button style="margin-top:20px;" (click)="logout()">Cerrar sesion</button>
      </mat-sidenav>
      <mat-sidenav-content style="padding:20px;">
        <router-outlet></router-outlet>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `
})
export class ShellComponent {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
