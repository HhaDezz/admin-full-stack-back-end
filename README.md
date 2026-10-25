# Anjana Admin API

A production-ready Spring Boot back end for **sign-in, users, roles, permissions, a dashboard and a tamper-proof audit log**.
Pairs with the Anjana Admin web app (`atelier-admin`), or use it on its own as the foundation of your product.

**Stack:** Java 17 · Spring Boot 4 · Spring Security · PostgreSQL 14+ · Flyway · JWT. No Redis or other services needed.

## Features

**Authentication**
- Email + password sign-in returning a signed JWT (HS256), configurable lifetime
- **Two-step sign-in** with any authenticator app (TOTP, RFC 6238) plus 10 single-use recovery codes; the secret is encrypted at rest (AES-256-GCM) and a code can't be used twice
- **Self sign-up** (can be turned off) with **email confirmation**, and **forgot / reset password** by emailed link. Links are single-use, expire (24 h / 1 h) and only their hash is stored
- **Devices**: every sign-in is a session with browser, OS, IP and last activity; sign out one device or all others. Real sign-out revokes the session
- Changing or resetting a password signs out every other device; disabling a user ends their sessions immediately
- Rate limits on sign-up, reset and confirmation emails; "if an account exists" answers so accounts can't be discovered
- Brute-force protection: too many failures for an email from one IP locks it for a while (HTTP 429)
- Identical answer for unknown email and wrong password, with equal timing, so accounts can't be discovered
- BCrypt (cost 12) password hashing; emails are case-insensitive

**Users** – list, view, create, edit, enable/disable, delete. Roles assigned per user. Last sign-in tracked.
You can't disable or delete yourself; the built-in administrator can't be changed by others.

**Roles & permissions** – create, rename, delete roles; assign permissions. A built-in `SUPER_ADMIN` role always
holds every permission (including ones you add later) and can't be edited or assigned by non-super-admins.
Roles and permissions are re-read on every request, so changes apply instantly.

**My account** – profile, password, security overview, two-step sign-in, devices, notification preferences
and "My activity" (the user's own sign-ins and changes for 90 days), for every signed-in user.

**Notifications** – in-app and by email, per category (security, account, product) and channel, as each user
chooses. Sent automatically for new sign-ins from a new browser, password and two-step changes, used recovery
codes and role changes; call `NotificationService.notify(...)` for your own events.

**Email** – SMTP via `spring.mail.*`. Without a mail server configured, emails (links included) are written to
the log, so every flow works in development.

**Dashboard** – user counts, sign-ins and failures per day (14 days), users per role, newest users, recent activity.

**Audit log** – recorded automatically with no code in your controllers:

| Type | What is recorded |
|---|---|
| `AUTH` | Every sign-in, failed sign-in (with reason), lock-out and sign-out |
| `REQUEST` | Every API call that changes something: user, method, path, status, duration, IP, browser |
| `DATA` | Every database row created, updated or deleted, with **before and after values** (secrets masked) |
| `BUSINESS` | Named events you write yourself, e.g. "Changed own password" |

Rows from the same API call share a request id. The log lives in its own `audit` schema, partitioned by month;
a database trigger makes it append-only, and months older than `AUDIT_RETENTION_MONTHS` are dropped automatically.
Only super admins can read it.

**Also:** consistent `{ success, message, data }` responses, field-level validation errors, no stack traces leaked,
CORS allow-list, `/health` endpoint, Dockerfile, docker-compose, and an integration test suite.

## Quick start

You need **Java 17+** and **PostgreSQL 14+**.

```bash
createuser -P atelier            # password: atelier (or use your own login)
createdb -O atelier atelier

cp src/main/resources/application.properties.example src/main/resources/application.properties
#   edit the database login if needed, and set app.jwt.secret (or export JWT_SECRET)
export JWT_SECRET=$(openssl rand -base64 48)

./mvnw spring-boot:run           # http://localhost:8089
```

`application.properties` is git-ignored so your passwords never get committed; `application.properties.example` is the template.

On first start the database tables are created and a super admin is added:
**admin@example.com / ChangeMe123!** – sign in and change the password, or set `ADMIN_EMAIL` / `ADMIN_PASSWORD` before the first start.

Set `DEMO_USERS=true` to add four sample users (password `Password123!`).

### With Docker

```bash
cp .env.example .env       # set JWT_SECRET and ADMIN_PASSWORD; WEB_URL and MAIL_* for email
docker compose up -d --build
```
Web app: http://localhost:8081 · API: http://localhost:8089 · expects the web app in `../atelier-admin`.

## Configuration

Set in `src/main/resources/application.properties`, or as environment variables.

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | *(required)* | Token signing key, at least 32 characters |
| `JWT_EXPIRATION` | `8h` | Token lifetime (`30m`, `8h`, `7d`…) |
| `DB_URL` | `jdbc:postgresql://localhost:5432/atelier` | Database |
| `DB_USERNAME` / `DB_PASSWORD` | `atelier` / `atelier` | Database login |
| `PORT` | `8089` | HTTP port |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Comma-separated web origins allowed to call the API |
| `ADMIN_NAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `Administrator` / `admin@example.com` / `ChangeMe123!` | First super admin (first start only) |
| `DEMO_USERS` | `false` | Add sample users |
| `MAX_LOGIN_ATTEMPTS` / `LOGIN_LOCKOUT` | `5` / `15m` | Brute-force protection |
| `AUDIT_RETENTION_MONTHS` | `3` | Months of audit log to keep |
| `AUDIT_DATA_CHANGES` | `true` | Record row-level before/after changes |
| `BRAND_NAME` | `Anjana` | Product name in emails and authenticator apps |
| `WEB_URL` | `http://localhost:5173` | Where the web app runs; used for links in emails |
| `REGISTRATION_ENABLED` | `true` *(example file)* | Allow self sign-up (`false` if unset) |
| `REGISTRATION_REQUIRE_VERIFICATION` | `true` | New accounts must confirm their email before signing in |
| `REGISTRATION_DEFAULT_ROLE` | `USER` | Role given to new sign-ups |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` | – / `587` | SMTP server; empty host = log emails instead |
| `MAIL_FROM` | `no-reply@example.com` | Sender address |

## API

All responses: `{ "success": true, "message": "…", "data": … }`. Errors use the same shape with `success: false`;
validation errors put `{ field: message }` in `data`. Send `Authorization: Bearer <token>` on every call except sign-in and `/health`.
`Atelier_API.postman_collection.json` has ready-to-run requests (import it into Postman; *Sign in* stores the token for the rest).

| Method & path | Permission | Notes |
|---|---|---|
| `GET /auth/options` | – | `{ registrationEnabled, requireEmailVerification, brandName }` |
| `POST /auth/login` | – | `{ email, password }` → session with `token`, or `twoFactorRequired` + `challengeToken` |
| `POST /auth/login/2fa` | – | `{ challengeToken, code }`: six-digit code or a recovery code |
| `POST /auth/logout` | signed in | Signs this device out |
| `POST /auth/register` | – | `{ name, email, password }` (when sign-up is on) |
| `POST /auth/verify-email` | – | `{ token }` from the emailed link |
| `POST /auth/resend-verification` | – | `{ email }` |
| `POST /auth/forgot-password` | – | `{ email }`: sends a reset link if the account exists |
| `POST /auth/reset-password/check` | – | `{ token }`: is the link still valid? |
| `POST /auth/reset-password` | – | `{ token, password }`: signs out every device |
| `GET /me` | signed in | Current user, roles and effective permissions |
| `PUT /me` | signed in | `{ name, phone, address }` |
| `PUT /me/password` | signed in | `{ currentPassword, newPassword }` → new token; other devices signed out |
| `GET /me/security` | signed in | Email confirmed, two-step on, recovery codes left, devices |
| `POST /me/verify-password` | signed in | `{ password }`: unlocks the lock screen |
| `POST /me/2fa/setup` → `POST /me/2fa/enable` | signed in | Secret + `otpauth://` link, then `{ code }` → 10 recovery codes |
| `POST /me/2fa/disable`, `POST /me/2fa/recovery-codes` | signed in | `{ password }` |
| `GET /me/sessions`, `DELETE /me/sessions/{id}`, `DELETE /me/sessions` | signed in | Devices; sign out one, or all others |
| `GET/PUT /me/notification-settings` | signed in | `{ security, account, product }`, each `{ inApp, email }` |
| `GET /me/activity` | signed in | Own sign-ins and changes, last 90 days |
| `GET /me/notifications` | signed in | `unread`, `page`, `size` → `{ items, total, unreadCount }` |
| `GET /me/notifications/unread-count` | signed in | For the bell badge |
| `POST /me/notifications/{id}/read` · `/unread` · `POST /me/notifications/read-all` · `DELETE /me/notifications/{id}` | signed in | |
| `GET /dashboard/summary` | `dashboard-view` | |
| `GET /users`, `GET /users/{id}` | `admin-user-view` | |
| `POST /users` | `admin-user-create` | `{ name, email, password, phone?, address?, roleIds?, enabled? }` |
| `PUT /users/{id}` | `admin-user-edit` | Same body; `password` optional |
| `PATCH /users/{id}/status` | `admin-user-edit` | `{ enabled }` |
| `POST /users/{id}/2fa/reset` | `admin-user-edit` | Turns two-step off for someone who lost their phone |
| `DELETE /users/{id}` | `admin-user-delete` | |
| `GET /roles`, `GET /roles/{id}` | `admin-role-view` | Includes `userCount` |
| `POST /roles` | `admin-role-create` | `{ roleName, description?, permissions?: [names] }` |
| `PUT /roles/{id}` | `admin-role-edit` | Rename / describe; leave `permissions` out to keep them |
| `PUT /roles/{id}/permissions` | `admin-role-edit` | Body: `[permissionId, …]`, replaces the set |
| `DELETE /roles/{id}` | `admin-role-delete` | |
| `GET /permissions` | `admin-role-view` | The permission catalogue |
| `GET /audit-logs` | super admin | `date_from`, `date_to`, `category`, `search`, `limit` |
| `GET /health` | – | `{ "status": "UP" }` |

## Extending it

**Add a permission** – create a migration, e.g. `src/main/resources/db/migration/V6__invoices.sql`:
```sql
INSERT INTO permissions (name, description) VALUES ('billing-invoice-view', 'See invoices');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p WHERE r.name = 'ADMIN' AND p.name = 'billing-invoice-view';
```
Name permissions `module-resource-action`; the web app groups them into its permission grid automatically.

**Protect an endpoint** – `@PreAuthorize("hasAuthority('billing-invoice-view')")`.

**Audit a business event** – inject `AuditLogService` and call
`logAction("Invoice", id, AuditAction.UPDATE, null, "Approved invoice INV-12")`. Requests and data changes are recorded automatically.

**Who is calling?** – `CurrentUser.get()` returns the signed-in user's id and email.

**Notify a user** – inject `NotificationService` and call
`notify(user, NotificationService.ACCOUNT, "INVOICE_APPROVED", "Invoice approved", "INV-12 was approved.", "/invoices/12")`.
It respects the user's in-app and email preferences.

**Fail with a friendly message** – `throw BusinessException.conflict("That name is taken.", "name")`; the response puts the message on the named field.

## Project layout

```
src/main/java/com/atelier/api/
  AtelierApiApplication.java
  config/        settings (AppProperties), Flyway, async/scheduling, password encoder,
                 audit request interceptor and Hibernate data-change listener
  controller/    Auth, Me, Notification, User, Role, Permission, Dashboard, AuditLog, Health
  dto/           NotificationSettings
  dto/request/   LoginRequest; account/ (Register, Email, Token, ResetPassword, TwoFactorLogin, Code,
                 PasswordConfirm); create/ (UserRequest, RoleRequest); update/ (UserUpdateRequest,
                 UserStatusRequest, ProfileUpdateRequest, PasswordChangeRequest)
  dto/response/  ApiResponse, LoginResponse, UserResponse, RoleResponse, PermissionResponse, Session,
                 Notification(Page), Activity, SecurityOverview, TwoFactorSetup, RecoveryCodes, AuthOptions,
                 DashboardSummaryResponse, AuditLogResponse
  entity/        BaseEntity, User, Role, Permission, AuditLog, RevokedToken, UserSession, UserToken,
                 RecoveryCode, Notification
  enums/         AuditAction, TokenPurpose
  exception/     BusinessException, GlobalExceptionHandler
  repository/    one per entity
  security/      SecurityConfig, JwtFilter, JwtUtil, AuthService, SessionService, TotpService, SecretCipher,
                 CustomUserDetailsService, TokenBlacklistService, LoginAttemptService, RateLimiter, Hashing,
                 CurrentUser
  seeder/        DataSeeder (first super admin, optional demo users)
  service/       UserService, AccountService, NotificationService, MailService, RoleService,
                 DashboardService, audit log services
src/main/resources/
  application.properties.example
  db/migration/  Flyway migrations V1–V5 (run automatically on start)
```

## Tests

Integration tests exercise the real HTTP API against PostgreSQL:
```bash
createdb -O atelier atelier_test
./mvnw test
```
Override the test database with `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`.

## Going to production

- Set a strong `JWT_SECRET` and keep it secret; changing it signs everyone out.
- Change the super admin password (or set `ADMIN_PASSWORD` before first start).
- Set `CORS_ALLOWED_ORIGINS` to your real web address only.
- Run behind HTTPS (a reverse proxy such as nginx or your cloud load balancer). The audit log reads the client IP from `X-Forwarded-For`.
- Brute-force counters and rate limits are kept in memory; if you run several API instances, they count separately.
- Configure SMTP (`MAIL_HOST` …) and set `WEB_URL` to the web app's public address, or links in emails won't work.
- Two-step secrets are encrypted with a key derived from `JWT_SECRET`: changing that secret turns two-step sign-in off in practice (users must set it up again).

## Author

**Anjana Ariyasinghe** · anjana7dilan@gmail.com

The back end of Anjana Admin, built for startup companies that need a secure, ready-made admin and back office. Copyright (c) 2026 Anjana Ariyasinghe. See [LICENSE.md](LICENSE.md).
