# Changelog

Anjana Admin follows [semantic versioning](https://semver.org). The same list is shown in the app at `/changelog`.

## 1.0.0 — 2026-10-28 · First release

- **New:** `anjana-admin-demo.html`, the whole front end in one file that opens from disk with no install or API; `npm run demo` and `npm run build:demo` for sample-data runs.
- **New:** hosted demo setup for Netlify (`netlify.toml`) and Vercel (`vercel.json`).
- **Improved:** text and status colours meet WCAG AA contrast (4.5:1) in light and dark mode; heading levels fixed on the dashboard, roles and settings pages; full-screen error pages are a `main` landmark.
- **Improved:** code examples in the UI Kit are syntax-highlighted with CodeBlock.
- **Changed:** the API is now `atelier-api` (Maven `com.atelier:atelier-api:1.0.0`, package `com.atelier.api`, main class `AtelierApiApplication`).

## 0.5.0 — 2026-10-28 · Apps and motion

- Kanban, Calendar, Chat, Mail, Files and Invoices app pages.
- KanbanBoard, Scheduler, RichTextEditor, CodeBlock, NotificationCenter, ChatPanel, MentionInput, Lightbox and Select (87 components).
- `/ui/motion` gallery with all 16 animations; circular theme reveal; swipeable toasts; form errors shake; checkbox ticks draw.

## 0.4.0 — 2026-10-27 · Dashboards, charts and blocks

- Analytics, Sales and Projects dashboards; charts kit; 30 blocks; TreeView and Timeline.
- DataTable: pinned and resizable columns, expandable rows, server mode, virtual rows.
- VitePress docs site.

## 0.3.0 — 2026-10-27 · Accounts and security

- Sign-up with email confirmation, password reset, two-step sign-in with recovery codes, lock screen.
- Settings, signed-in devices, notifications and My activity.
- Forms, date and time pickers, command palette and 16 more components.

## 0.2.0 — 2026-10-26 · The UI Kit

- 52 components with live docs, playgrounds and dark, right-to-left and phone previews.
- Theme customizer with presets, brand colour and CSS export.

## 0.1.0 — 2026-10-25 · First preview

- Users, roles, permissions, dashboard and audit log on React and Spring Boot.
