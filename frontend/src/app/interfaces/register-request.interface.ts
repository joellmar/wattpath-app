export interface RegisterRequest {
	username: string;
	password: string;
	tariffId?: number; // Opcional para el MVP si eligen tarifa luego
}
