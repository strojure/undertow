(defproject com.github.strojure/undertow "1.0.28-beta3"
  :description "Clojure API to Undertow web server."
  :url "https://github.com/strojure/undertow"
  :license {:name "The MIT License" :url "http://opensource.org/licenses/MIT"}

  :dependencies [[io.undertow/undertow-core "2.3.1.Final"]]

  :java-source-paths ["src"]
  :javac-options ["-source" "11" "-target" "11"]

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]]}}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo" :sign-releases false}]])
