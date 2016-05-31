(ns depends.test
  (:require
    [depends]
    [manifold [stream :as s] [deferred :as d]]
    [clojure.spec :as spec]
    [clojure.test :refer :all]
    [clojure.test.check :as tc]
    [clojure.test.check.generators :as gen]
    [clojure.test.check.properties :as prop]))

(spec/def :depends/anything (spec/with-gen (constantly true) (fn [] gen/any-printable)))

(def gen-dep-item
  (gen/fmap
    #(with-meta (first %) {:dependencies (second %)})
    (gen/tuple
      (gen/vector gen/any-printable)
      (spec/gen :depends/request-dependencies))))

(def gen-vector-dep-items (gen/vector gen-dep-item))

(def data-flow-prop
  (prop/for-all
    [items gen-vector-dep-items]
    (let [c (count items)
          ic (max c 10)
          in (s/stream ic)
          out (s/stream ic)
          released (depends/map-release out)
          released-buffer (s/stream ic)
          ds (depends/dependify in out)]
      (s/connect released released-buffer)
      (doseq [i items] @(s/put! in i))
      (let [released-items (take c (repeatedly #(s/take! released-buffer)))
            all-items (apply d/zip released-items)]
        (d/timeout! all-items 250 ::timeout)
        (and (not= @all-items ::timeout)
             (= @all-items items))))))

(deftest data-flow
  (testing "Plain data flow"
    (tc/quick-check 20 data-flow-prop)))


