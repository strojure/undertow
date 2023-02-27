(ns usage.handler-configuration
  (:require [strojure.undertow.handler :as handler]
            [strojure.undertow.server :as server])
  (:import (io.undertow.server HttpServerExchange)
           (io.undertow.util Headers)))

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

(defn- set-content-type-options
  [^HttpServerExchange exchange]
  (let [headers (.getResponseHeaders exchange)]
    (when (.contains headers Headers/CONTENT_TYPE)
      (.put headers Headers/X_CONTENT_TYPE_OPTIONS "nosniff"))))

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
      ;; Modify response before commit.
      (handler/on-response-commit set-content-type-options)
      ;; Add fixed response headers.
      (handler/set-response-header {"X-Frame-Options" "DENY"})
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
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Add fixed response headers.
   {:type `handler/set-response-header :header {"X-Frame-Options" "DENY"}}
   ;; Modify response before commit.
   {:type `handler/on-response-commit :listener set-content-type-options}
   ;; Path specific handlers.
   {:type `handler/path
    :prefix {"static" {:type `handler/resource :resource-manager :classpath-files
                       :prefix "public/static"}}
    :exact {"websocket" {:type `handler/websocket :callback websocket-callback}}}
   ;; Enable sessions for next handlers.
   {:type `handler/session}
   ;; The handlers for app hostnames.
   {:type `handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
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
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Add fixed response headers.
   {:type handler/set-response-header :header {"X-Frame-Options" "DENY"}}
   ;; Modify response before commit.
   {:type handler/on-response-commit :listener set-content-type-options}
   ;; Path specific handlers.
   {:type handler/path
    :prefix {"static" {:type handler/resource :resource-manager :classpath-files
                       :prefix "public/static"}}
    :exact {"websocket" {:type handler/websocket :callback websocket-callback}}}
   ;; Enable sessions for next handlers.
   {:type handler/session}
   ;; The handlers for app hostnames.
   {:type handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
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
    :host {"webapi.company.com" (my-handler :webapi-handler)}}
   ;; Add fixed response headers.
   {:type ::handler/set-response-header :header {"X-Frame-Options" "DENY"}}
   ;; Modify response before commit.
   {:type ::handler/on-response-commit :listener set-content-type-options}
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
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
   ;; Last resort handler
   (my-handler :default-handler)])

(comment
  (with-open [_ (server/start {:handler (imperative-handler-config)})])
  (with-open [_ (server/start {:handler (symbol-handler-config)})])
  (with-open [_ (server/start {:handler (instance-handler-config)})])
  (with-open [_ (server/start {:handler (keyword-handler-config)})])
  )
