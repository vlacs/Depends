(defproject org.vlacs/depends "0.0.1"
  :description "Queue re-ordering based on metadata dependencies using Manifold
               streams for abstraction and asyncronous tools."
  :url "http://github.com/vlacs/depends"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.9.0-alpha3"]
                 [manifold "0.1.4"]]
  :profiles {:dev {:dependencies [[org.clojure/test.check "0.9.0"]]}})
