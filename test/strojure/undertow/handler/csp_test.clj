(ns strojure.undertow.handler.csp-test
  (:require [clojure.test :as test :refer [deftest testing]]
            [java-http-clj.core :as http]
            [strojure.undertow.api.types :as types]
            [strojure.undertow.handler :as handler]
            [strojure.undertow.handler.csp :as csp]
            [strojure.undertow.server :as server])
  (:import (io.undertow.server HttpHandler HttpServerExchange)))

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

(def ^{:arglists '([{:keys [report-callback, report-uri]}])
       :private true}
  report-uri-handler* (partial csp/report-uri-handler dummy-handler))

(deftest report-uri-handler-t

  (test/is (= "/csp-report"
              (let [a! (atom :undefined)
                    callback (fn [^HttpServerExchange e] (reset! a! (.getRequestURI e)))]
                (exec {:handler (report-uri-handler* {:report-callback callback})
                       :uri "/csp-report"})
                @a!)))

  (test/is (= "/custom-csp-report"
              (let [a! (atom :undefined)
                    callback (fn [^HttpServerExchange e] (reset! a! (.getRequestURI e)))]
                (exec {:handler (report-uri-handler* {:report-uri "/custom-csp-report"
                                                      :report-callback callback})
                       :uri "/custom-csp-report"})
                @a!)))

  (test/is (= :undefined
              (let [a! (atom :undefined)
                    callback (fn [^HttpServerExchange e] (reset! a! (.getRequestURI e)))]
                (exec {:handler (report-uri-handler* {:report-callback callback})
                       :uri "/not-csp-report"})
                @a!)))

  (test/is (= #_:status 200
                        (let [a! (atom :undefined)
                              callback (fn [^HttpServerExchange e] (reset! a! (.getRequestURI e)))]
                          (-> (exec {:handler (report-uri-handler* {:report-callback callback})
                                     :uri "/csp-report"})
                              :status))))

  (test/is (= #_:status 500
                        (let [callback (fn [^HttpServerExchange _] (throw (Exception. "Oops")))]
                          (-> (exec {:handler (report-uri-handler* {:report-callback callback})
                                     :uri "/csp-report"})
                              :status))))

  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(def ^{:arglists '([{:keys [policy, report-only, random-nonce-fn, report-callback]}])
       :private true}
  csp-handler* (partial csp/csp-handler dummy-handler))

(deftest csp-handler-t

  (test/is (= "default-src 'none'"
              (-> (exec {:handler (csp-handler* {:policy {:default-src :none}})})
                  :headers (get "content-security-policy"))))

  (test/is (= "default-src 'none'"
              (-> (exec {:handler (csp-handler* {:policy {:default-src :none}
                                                 :report-only true})})
                  :headers (get "content-security-policy-report-only"))))

  (test/is (= "script-src 'nonce-TEST-NONCE'"
              (-> (exec {:handler (csp-handler* {:policy {:script-src :nonce}
                                                 :random-nonce-fn (constantly "TEST-NONCE")})})
                  :headers (get "content-security-policy"))))

  (test/is (= "script-src 'nonce-TEST-NONCE'"
              (-> (exec {:handler (csp-handler* {:policy {:script-src :nonce}
                                                 :report-only true
                                                 :random-nonce-fn (constantly "TEST-NONCE")})})
                  :headers (get "content-security-policy-report-only"))))

  (testing ":report-callback"

    (test/is (= "report-uri /csp-report"
                (-> (exec {:handler (csp-handler* {:policy {}
                                                   :report-callback identity})})
                    :headers (get "content-security-policy"))))

    (test/is (= "report-uri /test-report-uri"
                (-> (exec {:handler (csp-handler* {:policy {"report-uri" "/test-report-uri"}
                                                   :report-callback identity})})
                    :headers (get "content-security-policy"))))

    )

  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn- get-request-nonce*
  {:arglists '([{:keys [policy, report-only, random-nonce-fn, report-callback]}])}
  [opts]
  (-> (reify HttpHandler
        (handleRequest [_ e]
          (doto (.getResponseSender e)
            (.send (str (csp/get-request-nonce e))))))
      (csp/csp-handler opts)))

(deftest get-request-nonce-t

  (test/is (= "TEST-NONCE"
              (-> (exec {:handler (get-request-nonce* {:policy {:script-src :nonce}
                                                       :random-nonce-fn (constantly "TEST-NONCE")})})
                  :body)))

  (test/is (= ""
              (-> (exec {:handler (get-request-nonce* {:policy {:script-src :self}
                                                       :random-nonce-fn (constantly "TEST-NONCE")})})
                  :body)))

  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
