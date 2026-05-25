export interface PeriodRequest {
  id: number | null;
  name: string;
  priceKwh: number;
  startHour: string; // Formato "HH:mm:ss" mapeable a LocalTime en Spring Boot
  endHour: string; // Formato "HH:mm:ss" mapeable a LocalTime en Spring Boot
  dayType: string; // Valores: 'WEEKDAY', 'WEEKEND', etc. según el enum DayType
  startMonth: number;
  endMonth: number;

}

export interface TariffRequest {
  name: string;
  type: string;
  market: string;
  contractedPowerKw: number;
  energyCompany: string;
  periods: PeriodRequest[];
}
