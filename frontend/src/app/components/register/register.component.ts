import { CommonModule } from "@angular/common";
import { Component, inject, signal } from "@angular/core";
import { FormBuilder, ReactiveFormsModule, Validators } from "@angular/forms";
import { Router, RouterLink } from "@angular/router";
import { Button } from "primeng/button";
import { InputText } from "primeng/inputtext";
import { Message } from "primeng/message";
import { Password } from "primeng/password";
import type { RegisterRequest } from "../../interfaces/register-request.interface";
import { AuthService } from "../../services/auth.service";

@Component({
	selector: "app-register",
	standalone: true,
	imports: [
		ReactiveFormsModule,
		CommonModule,
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

	readonly registerForm = this.formBuilder.group({
		username: ["", [Validators.required, Validators.email]],
		password: ["", [Validators.required, Validators.minLength(6)]],
	});

	onSubmit(): void {
		if (this.registerForm.invalid) {
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
