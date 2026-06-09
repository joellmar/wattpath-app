import { TestBed } from "@angular/core/testing";
import { SessionStorageService } from "./session-storage.service";

// Token JWT mínimo válido (header.payload.signature) con authorities CSV
// El payload decodificado es: { username: "usuario@test.com", authorities: "ROLE_USER,ROLE_ADMIN", exp: 9999999999 }
const JWT_DUAL_ROLE =
	"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
	"eyJ1c2VybmFtZSI6InVzdWFyaW9AdGVzdC5jb20iLCJhdXRob3JpdGllcyI6IlJPTEVfVVNFUixST0xFX0FETUlOIiwiZXhwIjo5OTk5OTk5OTk5fQ." +
	"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

const JWT_USER_ONLY =
	"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9." +
	"eyJ1c2VybmFtZSI6InVzdWFyaW9AdGVzdC5jb20iLCJhdXRob3JpdGllcyI6IlJPTEVfVVNFUiIsImV4cCI6OTk5OTk5OTk5OX0." +
	"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";

describe("SessionStorageService", () => {
	let service: SessionStorageService;

	beforeEach(() => {
		TestBed.configureTestingModule({});
		service = TestBed.inject(SessionStorageService);
		sessionStorage.clear();
	});

	afterEach(() => {
		sessionStorage.clear();
	});

	it("debe instanciarse correctamente", () => {
		expect(service).toBeTruthy();
	});

	it("getAuthorities() devuelve array vacío cuando no hay token", () => {
		const roles = service.getAuthorities();
		expect(roles).toEqual([]);
	});

	it("getAuthorities() parsea el CSV de roles correctamente", () => {
		service.saveToken(JWT_DUAL_ROLE);
		const roles = service.getAuthorities();
		expect(roles).toContain("ROLE_USER");
		expect(roles).toContain("ROLE_ADMIN");
		expect(roles.length).toBe(2);
	});

	it("hasRole('ROLE_ADMIN') devuelve true cuando el token contiene ambos roles", () => {
		service.saveToken(JWT_DUAL_ROLE);
		expect(service.hasRole("ROLE_ADMIN")).toBe(true);
	});

	it("hasRole('ROLE_ADMIN') devuelve false cuando el token solo tiene ROLE_USER", () => {
		service.saveToken(JWT_USER_ONLY);
		expect(service.hasRole("ROLE_ADMIN")).toBe(false);
	});

	it("hasRole('ROLE_USER') devuelve true cuando el token tiene ROLE_USER", () => {
		service.saveToken(JWT_USER_ONLY);
		expect(service.hasRole("ROLE_USER")).toBe(true);
	});

	it("hasRole() devuelve false cuando no hay token en sesión", () => {
		expect(service.hasRole("ROLE_USER")).toBe(false);
	});

	it("getUsername() extrae el campo username del JWT", () => {
		service.saveToken(JWT_DUAL_ROLE);
		expect(service.getUsername()).toBe("usuario@test.com");
	});

	it("getUsername() devuelve null si no hay token", () => {
		expect(service.getUsername()).toBeNull();
	});
});
