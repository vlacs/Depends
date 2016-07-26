(ns depends.examples
  (:require
    [clojure.pprint :refer [pprint]]
    [manifold.stream :as s]
    [manifold.deferred :as d]
    [clojure.core.async :as a]
    [depends :refer [dependify]]))

(def items
  [^{:dependencies {[:user-id 1] :write}}
   [:user {:user-id 1
           :name "Jon"}]

   ^{:dependencies {[:user-id 1] :read
                    [:grade-id 1] :read}}
   [:user-grade {:user-id 1
                 :grade-id 1
                 :grade "A+"}]

   ^{:dependencies {[:user-id 1] :read
                    [:grade-id 2] :read}}
   [:user-grade {:user-id 1
                 :grade-id 2
                 :grade "B"}]

   ^{:dependencies {[:grade-id 3] :write}}
   [:grade {:grade-id 3
            :name "foo"
            :hard? true}]

   ^{:dependencies {[:grade-id 1] :write}}
   [:grade {:grade-id 1
            :name "bar"
            :hard? false}]

   ^{:dependencies {[:user-id 1] :read
                    [:grade-id 3] :read}}
   [:user-grade {:user-id 1
                 :grade-id 3
                 :grade "F-"}]])

(comment

  (do
    (def input (s/stream 10))
    (def output (s/stream 10))

    (def depends-mgr (dependify input output {})))

  (def depends-mgr (dependify items output {}))

  (pprint depends-mgr)

  (s/put-all! input items)
  (def rval (take 6 (repeatedly #(s/take! output))))

  (pprint rval)

  (d/success! (:depends/complete @(first rval)) true)
  (d/success! (:depends/complete @(second rval)) true)
  (d/success! (:depends/complete @(nth rval 2)) true)

  (pprint rval)

  )


