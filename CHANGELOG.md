# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/).

## Version `1.0.44-SNAPSHOT`



## Version `1.0.43-beta.5`

- Return closeable instance in `server/start`.
- Remove `server/closeable` function.
- Rename `handler/exchange-fn` to `with-exchange`.
- Accept websocket callback in `:callback`.

## Version `1.0.31-beta.4` on 2022-12-21
- 
- Remove namespace from server configuration options.

## Version `1.0.28-beta3` on 2022-12-21

- Add `server/closable` function.
- Import handler namespace for declarative configurations.
- Add `handler/exchange-fn` function.
- Rename configuration option `::server/builder-fn-wrapper`.

## Version `1.0.18-beta2` on 2022-12-20

- Implement Undertow API.
