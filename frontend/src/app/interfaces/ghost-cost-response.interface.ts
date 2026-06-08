export interface GhostCostResponse {
	macAddress: string;
	ghostCostEur: number; // El BigDecimal de Java se convierte en number en JSON
	start: string; // El Instant de Java se serializa como un string en formato ISO-8601
	end: string; // Ejemplo: "2026-05-30T19:25:00Z"
}
