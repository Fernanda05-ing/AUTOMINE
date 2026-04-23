import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { tap } from 'rxjs/operators';

interface LoginRequest {
  username: string;
  password: string;
}

interface LoginResponse {
  accessToken: string;
  tokenType: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly apiUrl = 'http://localhost:8080/api';

  login(payload: LoginRequest) {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`, payload).pipe(
      tap((res) => localStorage.setItem('automine_token', `${res.tokenType} ${res.accessToken}`))
    );
  }

  logout(): void {
    localStorage.removeItem('automine_token');
  }

  isLoggedIn(): boolean {
    return !!localStorage.getItem('automine_token');
  }
}
