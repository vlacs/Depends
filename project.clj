(defproject org.vlacs/depends "0.0.2"
  :description "Queue re-ordering based on metadata dependencies using Manifold
               streams for abstraction and asyncronous tools."
  :url "http://github.com/vlacs/depends"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha10"]
                 [org.clojure/test.check "0.9.0"]
                 [manifold "0.1.4"]]
  :source-paths ["src"]
  :profiles {:dev
             {:source-paths ["src" "dev"]
              :dependencies
              [[org.clojure/core.async "0.2.374"]]}})
