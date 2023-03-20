(ns strojure.undertow.handler.referrer-policy
  "The [Referrer-Policy] HTTP header controls how much referrer information
  (sent with the Referer header) should be included with requests.

  [Referrer-Policy]:
  https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy
  "
  (:import (clojure.lang Keyword)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(def ^:const ^String header-name
  "Returns \"Referrer-Policy\" string."
  "Referrer-Policy")

(defprotocol ReferrerPolicyHeader
  (render-header-value
    ^java.lang.String [obj]
    "Returns string value for the [Referrer-Policy] response header.

    [Referrer-Policy]:
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Referrer-Policy
    "))

(extend-protocol ReferrerPolicyHeader
  nil (render-header-value [_] nil)
  String (render-header-value [s] s)
  Boolean (render-header-value [b] (when b "strict-origin-when-cross-origin"))
  Keyword (render-header-value [k] (name k)))

(comment
  (render-header-value nil)
  (render-header-value false)
  (render-header-value true)
  (render-header-value :strict-origin)
  (render-header-value "no-referrer-when-downgrade")
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
