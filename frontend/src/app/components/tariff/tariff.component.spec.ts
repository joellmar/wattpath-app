import { provideHttpClient } from "@angular/common/http";
import { provideHttpClientTesting } from "@angular/common/http/testing";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { ReactiveFormsModule } from "@angular/forms";
import { SessionStorageService } from "../../services/session-storage.service";
import TariffComponent from "./tariff.component";

// Mock del SessionStorageService para controlar el rol en tests
class MockSessionStorage {
	private role: "ROLE_ADMIN" | "ROLE_USER" = "ROLE_USER";

	setRole(role: "ROLE_ADMIN" | "ROLE_USER"): void {
		this.role = role;
	}

	hasRole(r: "ROLE_ADMIN" | "ROLE_USER"): boolean {
		return this.role === r;
	}

	getAuthorities(): string[] {
		return [this.role];
	}

	getUsername(): string {
		return "test@ejemplo.com";
	}

	getToken(): string | null {
		return "fake-token";
	}

	isLoggedIn(): boolean {
		return true;
	}
}

describe("TariffComponent — formulario dinámico por peaje de acceso", () => {
	let component: TariffComponent;
	const mockSession = new MockSessionStorage();

	async function setupComponent(role: "ROLE_ADMIN" | "ROLE_USER") {
		mockSession.setRole(role);

		await TestBed.configureTestingModule({
			imports: [TariffComponent, ReactiveFormsModule],
			providers: [
				provideHttpClient(),
				provideHttpClientTesting(),
				{ provide: SessionStorageService, useValue: mockSession },
			],
			schemas: [NO_ERRORS_SCHEMA],
		}).compileComponents();

		const fixture = TestBed.createComponent(TariffComponent);
		component = fixture.componentInstance;
		fixture.detectChanges();
	}

	describe("con tarifa 2.0TD", () => {
		beforeEach(async () => {
			await setupComponent("ROLE_USER");
			// El constructor ya llama rebuildPeriodArrays("2.0TD") por defecto
		});

		it("debe crear exactamente 3 periodos de energía", () => {
			expect(component.periods.length).toBe(3);
		});

		it("debe crear exactamente 2 potencias contratadas", () => {
			expect(component.contractedPowers.length).toBe(2);
		});

		it("los códigos de periodo de energía son P1, P2, P3", () => {
			const codes = component.periods.controls.map(
				(g) => g.get("periodCode")?.value,
			);
			expect(codes).toEqual(["P1", "P2", "P3"]);
		});

		it("los códigos de potencia contratada son P1, P2", () => {
			const codes = component.contractedPowers.controls.map(
				(g) => g.get("periodCode")?.value,
			);
			expect(codes).toEqual(["P1", "P2"]);
		});
	});

	describe("al cambiar a 3.0TD", () => {
		beforeEach(async () => {
			await setupComponent("ROLE_USER");
			component.rebuildPeriodArrays("3.0TD");
		});

		it("debe crear exactamente 6 periodos de energía", () => {
			expect(component.periods.length).toBe(6);
		});

		it("debe crear exactamente 6 potencias contratadas", () => {
			expect(component.contractedPowers.length).toBe(6);
		});

		it("los códigos de energía son P1 a P6", () => {
			const codes = component.periods.controls.map(
				(g) => g.get("periodCode")?.value,
			);
			expect(codes).toEqual(["P1", "P2", "P3", "P4", "P5", "P6"]);
		});
	});

	describe("al cambiar a 6.1TD", () => {
		beforeEach(async () => {
			await setupComponent("ROLE_USER");
			component.rebuildPeriodArrays("6.1TD");
		});

		it("debe crear exactamente 6 periodos de energía", () => {
			expect(component.periods.length).toBe(6);
		});

		it("debe crear exactamente 6 potencias contratadas", () => {
			expect(component.contractedPowers.length).toBe(6);
		});
	});

	describe("según el rol", () => {
		it("isAdmin() es false para ROLE_USER", async () => {
			await setupComponent("ROLE_USER");
			expect(component.isAdmin()).toBe(false);
		});

		it("isAdmin() es true para ROLE_ADMIN", async () => {
			await setupComponent("ROLE_ADMIN");
			expect(component.isAdmin()).toBe(true);
		});
	});
});
