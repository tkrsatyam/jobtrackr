# Jobtrackr - Frontend

Angular 17+ frontend for JobTrackr. See the [root README](../../README.md) for full project overview, architecture, and setup.


## Stack

- Angular 17+ (standalone components)
- Angular Material (Azure/Blue theme)
- Angular CDK (drag-and-drop for Kanban board)
- SCSS


## Dev Setup

```bash
npm install
ng serve
```

Runs at `http://localhost:4200`. Expects the API Gateway running at `http://localhost:8080` — start the backend with `docker compose up -d` from the project root first.


## Structure

```
src/app/
├── core/          # Guards, interceptors, token storage
├── shared/        # Reusable components, pipes, models, constants
├── features/      # Auth, dashboard, applications, settings
└── layout/        # Shell, sidebar, topbar
```

## Key Notes

- Auth interceptor silently refreshes expired tokens — no manual token handling needed in services
- All identity headers (`X-User-Id` etc.) are injected by the Gateway — never send userId from the frontend
- Status transitions are enforced client-side via `status-transitions.ts` before hitting the API
- Tags are stored lowercase by the backend; displayed in title case via `TitleCaseTagPipe`


## Build

```bash
ng build --configuration production
```