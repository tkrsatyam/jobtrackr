# Frontend Review Guidelines (JobTrackr Angular Client)

Review any changes in `frontend/` (or `frontend/jobtrackr-fe/`) against these architectural and code quality rules:

## Technology Stack & Component Architecture
- **Framework**: Angular 19, zoneless change detection, standalone components.
- **Component File Structure**: Every component must strictly adhere to the 4-file structure:
  - `<name>.component.ts`
  - `<name>.component.html`
  - `<name>.component.scss`
  - `<name>.component.spec.ts`
  - **Strict: Never use inline templates (`template: '...'`) or inline styles (`styles: ['...']`)**.
- **Dev Server Port**: Default frontend dev server runs on port 4200.

## Reactivity & State Management
- Prefer Angular Signals (`signal`, `computed`, `input`, `output`) for component reactive state.
- Avoid unnecessary effects (`effect()`) for state derivations that should be computed signals (`computed()`).
- If RxJS Observables are used, prevent memory leaks. Ensure subscriptions are either handled via the `async` pipe or destroyed cleanly using `takeUntilDestroyed()`.
- Use modern Angular control flow syntax (`@if`, `@for`, `@switch`) in templates. Do not use legacy structural directives (`*ngIf`, `*ngFor`, `*ngSwitch`).
- Use `NonNullableFormBuilder` for reactive form controls.

## Design System & Styling Tokens
- Use Angular Material 3 design tokens (`--mat-sys-*`) and JobTrackr CSS custom properties instead of hardcoded hex colors or magic pixel values:
  - Status colors:
    - `--color-saved` (`#9e9e9e`)
    - `--color-applied` (`#1976d2`)
    - `--color-phone-screen` (`#ab47bc`)
    - `--color-interview` (`#7b1fa2`)
    - `--color-technical-round` (`#f57c00`)
    - `--color-hr-round` (`#fbc02d`)
    - `--color-offer` (`#388e3c`)
    - `--color-accepted` (`#1b5e20`)
    - `--color-rejected` (`#c62828`)
    - `--color-ghosted` (`#424242`)
    - `--color-withdrawn` (`#4e342e`)
  - Priority colors:
    - `--color-priority-low` (`#9e9e9e`)
    - `--color-priority-medium` (`#1976d2`)
    - `--color-priority-high` (`#f57c00`)
    - `--color-priority-dream` (`#ffd600`)
- Flag hardcoded colors or pixel-based spacing overrides that bypass design tokens.

## Iconography & UI Consistency
- Maintain icon style consistency using Angular Material Icons (`mat-icon`).
- Never use raw OS emojis for UI actions, buttons, or status indicators.

## System Invariants & Framework Guarantees (Do Not Flag)
- **Authentication & Interceptor Pipeline**: The Angular HTTP interceptor automatically attaches JWT bearer tokens and manages 401 refresh token rotation silently. Individual services must not duplicate token attachment or refresh logic for standard HTTP calls.
- **Signal Reactivity**: Angular Signals are synchronous and glitch-free. Do not flag defensive null checks when a signal has an explicit default value.
- **Service Lifecycles**: Services marked with `providedIn: 'root'` are singleton services that live for the full application lifecycle. They do not require `ngOnDestroy` unless they explicitly manage browser-level resources (e.g., WebSockets, event listeners).

## Responsive Parity
- Ensure layouts do not employ hardcoded fixed widths that break mobile viewports (target: Desktop `1440x900`, Mobile `390x844`).
