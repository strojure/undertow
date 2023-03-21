# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## `1.1.1-109-SNAPSHOT`

Release date `UNRELEASED`

- (feat api): add type hint to `exchange/get-input-stream`
- (fix api BREAKING): change `:report-callback` CSP option to `:report-handler`

## `1.1.0-108`

Release date `2023-03-20`

- (feat): add `handler/security` with options:
  - `:csp` – content security policy header
  - `:hsts` – Strict-Transport-Security header
  - `:referrer-policy` – Referrer-Policy header
  - `:content-type-options` – X-Content-Type-Options header

## `1.0.92`

Release date `2023-03-09`

- (chore): Change license to Unlicense.

## `1.0.88`

Release date `2023-02-28`

- Upgrade Undertow to version `2.3.4.Final`.

## `1.0.85-rc3`

Release date `2023-02-27`

- Implement `types/as-websocket-callback` using protocols for better 
  performance.
- Add `handler/set-response-header`.
- Implement `types/as-listener-builder` using protocol.
- Implement `types/as-websocket-listener` using protocol.
- Implement `types/as-websocket-connection-callback` using protocol.
- Add `handler/on-response-commit`.
- Use full path in protocol function typehints.

## `1.0.72-rc2`

Release date `2023-01-17`

- Change `:class-path` resource manager to `:classpath-files` ignoring
  directories.

## `1.0.69-rc1`

Release date `2023-01-13`

- Implement `types/bean*` for conversion of Java classes to maps.
- Define handler types as symbols.
- Define handler type aliases via inheritance.
- Change `object-type` implementation for symbol/keyword/class/var.
- Fix `as-resource-manager` for `handler/resource`.

## `1.0.64-beta9`

Release date `2022-12-25`

- Cast :key-managers, :trust-managers to Java arrays.
- Upgrade Undertow to version `2.3.2.Final`

## `1.0.60-beta8`

Release date `2022-12-24`

- BUGFIX: Missing `stop-service` multimethod for ServerInstance

## `1.0.57-beta7`

Release date `2022-12-23`

- Rename `handler/session-attachment` to `session`
- Rename wrapper utility functions
    - `handler/as-arity-1-wrapper` to `arity1-wrapper`
    - `handler/as-arity-2-wrapper` to `arity2-wrapper`
- Replace `handler/wrap-handler` with `chain` function

## `1.0.47-beta.6`

Release date `2022-12-22`

- Rename `handler/force-dispatch` to `dispatch`.

## `1.0.43-beta.5`

Release date `2022-12-22`

- Return closeable instance in `server/start`.
- Remove `server/closeable` function.
- Rename `handler/exchange-fn` to `with-exchange`.
- Accept websocket callback in `:callback`.

## `1.0.31-beta.4`

Release date `2022-12-21`

- Remove namespace from server configuration options.

## `1.0.28-beta3`

Release date `2022-12-21`

- Add `server/closable` function.
- Import handler namespace for declarative configurations.
- Add `handler/exchange-fn` function.
- Rename configuration option `::server/builder-fn-wrapper`.

## `1.0.18-beta2`

Release date `2022-12-20`

- Implement Undertow API.
