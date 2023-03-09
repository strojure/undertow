(defproject com.github.strojure/undertow "1.0.89-SNAPSHOT"
  :description "Clojure API to Undertow web server."
  :url "https://github.com/strojure/undertow"
  :license {:name "The Unlicense" :url "https://unlicense.org"}

  :dependencies [[io.undertow/undertow-core "2.3.4.Final"]]

  :java-source-paths ["src"]
  :javac-options ["-source" "11" "-target" "11"]

  :profiles {:provided {:dependencies [[org.clojure/clojure "1.11.1"]]}
             :dev,,,,, {:source-paths ["doc"]}}

  :deploy-repositories [["clojars" {:url "https://clojars.org/repo" :sign-releases false}]])
