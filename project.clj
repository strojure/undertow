(defproject com.github.strojure/undertow "1.4.1-126-SNAPSHOT"
  :description "Clojure API to Undertow web server."
  :url "https://github.com/strojure/undertow"
  :license {:name "The Unlicense" :url "https://unlicense.org"}

  :dependencies [[io.undertow/undertow-core "2.4.1.Final"]
                 [com.github.strojure/web-security "1.2.0-38"]]

  :java-source-paths ["src"]
  :javac-options ["--release" "17"]

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.12.5"]]}
             :dev,,,,, {:dependencies [;; Testing HTTP requests
                                       [java-http-clj "0.4.3"]]
                        :source-paths ["doc"]}}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo" :sign-releases false}]])
