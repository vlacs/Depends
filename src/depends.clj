(ns depends
  (:require
    [clojure.inspector :refer [atom?]]
    [clojure.spec :as spec]
    [clj-uuid :refer [v1] :rename {v1 uuid}]
    [manifold
     [time :as t]
     [stream :as s]
     [deferred :as d]]))

(spec/def ::complete d/deferred?)

(spec/def ::data-wrapper
  (spec/keys :req [::complete ::data]))

(spec/def ::read-lock d/deferred?)
(spec/def ::write-lock d/deferred?)

(spec/def ::dependency-item
  (spec/keys :req [::read-lock ::write-lock]))

(spec/def ::dependency-map
  (spec/map-of
    ::anything ::dependency-item))

(spec/def ::request-dependencies
  (spec/map-of
    ::anything #{:read :write}))

(defn wrap-data
  [data completion-lock]
  {::data data
   ::complete completion-lock})

(defn make-put-lock
  [dm dependencies]
  (apply
    d/zip
    (map
      (fn [[dep-name read-write]]
        (when-let [dep-locks (get dm dep-name)]
          (if (= read-write :write)
            (::write-lock dep-locks)
            (::read-lock dep-locks))))
      dependencies)))

(defn chain-lock
  [target lock read-write]
  {::read-lock
   (if (= read-write :write)
     (d/chain
       (d/zip (::read-lock target) lock)
       (constantly true))
     (::read-lock target (d/success-deferred true)))
   ::write-lock
   (d/chain
     (d/zip (::write-lock target) lock)
     (constantly true))})

(defn chain-completion-lock
  [completion-lock dm [dep-name read-write]]
  (update-in dm [dep-name] chain-lock completion-lock read-write))

(defn merge-completion-lock
  [dm dependencies completion-lock]
  (reduce
    (partial chain-completion-lock completion-lock)
    dm dependencies))

(defn chained-put!
  [dm incoming outgoing item]
  (let [dependencies (:dependencies (meta item))
        put-lock (make-put-lock dm dependencies)
        completion-lock (d/deferred)]
    (d/chain
      put-lock
      (fn [_]
        (s/put! outgoing (wrap-data item completion-lock))))
    (merge-completion-lock dm dependencies completion-lock)))

(spec/fdef
  chained-put!
  :args (spec/cat
          ::dependency-map ::dependency-map
          ::incoming-stream s/stream?
          ::outgoing-stream s/stream?
          ::chained-item ::anything)
  :ret ::dependency-map)

(spec/instrument #'chained-put!)

(defn dissoc-realized
  "Remove all the items where both read and write locks have been realized."
  [dm]
  (apply
    (partial dissoc dm)
    (keep
      (fn [[k {read-lock ::read-lock write-lock ::write-lock}]]
        (when
          (and (d/realized? read-lock) (d/realized? write-lock)) k)) dm)))

(spec/fdef
  dissoc-realized
  :args (spec/cat ::dependency-map ::dependency-map)
  :ret ::dependency-map)

(spec/instrument #'dissoc-realized)

(defn dependify
  "Starts doing some queue-reordering."
  [incoming outgoing]
  (let [dm (atom {})]
    {:state dm
     :streams
     {:incoming incoming
      :outgoing outgoing}
     :tasks
     {:clean-up-cron
      (t/every (t/seconds 10) #(d/future (swap! dm dissoc-realized) true))
      :chained-put-loop
      (d/loop []
        (d/chain
          (s/take! incoming)
          #(swap! dm chained-put! incoming outgoing %1)
          (fn [_] (d/recur))))}}))

(spec/def ::state atom?)
(spec/def ::clean-up-cron fn?)
(spec/def ::chained-put-loop d/deferred?)
(spec/def ::incoming s/stream?)
(spec/def ::outgoing s/stream?)
(spec/def ::streams (spec/keys :req-un [::incoming ::outgoing]))
(spec/def ::tasks (spec/keys :req-un [::clean-up-cron ::chained-put-loop]))
(spec/def ::system (spec/keys :req-un [::state ::tasks ::streams]))

(spec/fdef
  dependify
  :args (spec/cat ::incoming s/stream? ::outgoing s/stream?)
  :ret ::system)

(spec/instrument #'dependify)

(defn release! [i] (d/success! (::complete i) true))

(spec/fdef
  release!
  :args (spec/cat ::data-wrapper ::data-wrapper)
  :ret true?)

(spec/instrument #'release!)

(defn consume
  "This function lets you consume data that could have data dependencies that
  must be respected. This function pulls the extra information out and gives
  the function the original information before it was passed into the dep
  manager. Once the function is done consuming the message, the deferred
  completion value is then realized so the lock on the data can be released."
  [dependency-item-stream f & args]
  (d/loop []
    (d/chain
      (s/take! dependency-item-stream ::drained)
      (fn [{data ::data complete ::complete :as msg}]
        (when (not (identical? ::drained msg))
          (d/chain
            (d/future (apply (partial f data) args) msg)
            #(release! %1)
            (fn [] (d/recur))))))))

(defn map-release
  "Releases the dependencies on each item and emits the data on to the stream
  that gets returned from this function."
  [dep-event-stream]
  (let [out (s/stream)]
    (s/connect-via
      dep-event-stream
      (fn [i]
        (d/chain
          (s/put! out (::data i))
          (fn [_] (release! i)))) out) out))

(comment
  
  (def i (s/stream 10))
  (def o (s/stream 10))
  (def system (dependify i o))
  (def rb (s/stream 10))
  (s/connect (depends/map-release o) rb)

  (s/put! i [[[] []]])
  (s/take! rb)
  
  )
