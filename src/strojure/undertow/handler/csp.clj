(ns strojure.undertow.handler.csp
  "The HttpHandler to add [CSP] header in response.

  [CSP]: https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP
  "
  (:require [strojure.undertow.api.types :as types]
            [strojure.web-security.csp :as csp])
  (:import (io.undertow.server HttpHandler HttpServerExchange)
           (io.undertow.util AttachmentKey HttpString)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(def ^:const report-uri-default
  "Default value of the CSP report URI."
  "/csp-report")

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn report-uri-handler
  "Handles CSP report URI and invokes `:report-callback` function with
  `HttpServerExchange` as argument. Respond with HTTP 200. Used by
  [[csp-handler]] when `:report-callback` option is defined.

  Configuration map keys:

  - `:report-callback` – a function `(fn callback [exchange] ...)`.
      + Required.
      + Invoked when request URI equals `:report-uri`.
      + The return value is ignored.

  - `:report-uri` – a string with request `:uri` to match for.
      + Exact value is matched.
      + Default value is \"/csp-report\".
  "
  {:tag HttpHandler :added "1.1"}
  [next-handler {:keys [report-callback, report-uri]}]
  (assert (fn? report-callback))
  (let [report-uri (or report-uri report-uri-default)
        next-handler (types/as-handler next-handler)]
    (assert (string? report-uri) (str "Expect string in `:report-uri`: " report-uri))
    (reify HttpHandler
      (handleRequest [_ e]
        (if (.equals ^String report-uri (.getRequestURI e))
          (doto e (report-callback)
                  (.setStatusCode 200)
                  (.endExchange))
          (.handleRequest next-handler e))))))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defonce ^{:doc "The `AttachmentKey` for generated unique CSP nonce in exchange."}
  nonce-attachment-key
  (AttachmentKey/create String))

(defn csp-handler
  "Adds [CSP] header in ring response. If header uses nonce then nonce value
  is being attached to exchange and accessible using [[get-request-nonce]].

  [CSP]: https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP

  Configuration map keys:

  - `:policy` – a map of directive names (string, keyword) and directive values
                (string, keyword, collection of strings and keywords)
      + The `:nonce` keyword in directive values represents nonce placeholder

  - `:report-only` – optional boolean flag if report-only CSP header name should
                     be used.

  - `:random-nonce-fn` – optional 0-arity function to generate nonce for every
                         request.

  - `:report-callback` – a function `(fn callback [exchange] ...)` to handle
    `report-uri` directive.
      + When presented then handler is wrapped with [[report-uri-handler]].
      + If policy map does not have `report-uri` directive then it is added with
        default value \"/csp-report\".
  "
  {:tag HttpHandler :added "1.1"}
  [next-handler {:keys [policy, report-only, random-nonce-fn, report-callback]}]
  (let [next-handler (types/as-handler next-handler)
        header-name (HttpString. ^String (csp/header-name report-only))
        report-uri (and report-callback (csp/find-directive :report-uri policy))
        policy (cond-> policy (and report-callback (not report-uri))
                              (assoc :report-uri report-uri-default))
        header-value-fn (csp/header-value-fn policy)
        nonce-fn (and (csp/requires-nonce? header-value-fn)
                      (or random-nonce-fn (csp/random-nonce-fn)))]
    (cond->
      (if nonce-fn (reify HttpHandler
                     (handleRequest [_ e]
                       (let [nonce (nonce-fn)]
                         (doto e
                           (.putAttachment nonce-attachment-key nonce))
                         (doto (.getResponseHeaders e)
                           (.put header-name ^String (header-value-fn nonce)))
                         (-> next-handler (.handleRequest e)))))
                   (reify HttpHandler
                     (handleRequest [_ e]
                       (doto (.getResponseHeaders e)
                         (.put header-name ^String (header-value-fn)))
                       (-> next-handler (.handleRequest e)))))
      report-callback
      (report-uri-handler {:report-callback report-callback
                           :report-uri report-uri}))))

(defn get-request-nonce
  "Returns CSP nonce attached by the [[csp-handler]]."
  {:tag String :added "1.1"}
  [exchange]
  (.getAttachment ^HttpServerExchange exchange nonce-attachment-key))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
