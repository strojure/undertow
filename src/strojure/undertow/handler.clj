(ns strojure.undertow.handler
  "Undertow `HttpHandler` functionality and library of standard handlers."
  (:require [strojure.undertow.api.types :as types]
            [strojure.undertow.handler.session :as session]
            [strojure.undertow.handler.websocket :as websocket])
  (:import (clojure.lang Fn IPersistentMap MultiFn Sequential)
           (io.undertow.server HttpHandler)
           (io.undertow.server.handlers GracefulShutdownHandler NameVirtualHostHandler PathHandler ProxyPeerAddressHandler RequestDumpingHandler)
           (io.undertow.server.handlers.error SimpleErrorPageHandler)
           (io.undertow.server.handlers.resource ClassPathResourceManager ResourceHandler ResourceManager)
           (io.undertow.server.session SessionAttachmentHandler)
           (io.undertow.websockets WebSocketConnectionCallback WebSocketProtocolHandshakeHandler)
           (org.xnio ChannelListener)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn define-type
  "Adds multimethods for declarative description of HTTP handlers.

  1) `sym` A constant to distinguish specific handler, usually symbol of handler
     function var.
  2) Options:
      - `:as-handler` The function `(fn [obj] handler)` to coerce `obj` to
                      handler.
      - `:as-wrapper` The function `(fn [obj] (fn [handler]))` returning
                      function to wrap another handler.
      - `:alias` The alias for the `sym`, usually keyword.
  "
  #_{:clj-kondo/ignore [:shadowed-var]}
  [sym {:keys [as-handler, as-wrapper, alias]}]
  (assert (or (fn? as-handler) (fn? as-wrapper)))
  (when as-handler
    (.addMethod ^MultiFn types/as-handler sym as-handler)
    (prefer-method types/as-handler sym Fn))
  (when as-wrapper
    (.addMethod ^MultiFn types/as-wrapper sym as-wrapper)
    (prefer-method types/as-wrapper sym Fn))
  (when-let [v (and (symbol? sym) (resolve sym))]
    (derive (class @v) sym))
  (when alias
    (derive alias sym)))

(defn arity2-wrapper
  "Converts 2-arity function `(fn [handler obj])` to function `(fn [obj])`
  returning handler wrapper `(fn [handler] ...)`."
  [f]
  (fn [opts] (fn [handler] (f handler opts))))

(defn arity1-wrapper
  "Converts 1-arity function `(fn [handler])` to function `(fn [obj])`
  returning handler wrapper `(fn [handler] ...)`."
  [f]
  (fn [_] f))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn chain
  "Chains sequence of handler wrappers with `handler`in direct order. Used for
  declarative description of handler chains. The 1-arity function chains
  preceding wrappers with the last handler in sequence.

  The expression

      (handler/chain my-handler [handler/request-dump
                                 handler/simple-error-page])
      ;=> #object[io.undertow.server.handlers.RequestDumpingHandler 0x7b3122a5 \"dump-request()\"]

  is same as

      (-> my-handler
          simple-error-page
          request-dump)
  "
  ([xs]
   (chain (last xs) (butlast xs)))
  ([handler xs]
   (reduce (fn [next-handler, wrapper]
             ((types/as-wrapper wrapper) (types/as-handler next-handler)))
           (types/as-handler handler)
           (reverse xs))))

(.addMethod ^MultiFn types/as-handler Sequential chain)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn with-exchange
  "A simple HttpHandler which invokes function `handle-exchange` with server
  exchange as argument."
  [handle-exchange]
  (reify HttpHandler
    (handleRequest [_ exchange] (handle-exchange exchange))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn dispatch
  "A HttpHandler that dispatches request to the XNIO worker thread pool if the
  current thread in the IO thread for the exchange."
  [next-handler]
  (let [handler (types/as-handler next-handler)]
    (reify HttpHandler
      (handleRequest [_ exchange]
        (if (.isInIoThread exchange)
          (-> exchange (.dispatch handler))
          (-> handler (.handleRequest exchange)))))))

(define-type `dispatch {:alias ::dispatch
                        :as-wrapper (arity1-wrapper dispatch)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

;;; Standard Undertow handlers.
;;; There are more handlers available in the `io.undertow.Handlers` package.

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn path
  "Returns a new path handler, with optional default handler. A `HttpHandler`
  that dispatches to a given handler based of a prefix match of the path. This
  only matches a single level of a request, e.g. if you have a request that
  takes the form: `/foo/bar`.

  Arguments:

  - `default-handler` The handler that is invoked if there are no paths matched.

  - `config` The configuration map with options:

      - `:prefix` The map of path prefixes and their handlers.
          + If the path does not start with a `/` then one will be prepended.
          + The match is done on a prefix bases, so registering `/foo` will also
            match `/foo/bar`. Though exact path matches are taken into account
            before prefix path matches. So if an exact path match exists its handler
            will be triggered.
          + If `/` is specified as the path then it will replace the default handler.

      - `:exact` The map of exact paths and their handlers.
          + If the request path is exactly equal to the given path, run the handler.
          + Exact paths are prioritized higher than prefix paths.

      - `:cache-size` The cache size, unlimited by default, integer.

  Example:

      (handler/path {:prefix {\"static\" (handler/resource {...})}
                     :exact {\"ws\" (handler/websocket {...})}})
  "
  {:tag PathHandler}
  ([config] (path nil config))
  ([default-handler {:keys [prefix exact cache-size]}]
   #_{:clj-kondo/ignore [:shadowed-var]}
   (letfn [(add-prefix-path [this [path handler]]
             (.addPrefixPath ^PathHandler this path (types/as-handler handler)))
           (add-exact-path [this [path handler]]
             (.addExactPath ^PathHandler this path (types/as-handler handler)))]
     (as->
       (cond (and default-handler
                  cache-size) (PathHandler. (types/as-handler default-handler) (int cache-size))
             default-handler, (PathHandler. (types/as-handler default-handler))
             cache-size,,,,,, (PathHandler. (int cache-size))
             :else,,,,,,,,,,, (PathHandler.))
       handler
       (reduce add-prefix-path handler prefix)
       (reduce add-exact-path handler exact)))))

(define-type `path {:alias ::path
                    :as-handler path
                    :as-wrapper (arity2-wrapper path)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn virtual-host
  "Returns a new virtual host handler, with optional default handler.
  A `HttpHandler` that implements virtual hosts based on the `Host:` http
  header.

  Arguments:

  - `default-handler` The handler that is invoked if there are no hostnames
                      matched.

  - `config` The configuration map with options:

      - `:host` The map of hostnames and their handlers.

  Example:

      (handler/virtual-host {:host {\"static.localhost\" (handler/resource {...})
                                    \"ws.localhost\" (handler/websocket {...})})
  "
  {:tag NameVirtualHostHandler}
  ([{:keys [host]}]
   (reduce (fn [this [host handler]]
             (.addHost ^NameVirtualHostHandler this host (types/as-handler handler)))
           (NameVirtualHostHandler.)
           host))
  ([default-handler, config]
   (-> ^NameVirtualHostHandler (virtual-host config)
       (.setDefaultHandler (types/as-handler default-handler)))))

(define-type `virtual-host {:alias ::virtual-host
                            :as-handler virtual-host
                            :as-wrapper (arity2-wrapper virtual-host)})

(comment
  (types/as-handler {:type virtual-host :host {"localhost" (with-exchange identity)}})
  (types/as-handler {:type `virtual-host :host {"localhost" (with-exchange identity)}})
  (types/as-handler {:type ::virtual-host :host {"localhost" (with-exchange identity)}})
  (types/as-wrapper {:type `virtual-host :host {"localhost" (with-exchange identity)}})
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(.addMethod ^MultiFn types/as-websocket-connection-callback ChannelListener
            websocket/listener-as-connection-callback)

(prefer-method types/as-websocket-connection-callback
               ChannelListener WebSocketConnectionCallback)

(defn websocket
  "Returns a new web socket session handler with optional next handler to invoke
  if the web socket connection fails. A `HttpHandler` which will process the
  `HttpServerExchange` and do the actual handshake/upgrade to WebSocket.

  Function arguments:

  - `next-handler` The handler that is invoked if there are no web socket
                   headers.

  - `callback` The instance of the `WebSocketConnectionCallback` or callback
               configuration map.

    Callback configuration options provided “as is” or as `:callback` key:

      - `:on-connect` The function `(fn [{:keys [callback exchange channel]}])`.
          + Is called once the WebSocket connection is established, which means
            the handshake was successful.

      - `:on-message` The function `(fn [{:keys [callback channel text data]}])`.
          + Is called when listener receives a message.
          + The text message is provided in `:text` and binary message is
            provided in `:data`.

      - `:on-close` The function `(fn [{:keys [callback channel code reason]}])`.
          + Is called once the WebSocket connection is closed.
          + The `:code` is status code to close messages:
            http://tools.ietf.org/html/rfc6455#section-7.4

      - `:on-error` The function `(fn [{:keys [callback channel error]}])`.
          + Is called on WebSocket connection error.
          + Default implementation just closes WebSocket connection.
  "
  {:tag WebSocketProtocolHandshakeHandler
   :arglists '([{:keys [on-connect, on-message, on-close, on-error] :as callback}]
               [{{:keys [on-connect, on-message, on-close, on-error]} :callback}]
               [callback]
               [next-handler, callback])}
  ([callback]
   (WebSocketProtocolHandshakeHandler. (types/as-websocket-connection-callback
                                         (:callback callback callback))))
  ([next-handler, callback]
   (WebSocketProtocolHandshakeHandler. (types/as-websocket-connection-callback
                                         (:callback callback callback))
                                       (types/as-handler next-handler))))

(define-type `websocket {:alias ::websocket
                         :as-handler websocket
                         :as-wrapper (arity2-wrapper websocket)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

;; Translate :resource-manager to :type
(defmethod types/as-resource-manager IPersistentMap
  [{:keys [resource-manager] :as config}]
  (types/as-resource-manager (assoc config :type resource-manager)))

;; ResourceManager for files from class path. Ignores directories to pass them
;; through instead of responding with 403 Forbidden.
(defmethod types/as-resource-manager :classpath-files
  [{:keys [prefix] :or {prefix "public"}}]
  (let [manager (ClassPathResourceManager. (ClassLoader/getSystemClassLoader) ^String prefix)]
    #_{:clj-kondo/ignore [:shadowed-var]}
    (reify ResourceManager
      (getResource [_ path]
        (let [resource (.getResource manager path)]
          ;; Return `nil` if resource is a directory.
          (when-not (some-> resource (.isDirectory))
            resource)))
      (isResourceChangeListenerSupported [_] false))))

(defn resource
  "Returns a new resource handler with optional next handler that is called if
  no resource is found.

  Function arguments:

  - `next-handler` The handler that is called if no resource is found.

  - `resource-manager` The instance of `ResourceManager` or resource manager
                       configuration map.

    Resource manager configuration options:

      - `:resource-manager` The type of configuration manager, keyword.
          + Used as `:type` in configuration passed to [[api.types/as-resource-manager]].

    Configuration options of `:classpath-files` resource manager:

      - `:prefix` The prefix that is appended to resources that are to be
                  loaded, string.
          + Default prefix is \"public\".
          + The `:classpath-files` resource manager ignores directories to
            avoid 403 Forbidden response.

  Example:

      (handler/resource {:resource-manager :classpath-files
                         :prefix \"public/static\"})
  "
  {:tag ResourceHandler}
  ([resource-manager]
   (ResourceHandler. (types/as-resource-manager resource-manager)))
  ([next-handler, resource-manager]
   (ResourceHandler. (types/as-resource-manager resource-manager) (types/as-handler next-handler))))

(define-type `resource {:alias ::resource
                        :as-handler resource
                        :as-wrapper (arity2-wrapper resource)})

(.addMethod ^MultiFn types/as-resource-manager `resource
            (get-method types/as-resource-manager IPersistentMap))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(.addMethod ^MultiFn types/as-session-manager IPersistentMap session/in-memory-session-manager)
(.addMethod ^MultiFn types/as-session-config IPersistentMap session/session-cookie-config)

(defn session
  "Returns a new handler that attaches the session to the request. This handler
  is also the place where session cookie configuration properties are
  configured. Note: this approach is not used by Servlet, which has its own
  session handlers.

  1) `next-handler` The handler that is called after attaching session.

  2) Handler configuration map with options:

      - `session-manager` The instance of `SessionManager` or session manager
        configuration map. If not specified then `InMemorySessionManager` is used
        with default settings (see [[in-memory-session-manager]]).

      - `session-config` The instance of `SessionConfig` or session config
        configuration map. If not specified then `SessionCookieConfig` is used
        with default settings (see [[session-cookie-config]]).
  "
  {:tag SessionAttachmentHandler}
  [next-handler {:keys [session-manager, session-config]
                 :or {session-manager {} session-config {}}}]
  (SessionAttachmentHandler. (types/as-handler next-handler)
                             (types/as-session-manager session-manager)
                             (types/as-session-config session-config)))

(define-type `session {:alias ::session
                       :as-wrapper (arity2-wrapper session)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn proxy-peer-address
  "Returns a new handler that sets the peer address based on the
  `X-Forwarded-For` and `X-Forwarded-Proto` headers.

  This should only be used behind a proxy that always sets this header,
  otherwise it is possible for an attacker to forge their peer address."
  {:tag ProxyPeerAddressHandler}
  [next-handler]
  (ProxyPeerAddressHandler. next-handler))

(define-type `proxy-peer-address {:alias ::proxy-peer-address
                                  :as-wrapper (arity1-wrapper proxy-peer-address)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn simple-error-page
  "Returns a handler that generates an extremely simple no frills error page."
  {:tag SimpleErrorPageHandler}
  [next-handler]
  (SimpleErrorPageHandler. (types/as-handler next-handler)))

(define-type `simple-error-page {:alias ::simple-error-page
                                 :as-wrapper (arity1-wrapper simple-error-page)})

(comment
  (types/as-handler {:type simple-error-page})
  (types/as-handler {:type ::simple-error-page})
  ((types/as-wrapper {:type simple-error-page}) identity)
  ((types/as-wrapper {:type ::simple-error-page}) identity)
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn graceful-shutdown
  "Returns a new handler that can be used to wait for all requests to finish
  before shutting down the server gracefully."
  {:tag GracefulShutdownHandler}
  [next-handler]
  (GracefulShutdownHandler. (types/as-handler next-handler)))

(define-type `graceful-shutdown {:alias ::graceful-shutdown
                                 :as-wrapper (arity1-wrapper graceful-shutdown)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn request-dump
  "Returns a handler that dumps requests to the log for debugging purposes."
  {:tag RequestDumpingHandler}
  [next-handler]
  (RequestDumpingHandler. (types/as-handler next-handler)))

(define-type `request-dump {:alias ::request-dump
                            :as-wrapper (arity1-wrapper request-dump)})

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
