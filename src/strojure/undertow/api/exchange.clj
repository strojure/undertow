(ns strojure.undertow.api.exchange
  "Helper functions to work with `HttpServerExchange`."
  (:import (io.undertow.server Connectors HttpHandler HttpServerExchange)
           (io.undertow.server.session Session SessionConfig SessionManager)
           (java.io OutputStream)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defmacro async-dispatch
  "Dispatches execution of task `expr` to the XNIO worker thread pool. Required
  for async operations with server exchange."
  [exchange expr]
  `(-> ~(with-meta exchange {:tag 'io.undertow.server.HttpServerExchange})
       (.dispatch ^Runnable (^:once fn* [] ~expr))))

(defn async-throw
  "Rethrows `throwable` in context of request handling to allow server to handle
  error. Required for async operations with server exchange."
  [exchange, throwable]
  (-> (reify HttpHandler (handleRequest [_ _] (throw throwable)))
      (Connectors/executeRootHandler exchange)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn get-session-manager
  "Returns session manager from `exchange`."
  {:inline (fn [e] `^SessionManager (.getAttachment ~(with-meta e {:tag 'io.undertow.server.HttpServerExchange})
                                                    SessionManager/ATTACHMENT_KEY))}
  ^SessionManager
  [exchange]
  (-> ^HttpServerExchange exchange
      (.getAttachment SessionManager/ATTACHMENT_KEY)))

(defn sessions-enabled?
  "True if session manager is attached to the `exchange`."
  {:inline (fn [e] `(boolean (get-session-manager ~e)))}
  [exchange]
  (boolean (get-session-manager exchange)))

(defn get-session-config
  "Returns session config from the `exchange`."
  ^SessionConfig
  [exchange]
  (-> ^HttpServerExchange exchange
      (.getAttachment SessionConfig/ATTACHMENT_KEY)))

(defn get-existing-session
  "Returns session from the `exchange` or nil if is not created."
  ^Session
  [exchange]
  (some-> (get-session-manager exchange)
          (.getSession exchange (get-session-config exchange))))

(defn get-or-create-session
  "Return session from the `exchange`, creates new session if necessary. Returns
  `nil` if session manager is not attached to the `exchange`."
  ^Session
  [exchange]
  (when-let [mgr (get-session-manager exchange)]
    (let [cfg (get-session-config exchange)]
      (or (.getSession mgr exchange cfg)
          (.createSession mgr exchange cfg)))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn start-blocking*
  "Custom version of the `startBlocking` method which avoids exception in
  completed requests."
  [^HttpServerExchange e]
  ;; We close request channel in completed request to avoid exception caused by
  ;; `DefaultBlockingHttpExchange.getInputStream`:
  ;; java.io.IOException: UT000034: Stream is closed
  (when (and (.isRequestChannelAvailable e)
             (.isRequestComplete e))
    (-> (.getRequestChannel e)
        (.close)))
  (.startBlocking e))

(defn get-input-stream
  "Returns input stream for incomplete request. Starts blocking if necessary but
  does not check if running on IO thread."
  [^HttpServerExchange e]
  (when-not (.isRequestComplete e)
    (when-not (.isBlocking e)
      (.startBlocking e))
    (.getInputStream e)))

(defn new-output-stream
  "Returns new output stream. Starts blocking if necessary."
  ^OutputStream
  [^HttpServerExchange e]
  (start-blocking* e)
  (.getOutputStream e))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
