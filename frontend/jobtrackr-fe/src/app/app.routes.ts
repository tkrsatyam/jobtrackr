import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth-guard';

// TODO add routing along with creating components
export const routes: Routes = [
    { path: '', redirectTo: 'dashboard', pathMatch: 'full' },
    {
        path: 'login',
        loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent)
    },
    {
        path: 'register',
        loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent)
    },
    {
        path: '',
        loadComponent: () => import('./layout/shell/shell.component').then(m => m.ShellComponent),
        canActivate: [authGuard],
        children: [
            {
                path: 'dashboard',
                loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent)
            },
            {
                path: 'applications',
                loadComponent: () => import('./features/applications/application-list/application-list.component').then(m => m.ApplicationListComponent)
            }
        ]
    },
    { path: '**', redirectTo: 'dashboard' }
];
