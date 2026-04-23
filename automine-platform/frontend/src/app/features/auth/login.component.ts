import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatButtonModule } from '@angular/material/button';
import { AuthService } from '../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatCardModule, MatFormFieldModule, MatInputModule, MatButtonModule],
  template: `
    <section style="min-height:100vh;display:flex;align-items:center;justify-content:center;padding:24px;">
      <mat-card style="width:100%;max-width:420px;padding:24px;">
        <h1 class="page-title">AUTOMINE</h1>
        <p style="opacity:.8;margin-top:6px;">Control empresarial minero</p>

        <form [formGroup]="form" (ngSubmit)="submit()" style="display:grid;gap:16px;margin-top:20px;">
          <mat-form-field appearance="outline">
            <mat-label>Usuario</mat-label>
            <input matInput formControlName="username" />
          </mat-form-field>
          <mat-form-field appearance="outline">
            <mat-label>Contraseña</mat-label>
            <input matInput type="password" formControlName="password" />
          </mat-form-field>
          <button mat-raised-button color="primary" type="submit">Ingresar</button>
        </form>
      </mat-card>
    </section>
  `
})
export class LoginComponent {
  private readonly fb = inject(FormBuilder);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  form = this.fb.group({
    username: ['', Validators.required],
    password: ['', Validators.required]
  });

  submit(): void {
    if (this.form.invalid) return;
    this.auth.login(this.form.getRawValue() as { username: string; password: string }).subscribe({
      next: () => this.router.navigate(['/dashboard'])
    });
  }
}
