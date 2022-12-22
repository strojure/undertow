(ns strojure.undertow.api.types
  "Functions to coerce Clojure types to Undertow Java classes."
  (:import (clojure.lang Fn MultiFn)
           (io.undertow Undertow Undertow$ListenerBuilder)
           (io.undertow.server HttpHandler)
           (io.undertow.server.handlers.resource ResourceManager)
           (io.undertow.server.session SessionConfig SessionManager)
           (io.undertow.websockets WebSocketConnectionCallback)
           (io.undertow.websockets.core WebSocketCallback)
           (java.io Closeable)
           (org.xnio ChannelListener Option OptionMap)
           (strojure.undertow.websocket WebSocketChannelListener)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn object-type
  "Returns object type to be used as dispatch value for multimethods.

  - for maps returns value of the `:type` key or `:default`.
  - for other objects returns `(type obj)`.
  "
  [obj]
  (if (map? obj) (:type obj :default)
                 (type obj)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defrecord ServerInstance [undertow]
  Closeable
  (close [_] (.stop ^Undertow undertow)))

(defmulti server-start
  "Starts Undertow server given `obj`, returns closeable ServerInstance."
  {:arglists '([obj])
   :tag ServerInstance}
  object-type)

(defmulti server-stop
  "Stops Undertow server, returns nil."
  {:arglists '([obj])}
  object-type)

(defmethod server-start Undertow [^Undertow server] (ServerInstance. (doto server .start)))
(defmethod server-stop Undertow [^Undertow server] (.stop server))

(defmethod server-start ServerInstance [instance] (server-start (:undertow instance)))
(defmethod server-stop ServerInstance [instance] (server-stop (:undertow instance)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-handler
  "Coerces `obj` to the instance of `HttpHandler`."
  {:arglists '([obj]) :tag HttpHandler}
  object-type)

(.addMethod ^MultiFn as-handler HttpHandler identity)

(def ^{:dynamic true :arglists '([handler-fn])}
  *handler-fn-adapter*
  "The function `(fn [f] handler)` to coerce Clojure functions to `HttpHandler`
  instances.

  - Default implementation raise exception.
  - Can be overridden permanently using `server/set-handler-fn-adapter` function.
  "
  (fn [f]
    (throw (ex-info (str "Cannot use function as undertow handler: " f "\n"
                         "Define permanent coercion using `server/set-handler-fn-adapter`.")
                    {}))))

(defn- validate-handler-fn-adapter
  [f]
  (or (instance? Fn f)
      (instance? MultiFn f)
      (throw (IllegalArgumentException. (str "Requires function for *handler-fn-adapter*: " f)))))

(set-validator! #'*handler-fn-adapter* validate-handler-fn-adapter)

(.addMethod ^MultiFn as-handler Fn,,,,, #'*handler-fn-adapter*)
(.addMethod ^MultiFn as-handler MultiFn #'*handler-fn-adapter*)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-wrapper
  "Coerces `obj` to the 1-arity function which wraps handler and returns new
  handler."
  {:arglists '([obj])}
  object-type)

(.addMethod ^MultiFn as-wrapper Fn identity)
(.addMethod ^MultiFn as-wrapper MultiFn identity)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-listener-builder
  "Coerces `obj` to the instance of `io.undertow.Undertow$ListenerBuilder`."
  {:arglists '([obj]) :tag Undertow$ListenerBuilder}
  object-type)

(.addMethod ^MultiFn as-listener-builder Undertow$ListenerBuilder identity)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-option
  "Coerces option `k` to the pair of Undertow option and value of correct type.
  Used to create keyword aliases to Java objects of Undertow objects."
  {:arglists '([k v])}
  (fn [k _] k))

(defmethod as-option :default
  [option value]
  (if (instance? Option option)
    [option value]
    (throw (ex-info (str "Unknown undertow option: " option "\n"
                         "Use `define-option` to define new options.") {}))))

(defn as-option-map
  "Coerces Clojure map to Undertow's `OptionMap`."
  ^OptionMap
  [m]
  (if (seq m)
    (-> (OptionMap/builder)
        (.add (->> m (into {} (map (fn [[k v]] (as-option k v))))))
        (.getMap))
    OptionMap/EMPTY))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-resource-manager
  "Coerces `obj` to the instance of
  `io.undertow.server.handlers.resource.ResourceManager`"
  {:arglists '([obj]) :tag ResourceManager}
  object-type)

(.addMethod ^MultiFn as-resource-manager ResourceManager identity)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-session-manager
  "Coerces `obj` to the instance of `SessionManager`."
  {:arglists '([obj]) :tag SessionManager}
  object-type)

(.addMethod ^MultiFn as-session-manager SessionManager identity)

(defmulti as-session-config
  "Coerces `obj` to the instance of `SessionConfig`."
  {:arglists '([obj]) :tag SessionConfig}
  object-type)

(.addMethod ^MultiFn as-session-config SessionConfig identity)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-websocket-listener
  "Coerces `obj` to the instance of `ChannelListener`."
  {:arglists '([obj]) :tag ChannelListener}
  object-type)

(.addMethod ^MultiFn as-websocket-listener ChannelListener identity)

(defmethod as-websocket-listener :default
  [config]
  (WebSocketChannelListener. config))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-websocket-connection-callback
  "Coerces `obj` to the instance of `WebSocketConnectionCallback`."
  {:arglists '([obj]) :tag WebSocketConnectionCallback}
  object-type)

(.addMethod ^MultiFn as-websocket-connection-callback WebSocketConnectionCallback identity)

(defmethod as-websocket-connection-callback :default
  [obj]
  (as-websocket-connection-callback (as-websocket-listener obj)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmulti as-websocket-callback
  "Coerces `obj` to the instance of `WebSocketCallback`."
  {:arglists '([obj]) :tag WebSocketCallback}
  object-type)

(.addMethod ^MultiFn as-websocket-callback WebSocketCallback identity)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
