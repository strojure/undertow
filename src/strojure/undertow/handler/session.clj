(ns strojure.undertow.handler.session
  "Session handler functionality."
  (:import (io.undertow.server.session InMemorySessionManager SecureRandomSessionIdGenerator SessionCookieConfig)
           (strojure.undertow.session SessionCookieConfigPlus)))

(set! *warn-on-reflection* true)

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn in-memory-session-manager
  "Return instance of `InMemorySessionManager` given configuration map."
  {:tag InMemorySessionManager}
  [{:keys [session-id-generator
           deployment-name
           max-sessions
           expire-oldest-unused-session-on-max
           statistics-enabled]
    :or {max-sessions 0, expire-oldest-unused-session-on-max true}}]
  (InMemorySessionManager. (or session-id-generator (SecureRandomSessionIdGenerator.)),
                           deployment-name
                           max-sessions
                           expire-oldest-unused-session-on-max
                           (boolean statistics-enabled)))

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,

(defn session-cookie-config
  "Returns instance of `io.undertow.server.session.SessionConfig` for given
  configuration map. By default, the cookie is `HttpOnly` with `SameSite=Lax`."
  {:tag SessionCookieConfig}
  #_{:clj-kondo/ignore [:shadowed-var]}
  [{:keys [cookie-name, path, domain, discard, secure, http-only, same-site, max-age, comment]
    :or {http-only true, same-site "Lax"}}]
  (let [config (cond-> (new SessionCookieConfigPlus)
                 (string? same-site) (.setSameSiteMode (name same-site)))]
    (cond-> ^SessionCookieConfig config
      cookie-name       ^SessionCookieConfig (.setCookieName cookie-name)
      path              ^SessionCookieConfig (.setPath path)
      domain            ^SessionCookieConfig (.setDomain domain)
      (some? discard)   ^SessionCookieConfig (.setDiscard (boolean discard))
      (some? secure)    ^SessionCookieConfig (.setSecure (boolean secure))
      (some? http-only) ^SessionCookieConfig (.setHttpOnly (boolean http-only))
      max-age           ^SessionCookieConfig (.setMaxAge max-age)
      comment           ^SessionCookieConfig (.setComment comment))))

(comment
  (session-cookie-config {:same-site "Lax"})
  )

;;,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,,
