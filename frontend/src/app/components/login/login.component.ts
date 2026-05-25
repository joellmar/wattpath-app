import { Component, inject, signal } from '@angular/core';
import { AuthService } from '../../services/auth.service';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { LoginUser } from '../../interfaces/login-user.interface';
import { SessionStorageService } from '../../services/session-storage.service';
import { Password } from 'primeng/password';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    CommonModule,
    RouterLink,
    InputText,
    Password,
    Button,
    Message
  ],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export default class LoginComponent {
  private readonly authService = inject(AuthService);
  private readonly sessionStorageService = inject(SessionStorageService);
  private readonly router = inject(Router);
  private readonly formBuilder = inject(FormBuilder).nonNullable;

  // Señales de control de interfaz de usuario
  readonly isLoading = signal<boolean>(false);
  readonly loginError = signal<string | null>(null);

  // Formulario reactivo
  readonly loginForm = this.formBuilder.group({
    username: ["", [Validators.required, Validators.email]], // Obligamos formato email segun diseño tecnico
    password: ["", [Validators.required, Validators.minLength(4)]]
  });

  onSubmit() {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.loginError.set(null);

    const credentials: LoginUser = this.loginForm.getRawValue();

    this.authService.authentication(credentials).subscribe({
      next: response => {
        this.sessionStorageService.saveToken(response.jwt);
        // Introducemos un micro-retardo macro-tarea para garantizar que el almacenamiento se asiente
        // antes de disparar la navegación y las subsiguientes peticiones del Guard/Dashboard
        setTimeout(() => {
          this.isLoading.set(false);
          this.router.navigate(["/dashboard"]);
        }, 50);
      },
      error: err => {
        this.isLoading.set(false);
        this.loginError.set(err.error?.message || "Las credenciales introducidas no son válidas o el servidor no responde.");
      },
    });
  }
}
