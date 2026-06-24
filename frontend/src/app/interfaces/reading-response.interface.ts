export interface ReadingResponse {
	time: string | number;
	macAddress: string;
	powerW: number;
	energyTotalKwh: number;
	isOn: boolean;
}
