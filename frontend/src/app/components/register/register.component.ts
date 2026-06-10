import { Component, effect, inject, signal } from "@angular/core";
import {
	type AbstractControl,
	FormBuilder,
	ReactiveFormsModule,
	type ValidationErrors,
	Validators,
} from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { Button } from "primeng/button";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { Password } from "primeng/password";
import type { RegisterRequest } from "../../interfaces/register-request.interface";
import { AuthService } from "../../services/auth.service";

// Validador de grupo: verifica que password y confirmPassword coincidan.
// Opera sobre el FormGroup completo, no sobre un control individual.
function passwordMatchValidator(
	control: AbstractControl,
): ValidationErrors | null {
	const password = control.get("password")?.value as string | undefined;
	const confirm = control.get("confirmPassword")?.value as string | undefined;
	if (!password || !confirm) return null;
	return password === confirm ? null : { passwordMismatch: true };
}

@Component({
	selector: "app-register",
	standalone: true,
	imports: [
		ReactiveFormsModule,
		RouterLink,
		InputText,
		Password,
		Button,
		Message,
	],
	templateUrl: "./register.html",
	styleUrl: "./register.css",
})
export default class RegisterComponent {
	private readonly authService = inject(AuthService);
	private readonly router = inject(Router);
	private readonly formBuilder = inject(FormBuilder).nonNullable;

	readonly errorMessage = signal<string | null>(null);
	readonly isLoading = signal<boolean>(false);

	private _errorTimer: number | null = null;
	private readonly _autoDismissError = effect(() => {
		if (this.errorMessage() !== null) {
			if (this._errorTimer !== null) clearTimeout(this._errorTimer);
			this._errorTimer = window.setTimeout(
				() => this.errorMessage.set(null),
				7000,
			);
		}
	});

	readonly registerForm = this.formBuilder.group(
		{
			username: ["", [Validators.required, Validators.email]],
			password: ["", [Validators.required, Validators.minLength(6)]],
			confirmPassword: ["", [Validators.required, Validators.minLength(6)]],
		},
		{ validators: passwordMatchValidator },
	);

	loginWithProvider(provider: "google" | "github"): void {
		window.location.href = `/oauth2/authorization/${provider}`;
	}

	onSubmit(): void {
		if (this.registerForm.invalid) {
			// Hace visible todos los errores de campo y de grupo antes de salir
			this.registerForm.markAllAsTouched();
			return;
		}

		this.isLoading.set(true);
		this.errorMessage.set(null);

		const payload: RegisterRequest = this.registerForm.getRawValue();

		this.authService.register(payload).subscribe({
			next: () => {
				this.isLoading.set(false);
				this.router.navigate(["/login"]);
			},
			error: (err) => {
				this.isLoading.set(false);
				this.errorMessage.set(
					err.error?.message ||
						"Error en el proceso de registro. Revisa los campos.",
				);
			},
		});
	}
}
