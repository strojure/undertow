# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## `1.0.65-SNAPSHOT`

Release date `UNRELEASED`

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
