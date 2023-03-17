(defproject com.github.strojure/undertow "1.1.0-97-beta1"
  :description "Clojure API to Undertow web server."
  :url "https://github.com/strojure/undertow"
  :license {:name "The Unlicense" :url "https://unlicense.org"}

  :dependencies [[io.undertow/undertow-core "2.3.4.Final"]
                 [com.github.strojure/web-security "1.0.0-28"]]

  :java-source-paths ["src"]
  :javac-options ["-source" "11" "-target" "11"]

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :dev,,,,, {:dependencies [;; Testing HTTP requests
                                       [java-http-clj "0.4.3"]]
                        :source-paths ["doc"]}}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo" :sign-releases false}]])
