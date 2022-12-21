(ns strojure.undertow.server
  "Undertow server functionality (start, stop, options etc.)."
  (:require [strojure.undertow.api.builder :as builder]
            [strojure.undertow.api.types :as types])
  (:import (io.undertow Undertow Undertow$Builder UndertowOptions)
           (java.io Closeable)
           (org.xnio Options)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn start
  "Starts Undertow server given instance, builder or configuration map.

  Server configuration map options:

  - `:port` The map of ports and their listeners.
      + Can be just a port number for HTTP listener with default configuration.
      + The port listener is an instance of `Undertow$ListenerBuilder` or
        listener builder configuration map.
      + Port listener configuration options:
          - `:host`  The host name string, default \"localhost\".
          - `:https` HTTPS configuration map with options:
              - `:key-managers`   The instance of `javax.net.ssl.KeyManager[]`.
              - `:trust-managers` The instance of `javax.net.ssl.TrustManager[]`.
              - `:ssl-context`    The instance of `javax.net.ssl.SSLContext`.
          - `:handler` The listener HttpHandler to be used on the port.
                       See below how to declare handlers.
          - `:socket-options` The map of socket options for the listener.
              - `:undertow/enable-http2`. If HTTP2 protocol enabled, boolean.
              + Other option keywords can be found below in this namespace.
          - `:use-proxy-protocol` boolean.
      + The `:https` enables HTTPS protocol for the listener.
      + Declaration of AJP protocol is not supported.

  - `:handler` The server HttpHandler to be used for all listeners without
               handler specified. See below how declare handlers.

  - `:buffer-size`    integer.
  - `:io-threads`     The number of IO threads to create.
  - `:worker-threads` The number of worker threads, integer.
  - `:direct-buffers` If direct buffers enabled, boolean.
  - `:server-options` The map of server options.
  - `:socket-options` The map of socket options.
  - `:worker-options` The map of worker options.

  - `::handler-fn-adapter` The function `(fn [f] handler)`
      + Defines coercion of clojure functions to HttpHandler during invocation
        of `start`, like i.e. ring handler.
      + By default, the coercion is not defined and functions cannot be used as
        handler.
      + The coercion can be assigned permanently using [[set-handler-fn-adapter]].

  - `::wrap-builder-fn`
      + The function `(fn [f] (fn [builder config] (f builder config)))` which
        wraps standard builder configuration function `f` returning new function
        on builder and configuration.
      + Allows to customize builder configuration in any way by modifying
        builder, config and even ignoring function `f`.
      + Allows to make settings which are not available in declarative
        configuration like `setWorker`, `setByteBufferPool` etc.

  Server configuration example:

      {:port {;; HTTP port listener
              8080 {:host \"localhost\"
                    :handler (comment \"Listener handler declaration.\")}
              ;; HTTPS port listener
              4040 {:https {:ssl-context '_}}}
       ;; Server handler for all listeners without handlers.
       :handler (comment \"Server handler declaration.\")
       :server-options {:undertow/enable-http2 true}}

  **Handler declaration**

  Handlers can be declared and chained using function invocations:

      ;; The chain of HTTP handler in reverse order.
      (-> default-handler-fn
          ;; The handlers for app hostnames.
          (handler/virtual-host {:host {\"app1\" app1-handler-fn
                                        \"app2\" app2-handler-fn}})
          ;; Enable sessions for handlers above.
          (handler/session-attachment {})
          ;; The handler for specific path
          (handler/path {:prefix {\"static\" (handler/resource {:resource-manager :class-path
                                                              :prefix \"public/static\"})}
                         :exact {\"websocket\" (handler/websocket {:on-connect (fn [{:keys [channel] :as event}])
                                                                 :on-message (fn [{:keys [channel text] :as event}])
                                                                 :on-close (fn [event])
                                                                 :on-error (fn [event])})}})
          ;; The handler for webapi hostname.
          (handler/virtual-host {:host {\"webapi.localtest.me\" webapi-handler-fn}})
          (handler/simple-error-page)
          (handler/proxy-peer-address)
          (handler/graceful-shutdown))

  Or same handler written declarative:

      [;; The chain of HTTP handlers in direct order.
       {:type handler/graceful-shutdown}
       {:type handler/proxy-peer-address}
       {:type handler/simple-error-page}
       ;; The handler for webapi hostname.
       {:type handler/virtual-host
        :host {\"webapi.localtest.me\" webapi-handler-fn}}
       ;; The handler for specific path
       {:type handler/path
        :prefix {\"static\" {:type handler/resource
                           :resource-manager :class-path
                           :prefix \"public/static\"}}
        :exact {\"websocket\" {:type handler/websocket
                             :on-connect (fn [{:keys [channel] :as event}])
                             :on-message (fn [{:keys [channel text] :as event}])
                             :on-close (fn [event])
                             :on-error (fn [event])}}}
       ;; Enable sessions for next handlers.
       {:type handler/session-attachment}
       ;; The handlers for app hostnames.
       {:type handler/virtual-host :host {\"app1\" app1-handler-fn
                                          \"app2\" app2-handler-fn}}
       default-handler-fn]

  Keywords can be used instead of symbols as handler `:type` values:

      {:type ::handler/proxy-peer-address}

  There are some Undertow handlers available in the `handler` namespace. Others
  can be used via Java interop or adapted for declarative description using
  [[handler/define-type]] function.
  "
  {:arglists '([{:keys [port, handler,
                        buffer-size, io-threads, worker-threads, direct-buffers,
                        server-options, socket-options, worker-options]
                 ::keys
                 [handler-fn-adapter, wrap-builder-fn]}])}
  [config-or-server]
  (types/server-start config-or-server))

(defn stop
  "Stops server instance, returns nil. The instance can be an instance of
  `Undertow` or map with `::undertow` key."
  [instance]
  (types/server-stop instance))

(defmethod types/server-start :default
  [{::keys [handler-fn-adapter, wrap-builder-fn] :as config}]
  (binding [types/*handler-fn-adapter* (or handler-fn-adapter types/*handler-fn-adapter*)]
    (let [builder-fn (cond-> builder/configure wrap-builder-fn (wrap-builder-fn))
          server (-> (Undertow/builder)
                     (builder-fn config)
                     (builder/build))]
      (.start server)
      {::undertow server :type ::instance})))

(defmethod types/server-start ::instance
  [{::keys [undertow]}]
  (types/server-start undertow))

(defmethod types/server-stop ::instance
  [{::keys [undertow]}]
  (types/server-stop undertow))

(defmethod types/server-start Undertow$Builder
  [builder]
  (start {::wrap-builder-fn (constantly builder)}))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn closeable
  "Returns `java.io.Closable` interface for running servers instance to use with
  `with-open` macro like:

      (with-open [_ (closable (start {...}))]
        ;; Use running server here then close it.
        )
  "
  ^Closeable [instance]
  (reify Closeable
    (close [_] (stop instance))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn set-handler-fn-adapter
  "Permanently assigns coercion of Clojure function to `HttpHandler`. Can be
  used by adapters like Ring handler adapter."
  [f]
  (alter-var-root #'types/*handler-fn-adapter* (constantly f)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn define-option
  "Defines keyword alias for Undertow option. The optional `coerce-fn` is used
  to coerce option value to correct Java type."
  ([alias option] (define-option alias option identity))
  ([alias option coerce-fn]
   (defmethod types/as-option alias
     [_ v]
     [option (coerce-fn v)])))

;;;; Options

;;; XNIO workers

;; The number of IO threads to create. IO threads perform non-blocking tasks,
;; and should never perform blocking operations because they are responsible for
;; multiple connections, so while the operation is blocking other connections
;; will essentially hang. Two IO threads per CPU core is a reasonable default.
(define-option :xnio/worker-io-threads
               Options/WORKER_IO_THREADS int)

;; The number of threads in the workers blocking task thread pool. When
;; performing blocking operations such as Servlet requests threads from this
;; pool will be used. In general, it is hard to give a reasonable default for
;; this, as it depends on the server workload. Generally this should be
;; reasonably high, around 10 per CPU core.
(define-option :xnio/worker-task-core-threads
               Options/WORKER_TASK_CORE_THREADS int)

;;; Common Listener Options

;; The maximum size of a HTTP header block, in bytes. If a client sends more
;; data that this as part of the request header then the connection will be
;; closed. Defaults to 50k.
(define-option :undertow/max-header-size
               UndertowOptions/MAX_HEADER_SIZE int)

;; The default maximum size of a request entity. If entity body is larger than
;; this limit then a java.io.IOException will be thrown at some point when
;; reading the request (on the first read for fixed length requests, when too
;; much data has been read for chunked requests). This value is only the default
;; size, it is possible for a handler to override this for an individual request
;; by calling io.undertow.server.HttpServerExchange.setMaxEntitySize(long size).
;; Defaults to unlimited.
(define-option :undertow/max-entity-size
               UndertowOptions/MAX_ENTITY_SIZE long)

;; The default max entity size when using the Multipart parser. This will
;; generally be larger than MAX_ENTITY_SIZE. Having a separate setting for this
;; allows for large files to be uploaded, while limiting the size of other
;; requests.
(define-option :undertow/multipart-max-entity-size
               UndertowOptions/MULTIPART_MAX_ENTITY_SIZE long)

;; The maximum number of query parameters that are permitted in a request. If a
;; client sends more than this number the connection will be closed. This limit
;; is necessary to protect against hash based denial of service attacks.
;; Defaults to 1000.
(define-option :undertow/max-parameters
               UndertowOptions/MAX_PARAMETERS int)

;; The maximum number of headers that are permitted in a request. If a client
;; sends more than this number the connection will be closed. This limit is
;; necessary to protect against hash based denial of service attacks. Defaults
;; to 200.
(define-option :undertow/max-headers
               UndertowOptions/MAX_HEADERS int)

;; The maximum number of cookies that are permitted in a request. If a client
;; sends more than this number the connection will be closed. This limit is
;; necessary to protect against hash based denial of service attacks. Defaults
;; to 200.
(define-option :undertow/max-cookies
               UndertowOptions/MAX_COOKIES int)

;; The charset to use to decode the URL and query parameters. Defaults to UTF-8.
(define-option :undertow/url-charset
               UndertowOptions/URL_CHARSET)

;; Determines if the listener will decode the URL and query parameters, or
;; simply pass it through to the handler chain as is. If this is set url encoded
;; characters will be decoded to the charset specified in URL_CHARSET. Defaults
;; to true.
(define-option :undertow/decode-url
               UndertowOptions/DECODE_URL boolean)

;; If a request comes in with encoded / characters (i.e. %2F), will these be
;; decoded. This can cause security problems
;; (link:http://cve.mitre.org/cgi-bin/cvename.cgi?name=CVE-2007-0450) if a front
;; end proxy does not perform the same decoding, and as a result this is
;; disabled by default.
(define-option :undertow/allow-encoded-slash
               UndertowOptions/ALLOW_ENCODED_SLASH boolean)

;; If this is true then Undertow will allow non-escaped equals characters in
;; unquoted cookie values. Unquoted cookie values may not contain equals
;; characters. If present the value ends before the equals sign. The remainder
;; of the cookie value will be dropped. Defaults to false.
(define-option :undertow/allow-equals-in-cookie-value
               UndertowOptions/ALLOW_EQUALS_IN_COOKIE_VALUE boolean)

;; If the server should add a HTTP Date header to all response entities which do
;; not already have one. The server sets the header right before writing the
;; response, if none was set by a handler before. Unlike the DateHandler it will
;; not overwrite the header. The current date string is cached, and is updated
;; every second. Defaults to true.
(define-option :undertow/always-set-date
               UndertowOptions/ALWAYS_SET_DATE boolean)

;; If a HTTP Connection: keep-alive header should always be set, even for
;; HTTP/1.1 requests that are persistent by default. Even though the spec does
;; not require this header to always be sent it seems safer to always send it.
;; If you are writing some kind of super high performance application and are
;; worried about the extra data being sent over the wire this option allows you
;; to turn it off. Defaults to true.
(define-option :undertow/always-set-keep-alive
               UndertowOptions/ALWAYS_SET_KEEP_ALIVE boolean)

;; The maximum size of a request that can be saved in bytes. Requests are
;; buffered in a few situations, the main ones being SSL renegotiation and
;; saving post data when using FORM based auth. Defaults to 16,384 bytes.
(define-option :undertow/max-buffered-request-size
               UndertowOptions/MAX_BUFFERED_REQUEST_SIZE int)

;; If the server should record the start time of a HTTP request. This is
;; necessary if you wish to log or otherwise use the total request time, however
;; has a slight performance impact, as it means that System.nanoTime() must be
;; called for each request. Defaults to false.
(define-option :undertow/record-request-start-time
               UndertowOptions/RECORD_REQUEST_START_TIME boolean)

;; The amount of time a connection can be idle for before it is timed out. An
;; idle connection is a connection that has had no data transfer in the idle
;; timeout period. Note that this is a fairly coarse grained approach, and small
;; values will cause problems for requests with a long processing time.
(define-option :undertow/idle-timeout
               UndertowOptions/IDLE_TIMEOUT int)

;; How long a request can spend in the parsing phase before it is timed out.
;; This timer is started when the first bytes of a request are read, and
;; finishes once all the headers have been parsed.
(define-option :undertow/request-parse-timeout
               UndertowOptions/REQUEST_PARSE_TIMEOUT int)

;; The amount of time a connection can sit idle without processing a request,
;; before it is closed by the server.
(define-option :undertow/no-request-timeout
               UndertowOptions/NO_REQUEST_TIMEOUT int)

;;; HTTP Listener

;; If this is true then the connection can be processed as a HTTP/2 prior
;; knowledge connection. If a HTTP/2 client connects directly to the listener
;; with a HTTP/2 connection preface then the HTTP/2 protocol will be used
;; instead of HTTP/1.1.
(define-option :undertow/enable-http2
               UndertowOptions/ENABLE_HTTP2 boolean)

;;; HTTP2 Listener

;; The size of the header table that is used for compression. Increasing this
;; will use more memory per connection, but potentially decrease the amount of
;; data that is sent over the wire. Defaults to 4096.
(define-option :undertow/http2-settings-header-table-size
               UndertowOptions/HTTP2_SETTINGS_HEADER_TABLE_SIZE int)

;; If server push is enabled for this connection.
(define-option :undertow/http2-settings-enable-push
               UndertowOptions/HTTP2_SETTINGS_ENABLE_PUSH boolean)

;; The maximum number of streams a client is allowed to have open at any one
;; time.
(define-option :undertow/http2-settings-max-concurrent-streams
               UndertowOptions/HTTP2_SETTINGS_MAX_CONCURRENT_STREAMS int)

;; The initial flow control window size.
(define-option :undertow/http2-settings-initial-window-size
               UndertowOptions/HTTP2_SETTINGS_INITIAL_WINDOW_SIZE int)

;; The maximum frame size.
(define-option :undertow/http2-settings-max-frame-size
               UndertowOptions/HTTP2_SETTINGS_MAX_FRAME_SIZE int)

(comment
  (types/as-option :undertow/enable-http2 true)
  (types/as-option :undertow/enable-http2 nil)
  (types/as-option UndertowOptions/ENABLE_HTTP2 true)
  (types/as-option :xnio/worker-io-threads 4)
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
