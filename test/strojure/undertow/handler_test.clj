(ns strojure.undertow.handler-test
  (:require [clojure.string :as string]
            [clojure.test :as test :refer [deftest testing]]
            [java-http-clj.core :as http]
            [strojure.undertow.api.exchange :as exchange]
            [strojure.undertow.api.types :as types]
            [strojure.undertow.handler :as handler]
            [strojure.undertow.server :as server])
  (:import (io.undertow.server HttpHandler)))

(set! *warn-on-reflection* true)

(declare thrown?)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(def ^:private dummy-handler (handler/with-exchange (constantly nil)))

(defn- exec
  "Executes HTTP request and returns map HTTP response map."
  [{:keys [handler, method, uri, headers, body]
    :or {handler dummy-handler, method :get, uri "/", headers {}}}]
  (with-open [server (server/start {:handler handler :port 0})]
    (http/send {:method method
                :uri (str "http://localhost:"
                          (-> server types/bean* :listenerInfo first :address :port)
                          uri)
                :headers headers
                :body body})))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn- session-config-handler
  [config]
  (-> (reify HttpHandler
        (handleRequest [_ e]
          (some-> (exchange/get-or-create-session e)
                  (.setAttribute "test" {}))))
      (handler/session {:session-config config})))

(deftest session-t

  (testing "session config"

    (let [cookie (-> (exec {:handler (session-config-handler {})})
                     :headers (get "set-cookie"))]
      (test/is (string/includes? cookie "HttpOnly"))
      (test/is (string/includes? cookie "SameSite=Lax"))
      (test/is (not (string/includes? cookie "secure"))))

    (let [cookie (-> (exec {:handler (session-config-handler {:secure true
                                                              :same-site "none"})})
                     :headers (get "set-cookie"))]
      (test/is (string/includes? cookie "secure"))
      (test/is (string/includes? cookie "SameSite=None")))

    (let [cookie (-> (exec {:handler (session-config-handler {:http-only false
                                                              :secure false
                                                              :same-site nil})})
                     :headers (get "set-cookie"))]
      (test/is (not (string/includes? cookie "HttpOnly")))
      (test/is (not (string/includes? cookie "secure")))
      (test/is (not (string/includes? cookie "SameSite"))))

    (let [cookie (-> (exec {:handler (session-config-handler {:same-site false})})
                     :headers (get "set-cookie"))]
      (test/is (not (string/includes? cookie "SameSite"))))

    )

  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
