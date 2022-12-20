# undertow

Clojure API to Undertow web server.

[![cljdoc badge](https://cljdoc.org/badge/com.github.strojure/undertow)](https://cljdoc.org/d/com.github.strojure/undertow)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.strojure/undertow.svg)](https://clojars.org/com.github.strojure/undertow)

## Motivation

- Embrace Undertow API, don't hide Undertow features behind simplified DSL.
- Decouple server configuration and handler concepts like ring handler or
  pedestal interceptors.
- Easily extend implementation with handler concepts like ring and interceptors.
- Reuse Undertow's library of HTTP handlers.
- Have option to describe server configuration declarative.
