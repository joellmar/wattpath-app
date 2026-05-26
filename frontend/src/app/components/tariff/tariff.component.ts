import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import {
  FormArray,
  FormBuilder,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { Button } from 'primeng/button';
import { InputText } from 'primeng/inputtext';
import { InputNumber } from 'primeng/inputnumber';
import { Message } from 'primeng/message';
import { Select } from 'primeng/select';
import { TariffService } from '../../services/tariff.service';
import { Router } from '@angular/router';
import { TariffRequest } from '../../interfaces/tariff-request.interface';

interface PeriodFormGroup {
  id: FormControl<number | null>;
  name: FormControl<string>;
  priceKwh: FormControl<number>;
  startHour: FormControl<string>;
  endHour: FormControl<string>;
  dayType: FormControl<string>;
  startMonth: FormControl<number>;
  endMonth: FormControl<number>;
}

@Component({
  selector: 'app-tariff',
  standalone: true,
  imports: [ReactiveFormsModule, CommonModule, InputText, InputNumber, Select, Button, Message],
  templateUrl: './tariff.html',
  styleUrl: './tariff.css',
})
export default class TariffComponent {
  private readonly formBuilder = inject(FormBuilder).nonNullable;
  private readonly tariffService = inject(TariffService);
  private readonly router = inject(Router);

  readonly isLoading = signal<boolean>(false);
  readonly errorMessage = signal<string | null>(null);

  readonly marketOptions = [
    { label: 'Mercado libre', value: 'LIBRE' },
    { label: 'Mercado regulado (PVPC)', value: 'REGULADO' },
  ];

  readonly typeOptions = [
    { label: 'Tarifa simple pyme (2.0TD)', value: '2.0TD' },
    { label: 'Tarifa industrial maxímetro (3.0TD)', value: '3.0TD' },
  ];

  readonly tariffForm = this.formBuilder.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    type: ['2.0TD', [Validators.required]],
    market: ['LIBRE', [Validators.required]],
    contractedPowerKw: [15.0, [Validators.required, Validators.min(0.1)]],
    energyCompany: ['', [Validators.required]],
    periods: this.formBuilder.array<FormGroup<PeriodFormGroup>>([
      this.createPeriodFormGroup('P1 (Punta)', 0.1845, '10:00:00', '14:00:00', 'WEEKDAY', 1, 12),
      this.createPeriodFormGroup('P2 (Llano)', 0.1212, '08:00:00', '10:00:00', 'WEEKDAY', 1, 12),
      this.createPeriodFormGroup('P3 (Valle)', 0.0834, '00:00:00', '08:00:00', 'WEEKDAY', 1, 12),
    ]),
  });

  get periods(): FormArray<FormGroup<PeriodFormGroup>> {
    return this.tariffForm.get('periods') as FormArray<FormGroup<PeriodFormGroup>>;
  }

  private createPeriodFormGroup(
    name: string,
    price: number,
    start: string,
    end: string,
    dayType: string,
    startMonth: number,
    endMonth: number,
  ): FormGroup<PeriodFormGroup> {
    return this.formBuilder.group<PeriodFormGroup>({
      id: this.formBuilder.control<number | null>(null),
      name: this.formBuilder.control(name, [Validators.required]),
      priceKwh: this.formBuilder.control(price, [Validators.required, Validators.min(0.0001)]),
      startHour: this.formBuilder.control(start, [Validators.required]),
      endHour: this.formBuilder.control(end, [Validators.required]),
      dayType: this.formBuilder.control(dayType, [Validators.required]),
      startMonth: this.formBuilder.control(startMonth, [Validators.required, Validators.min(1), Validators.max(12)]),
      endMonth: this.formBuilder.control(endMonth, [Validators.required, Validators.min(1), Validators.max(12)])
    });
  }

  onSubmit(): void {
    if (this.tariffForm.invalid) {
      this.tariffForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    const payload: TariffRequest = this.tariffForm.getRawValue();

    this.tariffService.createTariff(payload).subscribe({
      next: () => {
        this.isLoading.set(false);
        this.router.navigate(['/dashboard']);
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(
          err.error?.message || 'Error al guardar la configuración de la tarifa eléctrica.',
        );
      },
    });
  }
}
