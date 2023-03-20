(ns strojure.undertow.handler.hsts)

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(def ^:const ^String header-name
  "The HTTP `Strict-Transport-Security` response header (often abbreviated as
  HSTS) informs browsers that the site should only be accessed using HTTPS, and
  that any future attempts to access it using HTTP should automatically be
  converted to HTTPS."
  "Strict-Transport-Security")

(defprotocol HstsHeader
  (render-header-value
    ^java.lang.String [_]
    "Returns string value for the [Strict-Transport-Security] response header.

    [Strict-Transport-Security]:
    https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Strict-Transport-Security
    "))

(extend-protocol HstsHeader
  nil (render-header-value [_] nil)
  String (render-header-value [s] s)
  Boolean (render-header-value [b] (when b "max-age=31536000")))

(comment
  (render-header-value nil)
  (render-header-value false)
  (render-header-value true)
  (render-header-value "max-age=31536000; includeSubDomains; preload")
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
