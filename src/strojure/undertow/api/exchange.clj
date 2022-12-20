(ns strojure.undertow.api.exchange
  "Helper functions to work with `HttpServerExchange`."
  (:import (io.undertow.server Connectors HttpHandler HttpServerExchange)
           (io.undertow.server.session Session SessionConfig SessionManager)
           (io.undertow.util HeaderMap HttpString)
           (java.io OutputStream)
           (java.util Collection)))

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

(defn- put-headers!
  "Inserts map `headers` into mutating `header-map!`, returns `nil`.

  - All keys and values in `headers` are converted to string with `str`.
  - Sequential values are put as multiple headers.
  - Keys from `headers` overwrite existing keys in `header-map`.
  - Keys with `nil` value remove headers from `header-map`.
  "
  [header-map!, headers]
  (reduce-kv (fn [^HeaderMap h! k v]
               (cond
                 (sequential? v)
                 (doto h! (.putAll (HttpString. (str k))
                                   ^Collection (map str v)))
                 (some? v)
                 (doto h! (.put (HttpString. (str k)) (str v)))
                 :else
                 (doto h! (.remove (HttpString. (str k))))))
             header-map!
             headers))

(comment
  (doto (HeaderMap.)
    (.put (HttpString. "a") "1")
    (.put (HttpString. "b") "2")
    (.put (HttpString. "c") "3"))
  ;=> #object[io.undertow.util.HeaderMap 0x3f1121c8 "{a=[1], b=[2], c=[3]}"]
  ;             Execution time mean : 98,584711 ns
  ;    Execution time std-deviation : 40,607351 ns
  ;   Execution time lower quantile : 62,501697 ns ( 2,5%)
  ;   Execution time upper quantile : 142,989464 ns (97,5%)
  (doto (HeaderMap.)
    (put-headers! {"a" "1" "b" "2" "c" "3"}))
  ;=> #object[io.undertow.util.HeaderMap 0x27f9be32 "{a=[1], b=[2], c=[3]}"]
  ;             Execution time mean : 227,497245 ns
  ;    Execution time std-deviation : 55,560311 ns
  ;   Execution time lower quantile : 189,682926 ns ( 2,5%)
  ;   Execution time upper quantile : 311,745386 ns (97,5%)
  (doto (HeaderMap.)
    (put-headers! {"a" ["1" "2" "3"]}))
  ;=> #object[io.undertow.util.HeaderMap 0x3fadaad "{a=[1, 2, 3]}"]
  ;             Execution time mean : 543,457907 ns
  ;    Execution time std-deviation : 26,917643 ns
  ;   Execution time lower quantile : 523,367311 ns ( 2,5%)
  ;   Execution time upper quantile : 578,557000 ns (97,5%)
  (doto (HeaderMap.)
    (put-headers! {"a" 1 "b" 2 "c" 3}))
  ;=> #object[io.undertow.util.HeaderMap 0x7206ac57 "{a=[1], b=[2], c=[3]}"]
  (doto (HeaderMap.)
    (put-headers! {:a 1 :b 2 :c 3}))
  ;=> #object[io.undertow.util.HeaderMap 0x15a43a2b "{:a=[1], :b=[2], :c=[3]}"]
  (doto (HeaderMap.)
    (put-headers! {"a" "1" "b" "2" "c" "3"})
    (put-headers! {"c" nil}))
  ;=> #object[io.undertow.util.HeaderMap 0x47650438 "{a=[1], b=[2]}"]
  (doto (HeaderMap.)
    (put-headers! {"a" ["1" "2" "3"]})
    (put-headers! {"a" ["4" "5" "6"]}))
  ;=> #object[io.undertow.util.HeaderMap 0x45240707 "{a=[4, 5, 6]}"]
  (doto (HeaderMap.)
    (put-headers! {"a" ["1" "2" "3"]})
    (put-headers! {"a" nil}))
  ;=> #object[io.undertow.util.HeaderMap 0x64479e67 "{}"]
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
