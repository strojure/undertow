# undertow

Clojure API to Undertow web server.

[![cljdoc badge](https://cljdoc.org/badge/com.github.strojure/undertow)](https://cljdoc.org/d/com.github.strojure/undertow)
[![Clojars Project](https://img.shields.io/clojars/v/com.github.strojure/undertow.svg)](https://clojars.org/com.github.strojure/undertow)

## Motivation

- Embrace Undertow API, don't hide Undertow features behind layers of 
  simplifying abstractions.
- Decouple server configuration and concepts like [ring handlers]
  or [pedestal interceptors].
- Extend functionality using Clojure idioms.
- Reuse Undertow's library of HTTP handlers.
- Provide declarative description of server configuration.
- Minimize the impact of implementation on performance.

## Companion projects

- Ring adapter https://github.com/strojure/ring-undertow.

## Usage

Undertow server can be started using [start][server_start] and stopped
with [stop][server_stop]. The running server instance is `java.io.Closeable` so
it can be used with `with-open` macro.

### Server configuration

The [start][server_start] function accepts clojure map with options 
translated to corresponding calls of Undertow builder methods. The 
configuration map structure reflects Undertow’s Java API.

The minimal configuration has only `:port` and `:handler` keys (Undertow 
will start even with empty configuration, but it is pretty useless). This 
starts HTTP listener on 8080 port with default settings.

```clojure
(server/start {:port 8080 :handler my-handler})
```

### Listener configuration

Listener configuration is defined in a map of ports and listener options 
under the `:port` key.

Simple HTTP listener:

```clojure
;; All three are the same:
{:port 8080}
{:port {8080 {}}}
{:port {8080 {:host "localhost"}}}
```

Every port can use its own handler instead of server’s one.

```clojure
{:port {8081 {:handler my-handler-1}
        8082 {:handler my-handler-2}}}
```

HTTPS listener:

```clojure
{:port {4242 {:https {:key-managers [] :trust-managers []}}}}
{:port {4242 {:https {:ssl-context my-ssl-context}}}}
```

NOTE: The AJP listener type is not available in declarative form.

### Handler configuration

Let’s suppose there is a scenario:

- Webapi handler on the "webapi.company.com" host.
- Application specific handlers on the hosts "app1.company.com" and
  "app2.company.com".
- Static resource handler for app hosts but not for webapi.
- Websocket handler for app hosts but not for webapi.
- HTTP sessions for app hosts but not for webapi, websockets and static 
  resources.

The Undertow handler for this case can be configured in different ways:

```clojure
(ns usage.handler-configuration
  (:require [strojure.undertow.handler :as handler]
            [strojure.undertow.server :as server])
  (:import (io.undertow.server HttpServerExchange)))

(defn- my-handler
  [label]
  (handler/with-exchange
    (fn [^HttpServerExchange e]
      (-> (.getResponseSender e)
          (.send (str "Dummy handler: " label))))))

(def ^:private websocket-callback
  {:on-connect (fn [{:keys [callback exchange channel]}] (comment callback exchange channel))
   :on-message (fn [{:keys [callback channel text]}] (comment callback channel text))
   :on-close (fn [{:keys [callback channel code reason]}] (comment callback channel code reason))
   :on-error (fn [{:keys [callback channel error]}] (comment callback channel error))})

(defn imperative-handler-config
  "The handler configuration created by invocation of series of handler
  constructors."
  []
  ;; The chain of HTTP handler in reverse order.
  (-> (my-handler :default-handler)
      ;; The handlers for app hostnames.
      (handler/virtual-host
        {:host {"app1.company.com" (my-handler :app1-handler)
                "app2.company.com" (my-handler :app2-handler)}})
      ;; Enable sessions for next handlers (above).
      (handler/session {})
      ;; Path specific handlers.
      (handler/path {:prefix {"static" (handler/resource {:resource-manager :classpath-files
                                                          :prefix "public/static"})}
                     :exact {"websocket" (handler/websocket websocket-callback)}})
      ;; The handler for webapi hostname.
      (handler/virtual-host {:host {"webapi.company.com" (my-handler :webapi-handler)}})
      ;; Supplemental useful handlers.
      (handler/simple-error-page)
      (handler/proxy-peer-address)
      (handler/graceful-shutdown)))

(defn symbol-handler-config
  "Declarative handler configuration as sequence of chaining handlers which are
  referred as symbols."
  []
  [;; Supplemental useful handlers.
   {:type `handler/graceful-shutdown}
   {:type `handler/proxy-peer-address}
   {:type `handler/simple-error-page}
   ;; The handler for webapi hostname.
   {:type `handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
   ;; Path specific handlers.
   {:type `handler/path
    :prefix {"static" {:type `handler/resource :resource-manager :classpath-files
                       :prefix "public/static"}}
    :exact {"websocket" {:type `handler/websocket :callback websocket-callback}}}
   ;; Enable sessions for next handlers.
   {:type `handler/session}
   ;; The handlers for app hostnames.
   {:type `handler/virtual-host
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Last resort handler
   (my-handler :default-handler)])

(defn instance-handler-config
  "Declarative handler configuration as sequence of chaining handlers which are
  referred as handler function instances."
  []
  [;; Supplemental useful handlers.
   {:type handler/graceful-shutdown}
   {:type handler/proxy-peer-address}
   {:type handler/simple-error-page}
   ;; The handler for webapi hostname.
   {:type handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
   ;; Path specific handlers.
   {:type handler/path
    :prefix {"static" {:type handler/resource :resource-manager :classpath-files
                       :prefix "public/static"}}
    :exact {"websocket" {:type handler/websocket :callback websocket-callback}}}
   ;; Enable sessions for next handlers.
   {:type handler/session}
   ;; The handlers for app hostnames.
   {:type handler/virtual-host
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Last resort handler
   (my-handler :default-handler)])

(defn keyword-handler-config
  "Declarative handler configuration as sequence of chaining handlers which are
  referred as keywords so can be easily stored in EDN file."
  []
  [;; Supplemental useful handlers.
   {:type ::handler/graceful-shutdown}
   {:type ::handler/proxy-peer-address}
   {:type ::handler/simple-error-page}
   ;; The handler for webapi hostname.
   {:type ::handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
   ;; Path specific handlers.
   {:type ::handler/path
    :prefix {"static" {:type ::handler/resource :resource-manager :classpath-files
                       :prefix "public/static"}}
    :exact {"websocket" {:type ::handler/websocket
                         :callback websocket-callback}}}
   ;; Enable sessions for next handlers.
   {:type ::handler/session}
   ;; The handlers for app hostnames.
   {:type ::handler/virtual-host
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Last resort handler
   (my-handler :default-handler)])

(comment
  (with-open [_ (server/start {:handler (imperative-handler-config)})])
  (with-open [_ (server/start {:handler (symbol-handler-config)})])
  (with-open [_ (server/start {:handler (instance-handler-config)})])
  (with-open [_ (server/start {:handler (keyword-handler-config)})])
  )
```

---

[ring handlers]:
https://github.com/ring-clojure/ring/wiki/Concepts#handlers

[pedestal interceptors]:
http://pedestal.io/reference/interceptors

[server_start]:
https://cljdoc.org/d/com.github.strojure/undertow/CURRENT/api/strojure.undertow.server#start

[server_stop]:
https://cljdoc.org/d/com.github.strojure/undertow/CURRENT/api/strojure.undertow.server#stop
