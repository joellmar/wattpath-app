import { Routes } from '@angular/router';
import { authGuard } from './guards/auth.guard';

export const routes: Routes = [
  {
    path: "login",
    loadComponent: () => import("./components/login/login.component")
  },
  {
    path: "register",
    loadComponent: () => import("./components/register/register.component")
  },
  {
    path: "tariffs",
    loadComponent: () => import("./components/tariff/tariff.component"),
    canActivate: [authGuard]
  },
  {
    path: "devices",
    loadComponent: () => import("./components/devices/devices.component"),
    canActivate: [authGuard]
  },
  {
    path: "alerts",
    loadComponent: () => import("./components/alerts/alerts.component"),
    canActivate: [authGuard]
  },
  {
    path: "dashboard",
    loadComponent: () => import("./components/dashboard/dashboard.component"),
    canActivate: [authGuard]
  },
  {
    path: "",
    redirectTo: "dashboard",
    pathMatch: "full"
  },
  {
    path: "**",
    redirectTo: "dashboard"
  }
];
