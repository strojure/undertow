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
      (handler/path {:prefix {"static" (handler/resource {:resource-manager :class-path
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
   {:type handler/graceful-shutdown}
   {:type handler/proxy-peer-address}
   {:type handler/simple-error-page}
   ;; The handler for webapi hostname.
   {:type handler/virtual-host
    :host {"app1.company.com" (my-handler :app1-handler)
           "app2.company.com" (my-handler :app2-handler)}}
   ;; Path specific handlers.
   {:type handler/path
    :prefix {"static" {:type handler/resource :resource-manager :class-path
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
    :prefix {"static" {:type ::handler/resource :resource-manager :class-path
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
  (with-open [_ (server/start {:handler (keyword-handler-config)})])
  )
