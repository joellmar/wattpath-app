import { Device } from "./device.interface";

export interface TelemetryState {
  devices: Device[];
  selectedMac: string | null;
  historicalReadings: {
    [mac: string]: {
      timestamps: number[],
      powerW: number[]
    }
  };
  isLoadingDevices: boolean;
}
